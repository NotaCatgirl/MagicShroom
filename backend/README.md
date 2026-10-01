# MagicShroom API (backend)

Spring Boot 4 + Kotlin REST API. The contract lives in [`../docs/openapi.yaml`](../docs/openapi.yaml);
write it there first, then implement it here.

## Run it

Open **this folder** (`backend/`) in IntelliJ IDEA, not the repo root. Requires JDK 25.

```bash
./gradlew bootRun   # http://localhost:8080
./gradlew build     # compile + all tests
```

For now it uses in-memory H2, so it starts with no setup. Replacing that with the team's hosted
Postgres (plus a `docker-compose.yml` for local dev) is Sprint 1–2 work.

## Package layout (`edu.csumb.magicshroom.api`)

Add these packages as you need them:

| Package | Holds |
|---|---|
| `config/` | `SecurityConfig` (OAuth2 Resource Server), OpenAPI/SpringDoc config |
| `controller/` | `@RestController`s, one per resource, routes under `/api/v1/...` |
| `service/` | Business logic. **The 70% JaCoCo floor is measured on this package.** |
| `repository/` | Spring Data interfaces |
| `model/` | Database entities |
| `dto/` | Request and response bodies (never return entities directly) |
| `error/` | `@RestControllerAdvice` that turns exceptions into RFC 9457 `ProblemDetail` |

Tests mirror this under `src/test/kotlin/edu/csumb/magicshroom/api/`: `service/` for JUnit 5 unit
tests with mocked repositories, `integration/` for REST Assured + Testcontainers.
