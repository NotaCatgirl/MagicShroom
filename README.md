# MagicShroom

MagicShroom is a Magic: The Gathering card catalog with personal decks and card comparison. The
backend is a Kotlin/Spring Boot API on Supabase PostgreSQL, deployed to Render; the Android app uses
Jetpack Compose and Supabase Auth.

## What works today

| Area | Status |
|---|---|
| Card catalog | 20,478 cards from the MTGJSON archive in Supabase; the API searches by name and color (the app by name), with paging, card detail, and a batch lookup. |
| Card images | Scryfall scans for 20,473 cards. The app downloads them from Scryfall with its own User-Agent. |
| Card comparison | "Compare with…" on a card lists similar cards (same type, same or overlapping colors, never the card's own other face); the comparison marks mana value, power, toughness, loyalty, and popularity as better, worse, or equal, shows matching colors as equal, and lists keywords, types, and differing colors unique to each card. |
| Sign-in | Supabase email/password and Google. The API verifies each access token against the project's signing keys and creates the app account on first use. |
| Decks | Full deck API (create, rename, delete, add and remove cards). The app has the data layer but no deck screens yet. |
| Admin | Users with the `ADMIN` role see Admin tools in the app: list users, change roles, and delete users (including their Supabase login when the server has the admin key). Promote the first admin with SQL; see below. |
| Deployment | GitHub Actions tests every pull request; a push to `main` also publishes the API image to GHCR and asks Render to deploy it. |

## Run it locally

1. Copy `.env.example` to `.env` and fill in the Supabase database and URL values (never commit it).
2. Start the API from the repository root:
   ```bash
   set -a && . ./.env && set +a && cd backend && ./gradlew bootRun
   ```
   Spring Boot does not read `.env` itself; without the first part it fails with
   `Could not resolve placeholder 'SUPABASE_URL'`.
3. The app's build already points at the team's Supabase project. Choose its backend in
   `~/.gradle/gradle.properties` (machine-local, never committed); without it the app uses
   `http://10.0.2.2:8080`, the emulator's address for your computer:
   ```properties
   backendUrl=https://magicshroom.onrender.com
   ```
   Override it for one build with `-PbackendUrl=...`. To use a different Supabase project, also set
   `supabaseUrl` and `supabasePublishableKey` (see [`frontend/README.md`](frontend/README.md)).
4. Open `frontend/` in Android Studio and run the app. Open `backend/` as its own project; the
   repository root is not a Gradle project.

Tests: `cd backend && ./gradlew test` (the PostgreSQL test needs Docker) and
`cd frontend && ./gradlew :app:testDebugUnitTest`.

## Make someone an admin

After the person has signed in once, run in the Supabase SQL editor or `psql`:

```sql
insert into app_users (auth_user_id, email, display_name, role)
select id, email, coalesce(nullif(raw_user_meta_data->>'full_name', ''), split_part(email, '@', 1)), 'ADMIN'
from auth.users where email = 'teammate@example.com'
on conflict (auth_user_id) do update set role = 'ADMIN'
returning id, email, role;
```

## Documentation

- [`backend/README.md`](backend/README.md): API configuration, profiles, tests, and Docker.
- [`frontend/README.md`](frontend/README.md): app configuration, sign-in, screens, and tests.
- [`docs/openapi.yaml`](docs/openapi.yaml): API contract.
- [`docs/supabase-deployment.md`](docs/supabase-deployment.md): Supabase setup, schema, and the one-time card import.
- [`docs/render-deployment.md`](docs/render-deployment.md) and [`docs/CI-RENDER-DEPLOYMENT.md`](docs/CI-RENDER-DEPLOYMENT.md): Render service and the CI deploy (the `RENDER_DEPLOY_HOOK_URL` repository secret must be set for automatic deploys).
- [`docs/superpowers/specs/`](docs/superpowers/specs/): feature designs, such as card comparison.

Database credentials and the Supabase secret key stay on the server. The app only holds the
Supabase publishable key and the signed-in user's access token.
