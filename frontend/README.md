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
project. The debug manifest permits emulator HTTP for local development; deploy the backend over
HTTPS and set `backendUrl` accordingly for release builds. The publishable key may be in the app;
the database password and service role key must stay on the backend.

Build with `./gradlew :app:assembleDebug`. The app currently presents one screen with card search,
card detail, Google sign-in, deck creation, and add/remove actions.
