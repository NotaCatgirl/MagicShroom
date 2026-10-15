# MagicShroom Android app

The current Compose app opens a demo login, then a searchable card list and a read-only account
screen. Sign in with `testEmail1@gmail.com` and `password1`. The demo account is local to the app;
search uses the backend's public `GET /api/v1/cards` endpoint, so the Android app does not need
Supabase credentials for this flow. Configure the backend separately as described in its README.
Card details and deck actions are not connected to this screen yet.

Open `frontend/` in Android Studio and run the app. The debug build defaults to
`http://10.0.2.2:8080`, which reaches a backend running on your computer from the Android
emulator. Start a backend with card data using the instructions in [`backend/README.md`](../backend/README.md),
or set another backend URL in your local Gradle properties:

```properties
backendUrl=https://YOUR_BACKEND_HOST
```

On the Android 17 (API 37) emulator, allow local-network access when the search screen asks for
it. This permission is only requested for a debug build using `10.0.2.2`. If the backend is
unavailable, search shows an error with a Retry button.

Build from this directory with `./gradlew :app:assembleDebug`. Supabase OAuth setup remains in
the project for a later authentication milestone; the current demo login does not use it.
