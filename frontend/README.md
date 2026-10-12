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

Start the backend first (`cd ../backend && ./gradlew bootRun`). The Search screen loads cards from it
through `data/api/MagicShroomApi.kt` (Retrofit; every contract route is already declared there).

- The debug build calls `http://10.0.2.2:8080/` (`API_BASE_URL` in `app/build.gradle.kts`). From the
  emulator, your computer is `10.0.2.2`, not `localhost`.
- Plain HTTP is allowed only to `10.0.2.2` and `localhost` (`res/xml/network_security_config.xml`).
  The deployed API must be HTTPS.
- Until OAuth sign-in exists, `StubAuthInterceptor` sends `X-Stub-User-Id` (`STUB_USER_ID`).
- No backend? Run the mock from the repo root: `npx @stoplight/prism-cli mock docs/openapi.yaml`,
  then set `API_BASE_URL` to `http://10.0.2.2:4010/`.

To call a new route: add a repository function wrapping it in `apiCall { ... }` (see
`CardRepository`), expose it from `data/AppContainer.kt`, and handle `ApiResult.Success` /
`ApiResult.Failure` in the ViewModel (see `ui/search/SearchViewModel.kt`).

## Open work

Unfinished pieces are marked in code as `TODO(area)`, with area one of `auth`, `db`, `api`, `ui`,
`nav`, `contract`, `deploy`, `test`. See them in Android Studio's **TODO** tool window or with
`grep -rn "TODO(" ..` from this folder.

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
