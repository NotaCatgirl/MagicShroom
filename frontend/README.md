# MagicShroom Android app

The Compose app browses public cards and lets a Google signed-in user create a personal deck,
add cards, and remove them. Supabase Auth handles OAuth and the session; the app calls the
MagicShroom backend with the Supabase access token. It does not connect to PostgreSQL.

Open this folder in Android Studio and set Gradle properties locally:

```properties
supabaseUrl=https://PROJECT_REF.supabase.co
supabasePublishableKey=sb_publishable_...
backendUrl=http://10.0.2.2:8080
```

Register `magicshroom://auth` as a Supabase Auth redirect URL and enable Google sign-in for the
project. On Android 17 (API 37), the debug app requests Nearby devices access before connecting
to the emulator-host address `10.0.2.2`; allow it to browse the local API. This permission is not
included in release builds or requested for a remote backend. The debug manifest also permits
emulator HTTP for local development; deploy the backend over HTTPS and set `backendUrl` accordingly
for release builds. The publishable key may be in the app;
the database password and service role key must stay on the backend.

Build with `./gradlew :app:assembleDebug`. The app currently presents one screen with card search,
card detail, Google sign-in, deck creation, and add/remove actions.

The merged `data/` and `ui/` packages also contain a separate Retrofit-backed navigation
prototype with mock login. Its `AppNavHost` is exercised by `AppFlowTest`, but it is not the
activity's entry point. Do not use its stub-user header for production requests; the active
screen uses Supabase Auth bearer tokens through `ApiClient`.
