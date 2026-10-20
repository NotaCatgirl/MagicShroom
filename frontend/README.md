# MagicShroom Android app

The Compose app opens a public card search with the MagicShroom logo above the search bar.
Guests can search and see cards without signing in. The top-right Log in button opens Google
sign-in through Supabase Auth; signed-in users get an Account button with profile information
and sign-out. There is no email/password or demo-account login. Search uses the backend's public
`GET /api/v1/cards` endpoint; card details and deck actions are not connected to this screen yet.

Open `frontend/` in Android Studio. The build reads these public Auth settings from the ignored
`.env` in the repository root:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

The existing names `supabaseUrl` and `supabasePublishableKey` also work in `.env`. After changing
these settings, rebuild and use Android Studio's full Run action so the installed app gets them.
User-level Gradle properties (`~/.gradle/gradle.properties`) can override these values and set
the backend address:

```properties
supabaseUrl=https://YOUR_PROJECT.supabase.co
supabasePublishableKey=sb_publishable_...
backendUrl=https://YOUR_BACKEND_HOST
```

Enable the Google provider in your Supabase project and allow `magicshroom://auth` as an Auth
redirect URL. The app's Android manifest already handles that callback. Only the URL and
publishable key belong in the Android app; keep database passwords and secret/service keys on the
server. The app restores a saved Supabase session on launch and returns to public search after
sign-out, with Log in shown again.

The debug build defaults to `http://10.0.2.2:8080` for `backendUrl`, which reaches a backend
running on your computer from the Android emulator. Start a backend with card data using
[`backend/README.md`](../backend/README.md), or point `backendUrl` to a deployed HTTPS API.
Google sign-in does not start the backend or make search available by itself.

On the Android 17 (API 37) emulator, allow local-network access when the search screen asks for
it. This permission is only requested for a debug build using `10.0.2.2`. If the backend is
unavailable, search shows an error with a Retry button.

Build from this directory with `./gradlew :app:assembleDebug`.

On macOS, checkouts under `~/Desktop` write generated Android build output to
`~/.gradle/magicshroom-builds/<checkout-id>/<module>/` to keep it outside iCloud Desktop syncing.
The debug APK is under `app/outputs/apk/debug/app-debug.apk` within that checkout's output folder.
Other checkout locations and operating systems use the usual build directories. After a Gradle
build-directory change, sync the project in Android Studio and use the full Run action.
