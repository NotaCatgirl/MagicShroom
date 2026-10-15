# Render deployment

The repository-root `render.yaml` defines a Free Docker web service built from `backend/`.
The image runs the existing Spring Boot API; it does **not** import the card archive. The
20,478-card import has already been completed in Supabase, so do not run the importer again.

## Publish

1. Push the branch containing `render.yaml`, the backend Dockerfile, and the Flyway starter fix
   to GitHub. In Render, choose **New > Blueprint**, connect this repository and branch, and
   select the root `render.yaml`. Render builds the image from `backend/Dockerfile`; no Docker Hub
   account or image push is needed.
2. Enter the four prompted environment variables in Render, not in Git or Android:
   - `DATABASE_URL`: the Supabase IPv4 Session pooler URL in **JDBC** form, such as
     `jdbc:postgresql://POOLER_HOST:5432/postgres?sslmode=require`.
   - `DATABASE_USER`: the matching pooler username (usually `postgres.PROJECT_REF`).
   - `DATABASE_PASSWORD`: the Supabase database password.
   - `SUPABASE_URL`: the project's HTTPS URL, `https://PROJECT_REF.supabase.co`, **not** the
     pooler host.
3. Let the deploy finish. The app uses Render's `PORT` environment variable and exposes
   `/actuator/health` for Render health checks. Flyway should report that schema version 2 is
   up to date. Do not add archive-import arguments or put the archive in the image.

Render prompts for `sync: false` values only when the Blueprint is first created. If updating an
existing service, set or change these variables in the service's **Environment** page. Never
paste the password or connection string into an issue, commit, screenshot, or chat.

## Verify

Replace `SERVICE` with the Render service's actual name:

```bash
API=https://SERVICE.onrender.com
curl -i "$API/actuator/health"                         # 200
curl -i "$API/api/v1/cards?page=0&size=20"           # 200, with cards
curl -i "$API/api/v1/cards?page=0&size=20&name=Bolt" # 200
curl -i "$API/api/v1/decks"                          # 401 without a token
```

Then set `backendUrl=https://SERVICE.onrender.com` in your local `frontend/gradle.properties`,
rebuild and install the Android app, and check card browsing, Google sign-in, and creating a deck.
The Android app never receives database credentials. The emulator's `10.0.2.2` address works only
for a backend on your development machine, not for Render.

The Free service spins down after inactivity. Its first request may take about a minute to wake,
longer than the Android client's current timeout; warm it with the health URL before testing the
app. If dependable first-request behavior is needed, use a paid always-on instance or add a
client-side cold-start retry before calling the deployment finished.
