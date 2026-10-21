# MagicShroom Android app

A Jetpack Compose app for browsing the card catalog, comparing cards, and managing users (admins).

## Configure

The build reads three Gradle properties. Put them in `~/.gradle/gradle.properties` so they stay on
your machine:

```properties
supabaseUrl=https://YOUR_PROJECT_REF.supabase.co
supabasePublishableKey=sb_publishable_...
backendUrl=https://magicshroom.onrender.com
```

Without them the app falls back to placeholders: sign-in opens `example.supabase.co`, which does not
exist. The app does not read the repository's `.env` file; that file is for the backend.
`backendUrl` defaults to `http://10.0.2.2:8080`, a backend running on your computer as seen from the
emulator; override it for one build with `-PbackendUrl=...`. On Android 17 (API 37), allow
local-network access when the app asks.

## Sign-in

Email/password and Google both go through Supabase Auth. Google returns to the app through the
`magicshroom://auth` link, which must be in the Supabase project's allowed redirect URLs. The app
sends the Supabase access token to the backend on every request.

## Screens

- **Search:** card gallery with name search and paging.
- **Card detail:** full scan (with a face switch for double-faced cards), mana cost, type line, rules
  text, and **Compare with…**.
- **Similar cards:** the cards most like the selected one; tap one to compare.
- **Comparison:** both cards, a tally, a stat table where each verdict has a symbol, a word, and a
  color (▲ Better, ▼ Worse, = Equal, — Not comparable), and the traits each card has alone (★).
- **Account:** profile and sign-out; **Admin tools** appear for users with the `ADMIN` role.

Card images come from Scryfall, which rejects default HTTP-library User-Agents, so the app's image
loader (`MagicShroomApplication`) sends its own.

## Build and test

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # with an emulator running
```

For UI work on mock data, run `cd backend && ./gradlew bootTestRun` and build with
`-PbackendUrl=http://10.0.2.2:8080 -PstubUserId=1`; the mock backend then treats every request as
that user (1 is the admin). You still sign in through Supabase first.
