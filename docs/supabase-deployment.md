# Supabase setup and card import

1. Enable Google in Supabase Auth and register the Android redirect URL
   `magicshroom://auth` in the allowed redirect URLs. Use an asymmetric JWT signing key so the
   backend can verify access tokens against the project JWKS endpoint. Record the Supabase URL
   and publishable key for Android; keep the database password on the backend only.
2. Create a **disposable** PostgreSQL database with the same major version as Supabase. Run
   `backend/src/test/resources/measurement-bootstrap.sql` there as a superuser; this supplies
   the `auth.users` reference and Supabase roles for Flyway. Set `DATABASE_URL`, `DATABASE_USER`,
   and `DATABASE_PASSWORD` to that temporary database.
3. Run the backend from `backend/` with:

   ```bash
   ./gradlew bootRun --args='--magicshroom.import.path=../../archive/AllCards.json --magicshroom.import.mode=measure'
   ```

   Flyway installs the schema. The importer loads the unchanged archive, reports
   `pg_database_size(current_database())` and `pg_total_relation_size('cards')`, then rolls back.
   Include any additional application tables/data planned for the Supabase project when judging
   total size. The complete projected database must be at most **400,000,000 bytes**. If it is
   larger, stop and decide what to do before dropping any archive fields.
4. Point the backend at Supabase's server-side PostgreSQL connection and set `SUPABASE_URL`.
   Start it with:

   ```bash
   ./gradlew bootRun --args='--magicshroom.import.path=../../archive/AllCards.json --magicshroom.import.mode=commit --magicshroom.import.measured-total-bytes=MEASURED_BYTES'
   ```

   The importer refuses commit without a recorded measurement at or below the threshold and
   refuses to run against a nonempty card table. Flyway removes the four disposable application
   tables but never changes `auth.users`. Import occurs once; later starts omit import arguments.
5. Configure Android Gradle properties `supabaseUrl`, `supabasePublishableKey`, and `backendUrl`
   (for an emulator, `http://10.0.2.2:8080`). The publishable key is public; no database password
   or service role key belongs in the APK. Google sign-in returns to `magicshroom://auth`, and
   the app sends the resulting Supabase access token to the backend as a bearer token.

To grant catalog administration, sign in once, then set `app_users.role = 'ADMIN'` for that
account's `auth_user_id` using a trusted server-side SQL session. The application never derives
admin access from Google profile data or client-supplied claims.

Check a public card page, a card detail, a missing/invalid bearer token (401), a signed-in
deck create/add/remove flow, and a deck belonging to a different user (404) before release.
Build the backend image with `docker build -t magicshroom-api backend` from the repository root.
