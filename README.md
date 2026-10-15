# MagicShroom

MagicShroom is a card catalog and personal deck app. The backend is a Kotlin/Spring Boot API;
the Android client uses Jetpack Compose and Supabase Auth for Google sign-in.

- [`backend/README.md`](backend/README.md): API configuration, profiles, tests, and Docker.
- [`frontend/README.md`](frontend/README.md): Android build and sign-in setup.
- [`docs/supabase-deployment.md`](docs/supabase-deployment.md): PostgreSQL size measurement,
  versioned schema, and one-time archive import.
- [`docs/render-deployment.md`](docs/render-deployment.md): Docker service setup and verification.
- [`docs/openapi.yaml`](docs/openapi.yaml): API contract.

The unchanged 2019 card archive is at `../archive/AllCards.json`. The API maps its compact
PostgreSQL storage to the existing card response shape. Database credentials stay on the server;
the app only holds a Supabase publishable key and a user's access token.
