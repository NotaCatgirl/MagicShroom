# MagicShroom Android app (frontend)

Kotlin + Jetpack Compose client that proves the API works end to end. Four screens: Login,
List, Detail, Admin.

## Run it

Open **this folder** (`frontend/`) in Android Studio, not the repo root. Let Gradle sync, pick an
emulator, press Run.

```bash
./gradlew :app:assembleDebug       # debug APK
./gradlew :app:testDebugUnitTest   # local unit tests
```

## Talking to the API

- From the emulator, your computer is `10.0.2.2`, not `localhost`.
- No backend yet? Run the mock from the repo root: `npx @stoplight/prism-cli mock docs/openapi.yaml`,
  then point the app at `http://10.0.2.2:4010`.
- Plain HTTP to `10.0.2.2` needs a network security config. The deployed API must be HTTPS.

## Package layout (`edu.csumb.magicshroom`)

Add these packages as you need them:

| Package | Holds |
|---|---|
| `data/api/` | Retrofit service interface and DTOs matching `docs/openapi.yaml` |
| `data/auth/` | Authorization Code + PKCE login (AppAuth), token storage |
| `data/repository/` | Wraps the API for ViewModels; maps HTTP errors (401/403/404/500) |
| `ui/login/`, `ui/list/`, `ui/detail/`, `ui/admin/` | One package per screen: `Screen` + `ViewModel` |
| `ui/nav/` | `AppNavHost` |
| `ui/theme/` | Compose theme |
