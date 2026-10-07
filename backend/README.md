# MagicShroom API (backend)

Spring Boot 4 + Kotlin REST API. The contract lives in [`../docs/openapi.yaml`](../docs/openapi.yaml);
write it there first, then implement it here.

## Run it

Open **this folder** (`backend/`) in IntelliJ IDEA, not the repo root. Requires JDK 25.

```bash
./gradlew bootRun   # http://localhost:8080
./gradlew build     # compile + all tests
```

## Data: mock JSON for now

Until the Supabase repositories exist, the API serves data from
[`src/main/resources/mock-data/`](src/main/resources/mock-data): `cards.json` (22 cards),
`users.json`, `decks.json`, `deck_cards.json`. Changes made through the API live in memory and are
lost on restart. Edit the files to change the starting data.

How to swap in the real database: implement the four interfaces in `repository/`
(`CardRepository`, `DeckRepository`, `DeckCardRepository`, `UserRepository`), annotate the new
classes with their own profile (e.g. `@Profile("supabase")`), and change `spring.profiles.default`
in `application.yaml`. Services and controllers do not change.

## Auth: stub until OAuth2 lands

Protected routes take the caller from the `X-Stub-User-Id` header (default user **2** when absent).
In the mock data, user **1** is the admin, and users **2** and **3** are regular users who own
decks 1–2 and deck 3. An unknown id gets 401. This is `auth/StubCurrentUserResolver`, which logs a
warning at startup; replace it with a JWT-based resolver. Controllers keep their `CurrentUser`
parameter.

## Try the routes

```bash
curl "localhost:8080/api/v1/cards?name=bolt"                     # public, paginated
curl "localhost:8080/api/v1/cards?color=R&sort=convertedManaCost,desc&page=0&size=5"
curl localhost:8080/api/v1/decks                                 # user 2's decks
curl -H "X-Stub-User-Id: 3" localhost:8080/api/v1/decks/3        # user 3's deck (404 for user 2)
curl -X POST localhost:8080/api/v1/decks/1/cards \
     -H "Content-Type: application/json" -d '{"cardId": 9, "quantity": 2}'
curl -X DELETE -H "X-Stub-User-Id: 1" localhost:8080/api/v1/cards/21   # admin only (403 otherwise)
```

Every error is RFC 9457 Problem Details (`application/problem+json`) with a `detail` that says what
went wrong, e.g. `"size must be greater than or equal to 1"`.

## Package layout (`edu.csumb.magicshroom.api`)

| Package | Holds |
|---|---|
| `controller/` | One `@RestController` per resource; routes under `/api/v1/...` exactly as in the contract |
| `service/` | Business rules: filtering, paging, ownership, admin checks, conflicts. **The 70% JaCoCo floor is measured here.** |
| `repository/` | Storage interfaces; `repository/json/` holds the mock implementations |
| `model/` | Domain types shaped like the contract's schemas |
| `dto/` | Request bodies and composed responses (`PageResponse`, `DeckResponse`) |
| `auth/` | `CurrentUser` and the stub resolver that supplies it |
| `error/` | `ApiException` types and the handler that turns every error into Problem Details |
| `config/` | Spring configuration |

Tests mirror this under `src/test/kotlin/edu/csumb/magicshroom/api/`. `controller/ApiRoutesTest`
runs the routes end to end against the mock data. Still to add: `service/` unit tests with mocked
repositories, and `integration/` tests with REST Assured + Testcontainers.
