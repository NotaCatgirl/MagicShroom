# Continuation packet: Supabase integration with compact card storage

This file records the state at the user's requested pause on 2026-10-13. All implementation
changes described below are **uncommitted** in the `MagicShroom` Git repository. Do not treat
the implementation as tested or ready to deploy. The current agent has no worker lifecycle
controls or context-usage telemetry; this file is the explicit handoff artifact.

## Original user request and inherited plan

The user asked: “A previous agent produced the plan below to accomplish the user's task.
Implement the plan in a fresh context. Treat the plan as the source of user intent, re-read files
as needed, and carry the work through implementation and verification.” The user later said
“resume,” then “pause here for now, we'll continue another time so write a context file
including: the original prompt, and what was changed since you began working. be very detailed.”

The inherited plan was titled **Supabase integration with compact card storage**:

> `jsonb` stores JSON in a PostgreSQL format, but selecting it still returns normal readable
> JSON. Whitespace and object-key order need not match the original text. Use `text[]` for simple
> card lists and `jsonb` for structured card details instead of separate detail tables. Keep the
> current card archive unchanged.
>
> A read-only scan of `archive/AllCards.json` found 20,478 cards in a 68.6 MB file. The six
> simple-list fields contain about 1.0 MB of serialized values; `foreignData`, `legalities`,
> `purchaseUrls`, and `rulings` contain about 43.9 MB, including 31.6 MB of `foreignData`.
> Those figures are not PostgreSQL size measurements. Before importing into Supabase, load the
> unchanged archive into a temporary PostgreSQL database and measure the complete database plus
> table/index size. Supabase Free becomes read-only above 500 MB; use 400 MB as the go/no-go
> threshold. If projected total exceeds that, stop and ask before dropping any card data.
>
> Replace disposable application tables with a versioned schema: typed card scalars, `text[]`
> for colors, color identity, types, subtypes, supertypes, and printings, and `jsonb` for foreign
> data, legalities, purchase URLs, and rulings. Keep `auth.users` untouched. Preserve numeric
> API user/deck/card IDs and link application users to `auth.users` through a separate UUID.
> Store decks and deck cards relationally and admin rights as a user role. Use PostgreSQL-backed
> production repositories, import the existing archive once, retain current card API responses,
> and confine mock data to isolated tests. Validate Supabase Auth bearer tokens on protected
> routes; add Android Google sign-in through Supabase Auth; send the access token to the backend;
> keep DB credentials out of the app. Complete core card-browse and personal-deck flow and verify
> the Docker image. Give every new function, including helpers, overrides, and tests, a one-line
> KDoc/Javadoc comment.
>
> Test JSONB/array round-trips, search/pagination, deck ownership and writes, missing/rejected
> tokens, Google sign-in handoff, and the production-profile Docker build. Compare API JSON
> before and after storage changes. Existing application-table rows are disposable. The 2019
> archive remains the import source until a separate dataset-refresh decision.

The previous planning pass changed no files. At the start of this implementation pass,
`git status --short` under `MagicShroom` was clean. The workspace root is
`/home/kyle/CSUMB/semester3/swe/proj2/api`; the Git repository and two Gradle projects are
under `/home/kyle/CSUMB/semester3/swe/proj2/api/MagicShroom`.

## Completed edits in this pass

### Backend build, configuration, and schema

- `backend/build.gradle.kts`: removed the H2 console and JPA starter/test starter; added Spring
  Security OAuth2 Resource Server, Flyway core, Flyway PostgreSQL support, and Actuator. Kept
  Spring Data JDBC, PostgreSQL runtime driver, H2 test runtime, Kotlin, and MVC dependencies.
- `backend/src/main/resources/application.yaml`: production now defaults to the `postgres`
  profile. Reads `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, and `SUPABASE_URL` from
  server environment. Auth issuer is `${SUPABASE_URL}/auth/v1`. Default local JDBC URL and user
  are placeholders for local PostgreSQL; a real Supabase connection must be configured.
- `backend/src/test/resources/application.properties`: default test profile remains `mock`,
  Flyway is disabled in tests, and the old JPA/Hibernate test settings were removed.
- `backend/src/main/resources/db/migration/V1__application_schema.sql`: Flyway migration drops
  the four disposable `public` application tables in dependency order (`deck_cards`, `decks`,
  `cards`, `users`); it does not drop `auth.users`. It creates `app_users` with numeric ID,
  `auth_user_id` UUID FK to `auth.users`, email, display name, and USER/ADMIN role; `cards` with
  typed scalars, `text[]` lists, and JSONB detail columns; relational `decks` and `deck_cards`
  with constraints and indexes. It includes archive-only card fields such as layout, power,
  toughness, platform IDs, color indicator, names, and leadership skills. It enables RLS and
  revokes direct `anon`/`authenticated` privileges from the app tables and their identity
  sequences. The backend is intended to connect as a trusted PostgreSQL owner role.
- Deleted obsolete `UserEntity.kt`, `JpaUserRepository.kt`, and their JPA test. This removal was
  intentional because the production data model is now the Flyway/JDBC model.

### Backend repositories and import

- `backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/PostgresRepositories.kt` is a
  new `!mock` repository implementation for cards, users, decks, and deck cards. Cards bind
  PostgreSQL `text[]` arrays and `jsonb` values through JDBC. Normal card listing filters,
  sorts, counts, limits, and offsets in SQL. User lookup or creation is keyed by validated Auth
  UUID; role stays in `app_users`. Deck and deck-card writes are relational.
- `CardRepository.kt` now has a `search(...)` method with a default in-memory implementation
  for mock tests. `CardService.list(...)` delegates to it so production avoids reading the full
  archive for each page. `UserRepository.kt` has `findOrCreate(...)` for a validated Auth UUID;
  the mock default throws if accidentally called. The mock card repository got a KDoc line.
- `CardService`, `DeckService`, and `UserService` now have `@Transactional` at class level.
- `backend/src/main/kotlin/edu/csumb/magicshroom/api/importer/CardArchiveImporter.kt` is a new
  explicit one-time `ApplicationRunner`. With no import path it does nothing. With
  `magicshroom.import.mode=measure`, it parses the unchanged `AllCards.json`, requires an empty
  cards table, imports all cards in a transaction, reports `pg_database_size` and
  `pg_total_relation_size('cards')`, enforces a **400,000,000 byte** limit, and rolls back. With
  mode `commit`, it additionally requires a supplied measured total at or below that limit,
  then commits. It maps archive legalities and URLs into the existing API model and preserves
  archive-only scalar fields and the complete raw `foreignData` array in DB storage. The
  repository projects foreign data back to the stable three-field API response type.
- A read-only `jq` audit after editing confirmed all 20,478 archive cards, every top-level key,
  the `foreignData` keys (`flavorText`, `language`, `name`, `text`, `type`), and ruling keys
  (`date`, `text`). `hand` and `life` are strings in the archive and were accordingly stored as
  text. No archive file was edited.

### Backend authentication

- `backend/src/main/kotlin/edu/csumb/magicshroom/api/auth/SupabaseSecurity.kt` configures a
  `!mock` Spring Security filter chain: public card GET and `/actuator/health`, bearer token
  required for the rest. It uses the project JWKS endpoint, trusts RS256 and ES256, and checks
  issuer, time claims, audience `authenticated`, and role claim `authenticated`. Its argument
  resolver obtains the verified JWT principal, parses the UUID `sub`, requires an email claim,
  creates/loads the numeric application user, and supplies `CurrentUser` with the DB role.
- The existing stub resolver is now `@Profile("mock")` only. `WebConfig.kt` registers the active
  resolver list. Comments in `CurrentUser.kt` and the stub resolver were updated.
- `backend/src/test/kotlin/edu/csumb/magicshroom/api/auth/SupabaseCurrentUserResolverTest.kt`
  adds direct resolver tests for Auth-subject handoff and missing/email-less JWT principals.
  These tests have **not run** in this sandbox.

### Android client

- `frontend/app/build.gradle.kts` now includes Supabase `auth-kt` instead of PostgREST. It
  generates build config values for `backendUrl`, `supabaseUrl`, and
  `supabasePublishableKey` Gradle properties; no DB password or service role key is added.
- `frontend/app/src/main/AndroidManifest.xml` registers `magicshroom://auth` on
  `MainActivity` with `singleTop` launch mode. A new debug-only manifest permits cleartext
  HTTP for local emulator development.
- `frontend/app/src/main/java/edu/csumb/magicshroom/ApiClient.kt` adds public card paging/search
  and bearer-token deck list/create/add/remove requests using `HttpURLConnection` on
  `Dispatchers.IO`. It parses the existing API JSON with Android `org.json`.
- `frontend/app/src/main/java/edu/csumb/magicshroom/MainActivity.kt` replaces the starter
  greeting screen. It creates the Supabase Auth client, handles initial and returning OAuth deep
  links, observes session changes, displays a Google sign-in/out button, searches/paginates
  cards, shows selected card details, lists/creates personal decks, and adds/removes a selected
  card. This client has **not been compiled or emulator-tested** here.

### Docker and documentation

- `backend/Dockerfile` is now a multi-stage JDK 25 build/JRE 25 Alpine runtime with
  `/actuator/health` as its health check. The backend build added Actuator and the security
  rules permit this health route.
- Rewrote root `README.md`, `backend/README.md`, and `frontend/README.md` to describe the new
  configuration and workflow. Added `docs/supabase-deployment.md` with temp PostgreSQL
  measurement, 400 MB gate, import commands, Android properties, Google redirect setup, and
  server-side admin role promotion. Added
  `backend/src/test/resources/measurement-bootstrap.sql` for a disposable local database
  (`auth.users` stub plus `anon`/`authenticated` roles).

## Validation actually performed

- `git diff --check` ran without whitespace errors before this handoff file was added.
- `jq` confirmed the archive contains 20,478 cards and inspected all top-level, foreign-data,
  and ruling keys and field types. The archive remains unchanged.
- Checked current Supabase documentation for JWT JWKS verification, Android OAuth deep links,
  Auth session access, and Google sign-in. Checked Spring Security documentation for JWKS
  decoder algorithms. Confirmed from cached Supabase 3.5.0 Android AAR with `javap` that `Auth`,
  `handleDeeplinks`, `currentSessionOrNull`, `scheme`, `host`, `signOut`, and `Google` APIs exist.
- `docker info` and `docker build -t magicshroom-api:integration backend` failed because access
  to `/home/kyle/.docker/desktop/docker.sock` is denied. No image was built.
- `./gradlew test --console=plain` first failed because the default Gradle home is read-only.
  A fresh `/tmp` Gradle home then could not download Gradle due sandbox network restrictions.
  A direct cached Gradle binary with a writable `/tmp` home and read-only dependency cache
  failed before project configuration: `Could not determine a usable wildcard IP for this
  machine.` The same failure occurred trying Android `:app:compileDebugKotlin`. Thus neither
  backend tests nor Android compilation ran. The new Flyway and Security dependencies also are
  not all present in the existing local cache.
- No PostgreSQL tools were available (`psql` absent), the Docker daemon was inaccessible, and
  no temporary PostgreSQL or Supabase database was accessed. No migration was applied and no
  archive import ran. The true database size and 400 MB go/no-go status remain unknown.

## Decisions and constraints

- The repository was clean at start. All current Git changes are from this implementation
  pass; they have not been staged or committed. Do not discard them when resuming.
- Existing `archive/AllCards.json` is outside the Git repository at
  `/home/kyle/CSUMB/semester3/swe/proj2/api/archive/AllCards.json`. It is the import source,
  is unchanged, and should not be refreshed as part of this task.
- The old application table rows are explicitly disposable under the user's plan. The Flyway
  migration contains destructive `DROP TABLE IF EXISTS public...` statements; review the
  exact database target and measure in a disposable PostgreSQL database before any Supabase
  run. `auth.users` must remain untouched.
- The 400 MB threshold uses decimal bytes (`400_000_000`), leaving headroom under the cited
  500 MB Supabase Free limit. The importer enforces the threshold in code, but its runtime
  behavior has not yet been tested in PostgreSQL.
- The API's numeric IDs and response model remain. Application users use a separate UUID FK
  to Auth. Admin access is from `app_users.role`, never a client claim. Deleting an application
  user currently leaves the Supabase Auth user intact, as required by the plan's direction to
  keep `auth.users` untouched.
- Current mock route tests are intended to stay isolated under `mock`; production defaults to
  PostgreSQL. The user asked for one-line KDoc/Javadoc on every new function. New functions
  were annotated while editing, but this should be checked again on resume.

## Risks and unfinished work

1. **Compilation is unverified.** Start by building both Gradle projects in an environment
   where Gradle can use sockets and resolve dependencies. Fix any Kotlin, Spring Boot 4,
   Jackson 3, Supabase SDK, SQL, or Android API errors that surface. The current source has
   only had static inspection.
2. **PostgreSQL behavior is unverified.** Run Flyway against a disposable PostgreSQL database
   with the bootstrap SQL, then test `text[]`/JSONB round-trips, archive import and rollback,
   search ordering/pagination, user UUID linkage, deck ownership, and deck writes. Compare
   representative API JSON before and after persistence. The newly added resolver test covers
   only the handoff from an already verified JWT principal; add/execute rejected-token route
   tests as feasible.
3. **Size gate is unresolved.** Measure the complete database plus card table/index sizes in
   temporary PostgreSQL before any Supabase import. Stop for user direction if projected total
   exceeds 400,000,000 bytes. Do not claim the JSON file's 68.6 MB size is a DB measurement.
4. **Android sign-in is unverified.** Configure a Supabase project with Google and
   `magicshroom://auth`, compile the app, test deep-link return and bearer-token deck calls on
   an emulator, and fix UI/SDK issues. The current UI is a compact first pass, not a polished
   multi-screen app.
5. **Docker is unverified.** Build the production image when a daemon is available and check
   the health endpoint and runtime environment injection. No image or external deployment was
   created in this pass.
6. **Possible implementation details to inspect:** the migration's role grants/identity
   sequence names and DROP ordering on a real Supabase schema; the importer transaction and
   PostgreSQL size query; the JDBC card update placeholder ordering; exact JSONB handling for
   archive extras; Spring Security's error response shape; and Gradle property escaping for
   Android `BuildConfig` strings. These are audit items, not confirmed defects.

## Immediate next action on resume

Read this file and `git status --short`, then obtain a working Gradle/PostgreSQL environment and
compile the backend and Android app before making more feature changes. Use the first compiler
or migration error to drive focused fixes. Preserve the current uncommitted work and the archive.

## Update after resuming (2026-10-13)

The sections above are the original pause snapshot. After resuming, the agent fixed a Jackson 3
`JsonNode.map` mismatch by converting array nodes with `toList().map`, extracted
`ArchiveCardMapper`, and added focused mapper tests. It moved the mock JSON fixtures from main
resources to test resources so they are not packaged with the production application. A direct
Kotlin compiler check succeeded for the PostgreSQL repositories, importer, mapper, and mapper
tests, with deprecation warnings only. A read-only archive check found no unfamiliar legality
statuses and no missing required UUID/name or ruling fields. `git diff --check` passed.

The full backend and Android Gradle suites still have **not run**: this sandbox's default Gradle
cache is read-only, and writable-cache attempts are blocked by network/socket restrictions.
JShell also could not start its execution engine because local socket creation was denied.
There is still no PostgreSQL size measurement, Supabase import, emulator run, or Docker build.

The user requested one descriptive commit per work section, then a pause for them to run tests.
The resulting commits are `7002209` (PostgreSQL schema, repositories, archive import),
`7acac71` (Supabase bearer-token security), `171127f` (Android sign-in and deck flow), and
`bd2bdd7` (Docker image). Documentation is committed separately. Do not continue implementation
until the user sends test results or asks to resume.
