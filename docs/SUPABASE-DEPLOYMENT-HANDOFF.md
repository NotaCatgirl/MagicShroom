# Supabase deployment and database import: conversation handoff

This document records the Supabase and PostgreSQL setup discussed for MagicShroom, the checks already performed, the errors encountered, and the remaining actions. It is a handoff/status document, not proof that the final Supabase migration or card import has been completed.

## Goal

The backend uses PostgreSQL for application data and Supabase Auth for user identity. The Android app signs in through Supabase Auth, then sends its Supabase access token to the Spring Boot API. The initial card archive is `archive/AllCards.json`. Before importing that archive into the hosted Supabase database, the workflow measures its projected PostgreSQL storage in a separate, disposable local database and enforces a 400,000,000-byte limit.

The relevant instructions and implementation are in:

- `docs/supabase-deployment.md` — original setup and import procedure.
- `backend/README.md` — backend runtime configuration and schema notes.
- `backend/src/main/resources/application.yaml` — datasource and auth issuer environment variables.
- `backend/src/main/resources/db/migration/V1__application_schema.sql` — initial application schema migration.
- `backend/src/main/resources/db/migration/V2__required_value_constraints.sql` — additional validation constraints.
- `backend/src/main/kotlin/edu/csumb/magicshroom/api/importer/CardArchiveImporter.kt` — measure/commit importer and size gate.
- `backend/src/test/resources/measurement-bootstrap.sql` — compatibility objects used only by the disposable measurement database.

## How the services and credentials fit together

There are three distinct pieces of configuration:

1. **Supabase project URL** (`SUPABASE_URL`): used by the backend to form the auth issuer URL `${SUPABASE_URL}/auth/v1`. It is not the PostgreSQL host and does not select the database that receives imported cards.
2. **Supabase PostgreSQL connection** (`DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`): selects the database used by Spring Boot and Flyway. For a Supabase import, this must point at the hosted Supabase database. For local measurement, it must point at the disposable local database.
3. **Android public configuration** (`supabaseUrl`, `supabasePublishableKey`, `backendUrl`): belongs to the Android Gradle configuration. The database password and Supabase service-role key must never be added to the APK.

The Supabase Auth Google provider uses a Google OAuth **Web application** client ID and matching client secret. Google Cloud's authorized redirect URI is the Supabase callback:

```text
https://YOUR_PROJECT_REF.supabase.co/auth/v1/callback
```

Supabase Auth's allowed redirect URL list separately includes the Android deep link:

```text
magicshroom://auth
```

These redirects serve different legs of the OAuth flow: Google returns to Supabase, and Supabase returns to Android. The app manifest and Auth client configure `magicshroom://auth`.

The database password is not the Supabase account login password, a publishable key, or a service-role key. Supabase does not show an existing database password again. If it is forgotten, reset it in the dashboard and save the newly chosen value securely. The user reported resetting it; the exact password was not shared in chat.

## Local PostgreSQL measurement setup

The documented measurement process uses a throwaway PostgreSQL database, separate from the hosted Supabase project. The earlier example Docker configuration was:

```bash
docker run --name magicshroom-measure-postgres \
  -e POSTGRES_USER=magicshroom \
  -e POSTGRES_PASSWORD=measure_only \
  -e POSTGRES_DB=magicshroom \
  -p 5433:5432 \
  -d postgres:15
```

That example maps host port `5433` to PostgreSQL's container port `5432`. The user connected through VS Code and initially saw `inet_server_port()` return `5432`. That is expected: it reports the server's port inside the container. VS Code must use host port `5433` when Docker reports a mapping such as `5432/tcp -> 0.0.0.0:5433`.

The temporary database's intended Spring environment was:

```dotenv
DATABASE_URL=jdbc:postgresql://localhost:5433/magicshroom
DATABASE_USER=magicshroom
DATABASE_PASSWORD=measure_only
```

The user printed `DATABASE_URL` and `DATABASE_USER`; the observed values were `jdbc:postgresql://localhost:5433/magicshroom` and `magicshroom`. This confirmed the measurement run was configured for the local database at that time. Do not infer that a later terminal or run is still using those same values.

The bootstrap SQL creates an `auth` schema with a minimal `auth.users` table and the `anon`/`authenticated` roles so the application migration can run in local PostgreSQL. It is specifically a disposable-database helper; it does not contain the cards or create application tables.

The application schema migration creates `app_users`, `cards`, `decks`, and `deck_cards`. The importer requires an empty `cards` table. In measure mode it inserts the archive within a transaction, calculates database/table size, then rolls back.

## Measurement result and interpretation

The successful local run reported:

```text
Imported 20478 cards; database=118172819 bytes, cards table and indexes=110256128 bytes
Archive measurement rolled back; measured database size=118172819 bytes
```

Use `118172819` as the recorded measured total for the later commit argument. The cards table plus indexes measured `110256128` bytes. The cards were rolled back in measure mode; they were not retained by that run. The measured total is below the configured 400,000,000-byte limit.

The output is a measurement for the local database state and migration/schema used in that run. It should only be treated as comparable when the local PostgreSQL major version matches the target and the projected hosted database contents are considered. A hosted database's current size and any other planned application data contribute to its final size.

The user later ran `SELECT version()` on both local PostgreSQL and Supabase and reported that both showed PostgreSQL 17.6. Thus the major versions matched at that check.

## Supabase connection troubleshooting

The user first tried Supabase's direct connection from VS Code and received:

```text
connect ENETUNREACH ...:5432 - Local (:::0)
```

This indicates their network could not route to the IPv6 direct endpoint. The recommended dashboard path is the project's **Connect** button, choosing the **Session pooler** and using all of the host, port, username, and database values from that same option. Do not mix the direct endpoint's username or host with the pooler's values. The database name is commonly `postgres`, but should be taken from the selected connection details. SSL should be enabled for hosted Supabase connections.

After switching connection settings, the user successfully ran read-only SQL on the hosted project. Results shown were:

- `current_database()` = `postgres`
- `current_user` = `postgres`
- server version = PostgreSQL 17.6
- `pg_database_size(current_database())` = `11070611` bytes (about 10.5 MiB)
- `to_regclass('public.cards')` returned `cards`, so a `public.cards` relation already exists.

The VS Code object tree screenshot showed apparent public tables named `Admin`, `Users`, `cards`, and `deck`. The exact identifiers and complete table list were not confirmed by a text result from `information_schema.tables`/`pg_tables`; casing may matter if a table was created with quoted mixed-case identifiers.

The user checked:

```sql
SELECT to_regclass('public.flyway_schema_history') AS flyway_history;
```

and reported `NULL`, meaning Flyway has not recorded a migration history table at that location yet.

## Flyway behavior and data-loss implications

Flyway is included in `backend/build.gradle.kts` through `flyway-core` and `flyway-database-postgresql`. Its SQL files are under `backend/src/main/resources/db/migration/`; Spring Boot runs Flyway on application startup when enabled by its auto-configuration.

**V1** (`V1__application_schema.sql`) creates the application schema, indexes, and RLS/revocation configuration. Before creation, it includes drops for these exact unqualified/lowercase public table names:

```sql
public.deck_cards
public.decks
public.cards
public.users
```

It then creates `app_users`, `cards`, `decks`, and `deck_cards`. V1 does not drop `app_users`, `Admin`, or singular `deck`; it does not alter `auth.users`. A quoted table such as `public."Users"` is a different PostgreSQL identifier from lowercase `public.users` and may not be removed by V1's `drop table if exists public.users`.

**V2** (`V2__required_value_constraints.sql`) adds check constraints, including nonblank names, array-shaped JSONB card fields, and nonnegative converted mana cost. V1 creates the tables; V2 tightens accepted values.

The user said the hosted database contains no important data besides disposable test data and requested dropping all current tables before recreating them through the migrations. The SQL suggested for that intention was:

```sql
DO $$
DECLARE
    t record;
BEGIN
    FOR t IN
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', t.tablename);
    END LOOP;
END $$;
```

This block was provided as an instruction but **the conversation does not confirm that it has been executed**. It drops every base/partition table listed in `pg_tables` for `public`; `CASCADE` can also remove dependent views, foreign-key constraints, and other dependent objects. It does not target `auth` tables such as `auth.users`. Confirm the current target connection and inspect the target table list before executing. The user explicitly intends to clear current public tables, but should understand the scope of `CASCADE`.

When a database schema is nonempty but has no Flyway history table, Flyway may refuse to migrate until a baseline is established. The earlier suggested startup flags were:

```text
--spring.flyway.baseline-on-migrate=true
--spring.flyway.baseline-version=0
```

Version 0 allows V1 and V2 (versions greater than 0) to run after baselining. If `public` is genuinely empty and Flyway sees no other objects in its configured schema, normal migration should run without baselining. If startup reports a Flyway validation/baseline error, stop and inspect it rather than repeatedly retrying or adding cleanup flags blindly.

## Startup errors encountered and resolved/diagnosed

1. **Missing `SUPABASE_URL`.** The first app startup failed resolving `${SUPABASE_URL}/auth/v1`. Even a local PostgreSQL run needs this environment variable because the backend constructs its JWT issuer. Setting `SUPABASE_URL` configures auth issuer validation; it does not choose or connect the PostgreSQL database.
2. **`relation "cards" does not exist`.** A later run connected to PostgreSQL but the importer failed at `select count(*) from cards`. The bootstrap file alone does not create `cards`; the application migration must run first. The run command also contained a typo, `--spring.flyway.enables=true`; the correct property spelling is `enabled`, with no trailing `s`.
3. **No visible Flyway messages.** At the time, the user was guided to inspect schema/migrations through VS Code and execute SQL manually. The subsequent importer run succeeded. The exact successful command and whether Flyway or the user manually applied the local schema are not established in the conversation.
4. **Gradle exit wording.** Some failed application runs still ended with Gradle printing `BUILD SUCCESSFUL`; the app logs themselves showed `Application run failed`. Judge success from the importer completion messages and application output, not that Gradle line alone.
5. **Generic database connection log.** The app logs `Supabase database connection successful: SELECT 1 returned 1` from a startup connection check. During the confirmed local run, `DATABASE_URL` was `localhost:5433`, so that message did not prove it connected to the hosted Supabase database.

## `.env` use

A local `backend/.env` was discussed as a convenient place for backend variables. Spring Boot/Gradle does not automatically load an arbitrary `.env` file. In a `backend/` shell, the suggested loading sequence is:

```bash
set -a
source .env
set +a
```

Then Gradle launched from that same shell inherits those values. `set -a` exports subsequently assigned/sourced shell variables; `source .env` reads the assignments into the current shell; `set +a` disables automatic export afterward. New terminal sessions must load the file again.

The `.env` must be ignored by Git and must not be committed. Check from the repository root with:

```bash
git check-ignore backend/.env
```

If it is not ignored, add `backend/.env` to the appropriate `.gitignore`. Never put the database password or service-role key in Android Gradle properties or the APK. Do not print or paste the password in chat. A Supabase Session pooler URL includes host, port, and database; the username and password are separate backend variables. The application's `DATABASE_URL` must use JDBC form, such as `jdbc:postgresql://HOST:PORT/DATABASE?sslmode=require`, and the `DATABASE_USER` must match the selected connection mode's username.

## Current status

Confirmed from the conversation:

- Google OAuth client type discussed: Web application; Google callback URI format and Android deep link were identified.
- Local temporary PostgreSQL connection settings were printed and showed `localhost:5433/magicshroom` as `magicshroom`.
- Local measure mode successfully processed 20,478 cards and rolled back.
- Measured total recorded: `118172819` bytes; cards table and indexes: `110256128` bytes.
- Local and hosted PostgreSQL both reported version 17.6.
- VS Code successfully connected to Supabase through an appropriate connection after the direct IPv6 endpoint was unreachable.
- Hosted database was `postgres`, user `postgres`, size `11070611` bytes at the time of the query.
- `public.cards` existed and `public.flyway_schema_history` was `NULL` at the time of inspection.
- The user stated remaining hosted data is unimportant test data and wants current tables dropped and recreated through migrations.

Not confirmed as completed:

- The exact list/casing of all public tables has not been returned as SQL text.
- The suggested `DO ... DROP TABLE ... CASCADE` block has not been confirmed as executed.
- `.env` existence, Git ignore status, and actual shell loading have not been verified.
- No Supabase Flyway migration has been confirmed as applied.
- No card import has been run against Supabase.
- Supabase project version/settings and OAuth sign-in have not been end-to-end tested.

## Recommended continuation

Proceed one checkpoint at a time:

1. In the Supabase VS Code connection, query the exact public table list:

   ```sql
   SELECT schemaname, tablename
   FROM pg_tables
   WHERE schemaname = 'public'
   ORDER BY tablename;
   ```

2. Confirm the selected VS Code connection is the intended Supabase project. Since the user wants all public tables cleared and says their data is disposable, execute the `DROP TABLE ... CASCADE` block above only after checking the list. Then verify:

   ```sql
   SELECT schemaname, tablename
   FROM pg_tables
   WHERE schemaname = 'public';
   ```

3. Confirm the backend `.env` contains the Supabase **Session pooler** JDBC host/port/database and matching username/password, plus `SUPABASE_URL`. Load it in `backend/` with the shell commands above. Check `DATABASE_URL` and `DATABASE_USER` without printing `DATABASE_PASSWORD`; ensure the URL is not `localhost`.

4. Only after target and cleanup are confirmed, start the one-time import from `backend/`:

   ```bash
   ./gradlew bootRun --args='--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=0 --magicshroom.import.path=../../archive/AllCards.json --magicshroom.import.mode=commit --magicshroom.import.measured-total-bytes=118172819'
   ```

   The baseline flags are intended to handle the case where the configured schema is still considered nonempty but has no history table; on a truly empty schema Flyway should run the migrations normally. Watch logs for both V1 and V2, then the importer's committed result and measured database size. The importer must stop if the actual total exceeds `400000000` bytes.

5. If any Flyway error, duplicate relation, connection failure, or size-gate error appears, stop and inspect the precise output. Do not repeatedly rerun the commit command without understanding the state; a successful commit makes the card table nonempty and the importer refuses to run again against a nonempty table.

6. After successful import, start the backend without import arguments for normal operation. Continue with release checks in `docs/supabase-deployment.md` (public card reads, invalid token handling, signed-in deck operations, and ownership checks).

## Important distinction: measuring versus committing

The local `measure` run is intentionally rolled back and is safe for estimating storage on the disposable database. The `commit` run against Supabase permanently writes all archive cards. Before commit, the migration also changes the hosted `public` schema and V1 drops its named legacy tables. Keep database credentials private, verify the target carefully, and retain the measured value for the import gate.
