# MagicShroom API

Spring Boot/Kotlin API for the card catalog and personal decks. The API contract is in
[`../docs/openapi.yaml`](../docs/openapi.yaml).

## Run

Use JDK 25. Production is the default profile and requires PostgreSQL plus a Supabase project:

```bash
export DATABASE_URL='jdbc:postgresql://HOST:PORT/postgres?sslmode=require'
export DATABASE_USER='postgres.PROJECT_REF'
export DATABASE_PASSWORD='...'
export SUPABASE_URL='https://PROJECT_REF.supabase.co'
./gradlew bootRun
```

The backend validates Supabase bearer tokens with the project's JWKS endpoint. Configure an
asymmetric JWT signing key in Supabase Auth. The database password and pooler connection belong
only on the server. Public card GET routes need no token; all writes and deck routes do.

The `mock` profile uses JSON fixtures under `src/test/resources` and a stub caller for isolated tests:

```bash
./gradlew test
```

The stub accepts `X-Stub-User-Id` only in the mock profile. It is never enabled in production.

## Schema and archive

Flyway applies `V1__application_schema.sql` and `V2__required_value_constraints.sql` on startup.
V1 drops the old disposable `public.users`, `cards`, `decks`, and `deck_cards` tables, creates
typed application tables, and leaves `auth.users` alone. V2 rejects blank user/card/deck names
and JSONB card details that are not arrays. Required identity, name, list, and detail columns
are `NOT NULL`; genuinely optional card values such as mana cost and rules text remain nullable.
Application users retain numeric API IDs and link to Supabase Auth through a UUID. The tables
deny direct `anon` and `authenticated` access; the backend uses a server-side database connection.

Follow [`../docs/supabase-deployment.md`](../docs/supabase-deployment.md) to measure the complete
temporary PostgreSQL database before the one-time `archive/AllCards.json` import. An import above
400 MB rolls back. The importer preserves archive-only scalar fields in typed columns and the
complete `foreignData` payload in JSONB while returning the existing card API shape.

## Docker

`docker build -t magicshroom-api .` builds a production JAR in a JDK 25 stage and runs it from a
JRE 25 image. The image exposes `/actuator/health`; pass database and Supabase settings as runtime
environment variables. The server uses `PORT` when supplied (default `8080`). For Render setup,
see [`../docs/render-deployment.md`](../docs/render-deployment.md).
