# Error model

Every error the API returns, from validation failures to unexpected crashes, uses one shape:
**RFC 9457 Problem Details**, sent as `Content-Type: application/problem+json`. Clients never have
to parse HTML error pages or Spring's default error JSON. The contract defines this as the `Problem`
schema and the shared `BadRequest`, `Unauthorized`, `Forbidden`, `NotFound`, `Conflict`,
`ServiceUnavailable`, and `InternalError` responses in [`openapi.yaml`](openapi.yaml).

## Shape

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "Card 1001 was not found.",
  "instance": "/api/v1/cards/1001"
}
```

| Field | Meaning |
|---|---|
| `type` | Always `about:blank`: the status code alone identifies the kind of problem. |
| `title` | The HTTP reason phrase for `status`, such as `Not Found`. |
| `status` | The HTTP status code, repeated in the body. |
| `detail` | A human-readable sentence about this occurrence; safe to show to users. |
| `instance` | The request path that failed. |

## Status codes

| Status | When | Raised by | Example `detail` |
|---|---|---|---|
| **400** Bad Request | Malformed JSON, a missing or invalid field, an out-of-range query parameter, an unknown `sort` value | `BadRequestException`, Bean Validation (`@Valid`, `@Min`, `@Max`, `@Size`), Jackson parse errors | `size must be less than or equal to 100`; `Missing or invalid value for 'cardIds'.`; `Send at least one field to change.` |
| **401** Unauthorized | No bearer token, an invalid or expired token, or a token for a deleted account | Spring Security via `ProblemAuthenticationEntryPoint`; `UnauthorizedException` | `A bearer token is required.`; `The bearer token is invalid or expired.` |
| **403** Forbidden | Signed in, but the route needs the `ADMIN` role | `ForbiddenException` from `CurrentUser.requireAdmin()` | `This operation requires the ADMIN role.` |
| **404** Not Found | The resource does not exist, **or it belongs to another user** | `NotFoundException` | `Deck 42 was not found.`; `Cards not found: 7, 9.` |
| **409** Conflict | The change clashes with existing data | `ConflictException` | `Card 12 is still used by a deck.`; `At least one admin must remain.` |
| **503** Service Unavailable | A required external service is missing or failing (Supabase Auth admin API for account deletion) | `ServiceUnavailableException` | `Account deletion is not configured on this server.` |
| **500** Internal Server Error | Anything unexpected | `ApiExceptionHandler.handleUnexpected` | `Unexpected server error.` |

Spring's own framework errors, such as an unsupported HTTP method (405) or media type (415), pass
through the same handler and also arrive as Problem Details.

## Rules the implementation follows

- **One handler.** `ApiExceptionHandler` (a `@RestControllerAdvice` extending
  `ResponseEntityExceptionHandler`) turns every exception into a `ProblemDetail`. Services throw the
  sealed `ApiException` subclasses in `error/ApiExceptions.kt`, and the subclass chooses the status.
- **Validation names the field.** Spring's generic "Invalid request content" is replaced with the
  failing fields and messages, joined by `; `, so a client can show which input was wrong.
- **401s look the same.** Errors from the security filter chain are written by
  `ProblemAuthenticationEntryPoint` in the same shape, and keep the `WWW-Authenticate: Bearer` header.
- **No information leaks.** A 500 never includes a stack trace or exception message (those go to the
  server log), and another user's deck returns 404 rather than 403 so IDs cannot be probed.
- **Clients read `detail`.** The Android app decodes errors into `ProblemDto` and shows `detail`
  to the user.

## Tested in

- `ApiRoutesTest` and `UserRoutesTest`: 400, 403, 404, and 409 bodies and their `detail` text.
- `ProblemAuthenticationEntryPointTest`: 401 bodies and the bearer challenge.
- `SupabasePostgresAuthIntegrationTest` (REST Assured on a Testcontainers PostgreSQL database):
  401 for missing, wrongly signed, expired, and wrong-audience tokens, and 409 for deleting a card in use.
