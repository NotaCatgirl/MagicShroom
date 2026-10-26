# Authentication sequence

The Android app signs users in with **Google through Supabase Auth** using the OAuth 2.0
authorization-code flow with **PKCE**. The API never sees a password or a Google token: it accepts
a Supabase access token (a signed JWT) as a bearer token and verifies it itself.

## Sign-in and an authenticated request

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant App as Android app
    participant Auth as Supabase Auth
    participant Google
    participant API as MagicShroom API
    participant DB as PostgreSQL

    User->>App: Tap "Log in"
    App->>App: Create PKCE code_verifier and code_challenge
    App->>Auth: Open browser at /authorize?provider=google&code_challenge=…
    Auth->>Google: Redirect to Google sign-in
    User->>Google: Choose account and consent
    Google-->>Auth: Authorization code (OAuth callback)
    Auth-->>App: Redirect to magicshroom://auth?code=…
    App->>Auth: Exchange code + code_verifier for a session
    Auth-->>App: Access token (JWT) + refresh token
    Note over App: Session is persisted on the device and refreshed automatically

    User->>App: Open "My decks"
    App->>API: GET /api/v1/decks<br/>Authorization: Bearer <access token>
    API->>Auth: GET /auth/v1/.well-known/jwks.json (cached)
    Auth-->>API: Public signing keys
    API->>API: Verify signature (RS256/ES256), issuer, expiry,<br/>aud = authenticated, role = authenticated
    API->>DB: Find or create app_users row for the token's sub + email
    DB-->>API: User id and role (USER or ADMIN)
    API->>DB: Load this user's decks
    DB-->>API: Decks
    API-->>App: 200 OK + decks
```

1. **PKCE** (steps 2–9): the app keeps the `code_verifier` and sends only its hash, so an
   intercepted redirect code is useless without it. The redirect URI `magicshroom://auth` is
   handled by `MainActivity`, and `supabase-kt` performs the code exchange
   (`AppContainer` installs Auth with `flowType = FlowType.PKCE`).
2. **Bearer token** (step 11): `BearerTokenInterceptor` adds the current access token to every API
   request; requests go out without one when nobody is signed in.
3. **Verification** (steps 12–14): `SupabaseSecurity` configures Spring Security as an OAuth 2.0
   resource server. It downloads the project's public keys once and validates every token locally.
4. **Application user** (steps 15–16): `SupabaseCurrentUserResolver` maps the token's `sub` (a UUID)
   to a numeric `app_users.id`, creating the row on first use. The role comes from the database,
   not from the token, so promoting someone to admin takes effect immediately.

## Failure paths

```mermaid
sequenceDiagram
    autonumber
    participant App as Android app
    participant API as MagicShroom API

    App->>API: GET /api/v1/decks (no Authorization header)
    API-->>App: 401 Problem Details: "A bearer token is required."<br/>WWW-Authenticate: Bearer

    App->>API: GET /api/v1/decks (expired or wrongly signed token)
    API-->>App: 401 Problem Details: "The bearer token is invalid or expired."

    App->>API: POST /api/v1/cards (valid token, role USER)
    API-->>App: 403 Problem Details: "This operation requires the ADMIN role."

    App->>API: GET /api/v1/decks/42 (deck owned by someone else)
    API-->>App: 404 Problem Details: "Deck 42 was not found."
```

A deck that belongs to another user returns **404, not 403**, so the API does not reveal which deck
IDs exist. If a user's Supabase login has been deleted, a token they still hold is rejected with
`401 "This account no longer exists. Sign in again."` instead of recreating the account.

## Which routes need a token

| Routes | Access |
|---|---|
| `GET /api/v1/cards`, `GET /api/v1/cards/{cardId}`, `GET /api/v1/cards/{cardId}/similar`, `POST /api/v1/cards/batch` | Public |
| `/swagger-ui.html`, `/v3/api-docs`, `/actuator/health` | Public |
| `/api/v1/decks/**`, `GET`/`DELETE /api/v1/users/me` | Any signed-in user (own data only) |
| `POST`/`PUT`/`PATCH`/`DELETE /api/v1/cards/**`, `/api/v1/users`, `/api/v1/users/{userId}` | Signed-in user with the `ADMIN` role |

All error bodies follow the [error model](error-model.md).
