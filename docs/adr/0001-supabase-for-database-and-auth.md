# ADR 0001: Supabase for the database and sign-in

- **Status:** Accepted
- **Date:** October 2026

## Context

The API needs a hosted PostgreSQL database that stays up for the whole course and holds about
20,000 cards, plus a way for Android users to sign in. The API runs on Render's free plan. Render's
free PostgreSQL database expires after a month, and building our own OAuth server or password
storage would take time away from the API itself. We chose Google as the OAuth 2.0 provider because it
supports the authorization-code flow with PKCE.

## Decision

Use **Supabase** for both:

1. **Database:** Supabase PostgreSQL, reached by the API over the session pooler with a server-only
   connection. The schema is owned by Flyway migrations in the backend, not by the Supabase dashboard.
2. **Sign-in:** Supabase Auth with the **Google** provider. The Android app runs the PKCE flow with
   `supabase-kt`, and the API acts as an OAuth 2.0 **resource server**: it verifies Supabase access
   tokens against the project's public signing keys (JWKS) and never calls Supabase on each request.

Application data stays in our own tables. `app_users` links to `auth.users` by UUID and holds the
numeric ID and the `USER`/`ADMIN` role the API uses.

## Consequences

- **Good:** one free service covers the database and Google sign-in, and does not expire.
- **Good:** token checks are local and stateless, so the API scales and stays fast.
- **Good:** roles live in our database, so making someone an admin is one SQL statement and takes
  effect on their next request.
- **Cost:** two configuration surfaces (Render and Supabase) and secrets that must stay on the server:
  the database password and the Supabase secret key used to delete logins.
- **Cost:** tests cannot use Supabase. Integration tests run PostgreSQL in Testcontainers and sign
  their own tokens against a mock JWKS server; route tests use a `mock` profile with JSON fixtures.
- **Cost:** direct table access by the `anon` and `authenticated` Supabase roles is revoked, so
  every read and write must go through the API.

## Alternatives considered

- **Render PostgreSQL:** simplest to wire up next to the API, but the free database expires after a month.
- **Our own authentication:** storing passwords or running an authorization server ourselves would add
  security risk and work outside the project's focus.
