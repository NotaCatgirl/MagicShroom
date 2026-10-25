# MagicShroom Android app

The Compose app opens a public searchable card gallery with the MagicShroom logo above the
search bar. Guests can search, open card details, and compare cards without signing in. Card data
and image URLs come from the backend's public card endpoints; the app loads scans from Scryfall.
Configure the backend and its Scryfall image metadata import as described in
[`backend/README.md`](../backend/README.md).

The top-right Log in button opens Google sign-in through Supabase Auth. Signed-in users get an
Account button with read-only profile information and sign-out. Admin tools are available only
when the backend confirms the signed-in user has the ADMIN role; the backend enforces admin
permissions. There is no email/password or demo-account login. Open My decks from Account to
create, rename, or delete decks and add or remove catalog cards.

See the [app screenshot gallery](../README.md#app-screenshots) for browsing, comparison,
account/admin tools, and deck management.

Open `frontend/` in Android Studio. The build reads these public Auth settings from the ignored
`backend/.env`:

```properties
SUPABASE_URL=https://YOUR_PROJECT.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
```

The existing names `supabaseUrl` and `supabasePublishableKey` also work in `backend/.env`. After changing
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

The committed `frontend/gradle.properties` points `backendUrl` at the Render API
(`https://magicshroom.onrender.com`). For a backend running on your computer, build with
`-PbackendUrl=http://10.0.2.2:8080`, the emulator's address for your computer (also the fallback
when no `backendUrl` is set anywhere). Start a backend with card data using
[`backend/README.md`](../backend/README.md). Google sign-in does not start the backend or make
search available by itself.

On the Android 17 (API 37) emulator, allow local-network access when the search screen asks for
it. This permission is only requested for a debug build using `10.0.2.2`. If the backend is
unavailable, search shows an error with a Retry button.

Build from this directory with `./gradlew :app:assembleDebug`.

On macOS, checkouts under `~/Desktop` write generated Android build output to
`~/.gradle/magicshroom-builds/<checkout-id>/<module>/` to keep it outside iCloud Desktop syncing.
The debug APK is under `app/outputs/apk/debug/app-debug.apk` within that checkout's output folder.
Other checkout locations and operating systems use the usual build directories. After a Gradle
build-directory change, sync the project in Android Studio and use the full Run action.

## Comparing cards

A card's detail screen has **Compare with…**, which lists the cards most like it: the same main
type, the same or overlapping colors, the closest mana value, and never the card's own other face.
Pick one to see both cards side by side with a tally, a stat table, and the traits only one card
has (★). Every verdict has a symbol, a word, and a color: ▲ Better, ▼ Worse, = Equal, and — N/A
when a value is missing or not a number (such as `*`). Verdicts are from the first card's point of
view: lower mana value and EDHREC rank are better; higher power, toughness, and loyalty are better.
Comparison needs a backend with `GET /api/v1/cards/{cardId}/similar`; an older one shows
"Comparisons need the latest server version."

Card images come from Scryfall, which rejects default HTTP-library User-Agents, so the app's image
loader (`MagicShroomApplication`) sends its own.

## Tests

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # with an emulator running
```

`CardImageLinksIntegrationTest` calls the deployed Render API, so it is skipped unless you opt in
with `./gradlew :app:testDebugUnitTest -PliveApiTests=true`. The free Render instance sleeps when idle
and has taken about 1.5–3 minutes to answer on a cold start, which is why CI does not run it.

For admin screens on mock data, run `cd backend && ./gradlew bootTestRun` and build with
`-PbackendUrl=http://10.0.2.2:8080 -PstubUserId=1`. After a Google sign-in, the mock backend treats
every request as user 1, the admin.
