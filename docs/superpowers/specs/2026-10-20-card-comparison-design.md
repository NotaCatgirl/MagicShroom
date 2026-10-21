# Card comparison: design

Date: 2026-10-20 · Branch: `feature/card-comparison` · Status: approved design, pending spec review

## Goal

From a card's detail screen, a player taps **Compare with…**, picks one of the most similar cards,
and sees the two side by side with every stat marked **better**, **worse**, **equal**, or **unique**.

## Decisions

| Question | Decision |
|---|---|
| What counts as "similar" | Shares a main type (Creature, Instant, …); same color identity ranked first, widening to cards that share at least one color; then closest mana value, most shared subtypes, most popular. |
| Comparison layout | Both card images, a one-line tally, a stat table (icon + word + color per row), then traits unique to each card. |
| Where the logic lives | Backend serves the stats and the similar-card list; the app computes the comparison. |

Research that shaped this: [Grimoria's suggestions](https://grimoria.app/help/discovery/suggestions)
compare a candidate to a reference card on mana value, keywords, power/toughness, colors, and types;
[Casual Planeswalker](https://casualplaneswalker.com/card/solar-blaze/similar) lists about six similar
cards per card; WCAG 1.4.1 (use of color) requires a non-color signal for every verdict; and
[a comparison-UI write-up](https://peerlist.io/ethanjamescolez/articles/what-building-a-gear-comparison-tool-taught-me-about-auditable-ui)
argues that missing values must look different from zero.

## Out of scope

Comparing against an arbitrary searched card, swapping which card is the reference, legal formats,
prices, and comparing more than two cards. No database migration: every column already exists.

## 1. Backend

### Card stats in responses

`Card` (model, OpenAPI `Card` schema) gains four nullable, read-only fields, read from columns the
archive import already filled:

| Field | Column | Type | Notes |
|---|---|---|---|
| `power` | `power` | string | Text because of values such as `*` and `1+*`. 10,747 cards. |
| `toughness` | `toughness` | string | Same as power. |
| `loyalty` | `loyalty` | string | Planeswalkers. 204 cards. |
| `edhrecRank` | `edhrec_rank` | integer | Lower is more popular. 19,305 cards. |

`PostgresCardRepository` adds the columns to its select list and row mapper only. Insert and update
keep their current column lists, so admin create/replace/patch never write these fields; replace and
patch already re-read the card afterwards, so their responses show the stored values. `CardInput`
and `CardPatch` do not change. The JSON mock fixtures gain stats on a few cards for tests.

### `GET /api/v1/cards/{cardId}/similar?limit=10`

Public, like the other card reads. Returns `Card[]`, best match first.

- `limit`: default 10, 1–30; anything else is 400 (Problem Details, like the other card routes).
- Unknown `cardId` is 404, via `CardService.get`.
- Candidates: every other card (`id` differs) whose `types` share at least one entry with the base card's,
  excluding other faces of the same card (same `scryfallOracleId`, when the base card has one; 248 oracle
  ids in the catalog have two or more face rows, such as Delver of Secrets / Insectile Aberration).
- Tier 0: same color identity as a set (two colorless cards match). Tier 1: shares at least one color.
  Cards in neither tier are excluded, so a colorless card is matched only to other colorless cards.
- Order: tier, then `|mana value − base mana value|` (missing mana value counts as 0), then number of
  shared subtypes (descending), then `edhrecRank` (ascending, unranked last), then `id`.

Because tier 1 sorts after tier 0, results widen to shared colors only when fewer than `limit`
same-identity cards exist.

Implementation:
- `CardRepository.findSimilar(card: Card, limit: Int): List<Card>` gets a default Kotlin
  implementation over `findAll()` (used by the mock profile), mirroring how `search` works today.
- `PostgresCardRepository` overrides it with one SQL query. The query must not alias the `cards`
  table, because the shared column list references `cards.scryfall_oracle_id`.
- `CardController` validates `limit` with `@Min(1) @Max(30)`, like the list route's `size`;
  `CardService.similar(cardId, limit)` loads the base card (404 if missing) and delegates.
- `SupabaseSecurity` adds `/api/v1/cards/*/similar` to the public GET matchers; `/api/v1/cards/*`
  matches only one path segment.
- `docs/openapi.yaml` documents the route and the four fields.

## 2. Comparison rules (app)

A pure Kotlin function, `compareCards(base: CardDto, other: CardDto): CardComparison`, with no
Android dependencies. Verdicts are always from the base card's point of view (the card the player
started on).

```kotlin
enum class Verdict { BETTER, WORSE, EQUAL, NOT_COMPARABLE }
data class StatRow(val label: String, val baseValue: String, val otherValue: String, val verdict: Verdict)
data class CardComparison(
    val rows: List<StatRow>,
    val shared: List<String>,
    val uniqueToBase: List<String>,
    val uniqueToOther: List<String>,
)
```

### Stat rows

| Row | Shown when | Better when | Value text |
|---|---|---|---|
| Mana value | always | lower | `5`, or `—` if missing (then NOT_COMPARABLE) |
| Power | either card has one | higher | `4`; `*` or missing → NOT_COMPARABLE, missing shown as `—` |
| Toughness | either card has one | higher | same as power |
| Loyalty | either card has one | higher | same as power |
| Popularity | either card is ranked | lower rank | `#4698`; unranked shown as `Unranked` (NOT_COMPARABLE) |
| Colors | identities are equal | n/a | always EQUAL; differing colors become unique traits |

Numbers compare as decimals, so `1.5` and `2` work. Equal numbers are EQUAL.

### Traits (shared and unique)

A card's traits are: its keyword abilities, its subtypes (Angel, Elf, …), its supertypes
(Legendary, …), its main types (Artifact, Creature, …), and, only when the two color identities
differ, its color names (White, Blue, Black, Red, Green, or Colorless). When the identities are
equal the Colors row already says so. A trait on both cards goes in `shared`; otherwise it is
unique to one. Each list keeps this order: keywords, subtypes, supertypes, main types, colors.

Keyword abilities are read from rules text, only from lines made up entirely of comma-separated
keywords (for example `Flying, vigilance` or `Ward {2}`), so a line such as "Creatures you control
have flying" does not count. Reminder text in parentheses is ignored. Recognized keywords:
Flying, First strike, Double strike, Deathtouch, Defender, Flash, Haste, Hexproof, Indestructible,
Lifelink, Menace, Prowess, Reach, Trample, Vigilance, Ward, and Protection. `Ward {2}` counts as Ward,
and `Protection from red` as Protection.

### Tally

`▲ better` and `▼ worse` count rows with those verdicts; `= equal` counts EQUAL rows; `★ unique`
counts `uniqueToBase` plus `uniqueToOther`. NOT_COMPARABLE rows are shown but not counted.

## 3. Screens and navigation

- `CardDetailContent` gets `onCompareClick: (() -> Unit)? = null` and shows an outlined
  **Compare with…** button below the rules text when it is set. Existing callers keep compiling.
- New route `cards/{cardId}/compare` → **Similar cards screen**: top bar "Compare <name> with…", then
  a grid of `SearchCardTile`s (changed from `private` to `internal` so it can be reused). Tapping a
  tile opens the comparison.
- New route `cards/{cardId}/compare/{otherId}` → **Comparison screen**:
  - the two card images side by side (small scans), names under each;
  - the tally line;
  - the stat table: label, base value, verdict badge, other value;
  - "Shared", "Unique to <base>", and "Unique to <other>" trait lists, each trait with a ★ badge.
- Verdict badge: icon + word + color: ▲ Better (green), ▼ Worse (red), = Equal (gray), ★ Unique
  (brand purple), — Not comparable (gray). Each table row has a screen-reader description such as
  "Power: Serra Angel 4, Baneslayer Angel 5, worse".
- Screens follow `CardDetailScreen`'s pattern (`remember` + `LaunchedEffect` + `ApiResult`, Retry on
  failure). The comparison screen loads both cards with `getCardsByIds`, which hits the cache that
  the similar list just filled.

## 4. Data flow and errors (app)

- `CardDto` gains `power`, `toughness`, `loyalty`, `edhrecRank`, all nullable with `null` defaults,
  so the app still parses responses from an older server.
- `MagicShroomApi.getSimilarCards(cardId, limit)` calls the new route.
  `CardRepository.getSimilarCards(cardId, limit = 10)` returns `ApiResult<List<CardDto>>` and caches
  each card.
- Loading shows a spinner. Failures show `userMessage` with Retry and Back.
- An HTTP 404 from the similar route (a server without it, such as Render before redeploying) shows
  "Comparisons need the latest server version." An empty list shows "No similar cards found."

## 5. Testing

Backend:
- Mock-profile route tests: ranking order (tier, then mana value, subtypes, popularity), self
  excluded, `limit` applied, `limit` 0 and 31 → 400, unknown card → 404, and the four new fields
  present in card responses.
- Postgres integration test (Testcontainers, runs when Docker is available): the SQL ranking returns
  the same order as the Kotlin rules for seeded cards, and the route works without a bearer token.

App:
- Unit tests for `compareCards`: each row's better/worse/equal/not-comparable cases, `*` and missing
  values, popularity direction, colors equal versus unique, keyword-line parsing (including the
  "creatures you control have flying" false positive and `Ward {2}`), and the tally.
- `CardRepositoryTest` (MockWebServer): `getSimilarCards` success, caching, and 404.
- One instrumented Compose test that the comparison content shows the verdict words and unique traits.

## Development and verification

- Backend tests: `cd backend && ./gradlew test` (the Postgres test needs Docker running).
- Run the backend against Supabase from the repo root:
  `set -a && . ./.env && set +a && cd backend && ./gradlew bootRun`.
- Point the app at the local backend while Render lacks the route:
  `cd frontend && ./gradlew :app:installDebug -PbackendUrl=http://10.0.2.2:8080`.
- App tests: `cd frontend && ./gradlew :app:testDebugUnitTest` and, with the emulator running,
  `./gradlew :app:connectedDebugAndroidTest`.
- Render serves the feature only after the next deploy of `main` (see `docs/CI-RENDER-DEPLOYMENT.md`).
