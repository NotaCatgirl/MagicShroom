# Card Comparison Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** From a card's detail screen, "Compare with…" lists the most similar cards; picking one shows both cards with every stat marked better, worse, equal, or unique.

**Architecture:** The backend returns four more card stats and adds `GET /api/v1/cards/{cardId}/similar`, ranked by one set of rules written once in Kotlin (`SimilarCardRanking`, used by the mock profile) and once in SQL (Postgres), with a shared fixture proving they agree. The Android app adds the fields, a repository call, a pure-Kotlin `compareCards` function, and two Compose screens that reuse `SearchCardTile`, the card cache, and a back bar extracted from the detail screen.

**Tech Stack:** Kotlin, Spring Boot 4 (JDBC, MockMvc, Testcontainers/RestAssured), PostgreSQL, Android Jetpack Compose (Material 3, Navigation, Coil 3), Retrofit + kotlinx.serialization, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-10-20-card-comparison-design.md`

**Conventions to keep:** Backend files use tabs except `PostgresRepositories.kt`, `SupabaseSecurity.kt`, and `SupabasePostgresAuthIntegrationTest.kt` (4 spaces). Frontend files use 4 spaces. Every public class and function gets a one-line KDoc, as the surrounding code does. Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

## File map

Backend (`backend/src/...`):
- Modify `main/kotlin/edu/csumb/magicshroom/api/model/Card.kt`: four stat fields.
- Modify `main/kotlin/edu/csumb/magicshroom/api/repository/PostgresRepositories.kt`: select/map the stats; SQL `findSimilar`.
- Create `main/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardRanking.kt`: the ranking rules.
- Modify `main/kotlin/edu/csumb/magicshroom/api/repository/CardRepository.kt`: `findSimilar` default.
- Modify `main/kotlin/edu/csumb/magicshroom/api/service/CardService.kt`: `similar`.
- Modify `main/kotlin/edu/csumb/magicshroom/api/controller/CardController.kt`: the route.
- Modify `main/kotlin/edu/csumb/magicshroom/api/auth/SupabaseSecurity.kt`: make the route public.
- Modify `test/resources/mock-data/cards.json`: real stats for the 22 mock cards.
- Create `test/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardFixtures.kt` and `SimilarCardRankingTest.kt`.
- Modify `test/kotlin/edu/csumb/magicshroom/api/controller/ApiRoutesTest.kt` and `test/kotlin/edu/csumb/magicshroom/api/auth/SupabasePostgresAuthIntegrationTest.kt`.

Docs: modify `docs/openapi.yaml`, `README.md`, `frontend/README.md`, `backend/README.md`.

Frontend (`frontend/app/src/...`, package path `edu/csumb/magicshroom`):
- Modify `main/java/.../data/api/CardDtos.kt`, `data/api/MagicShroomApi.kt`, `data/repository/CardRepository.kt`.
- Create `main/java/.../ui/compare/CardComparison.kt`: verdict rules (no Android imports).
- Create `main/java/.../ui/common/BackTopBar.kt`: back bar shared by card screens.
- Modify `main/java/.../ui/detail/CardDetailScreen.kt`: use `BackTopBar`, add the button.
- Modify `main/java/.../ui/search/SearchScreen.kt`: `SearchCardTile` becomes `internal`.
- Create `main/java/.../ui/compare/SimilarCardsScreen.kt` and `ui/compare/CardComparisonScreen.kt`.
- Modify `main/java/.../ui/nav/AppNavHost.kt`: two routes.
- Create `test/java/.../ui/compare/CardComparisonTest.kt`; modify `test/java/.../data/repository/CardRepositoryTest.kt`.
- Create `androidTest/java/edu/csumb/magicshroom/CardComparisonUiTest.kt`.

---

### Task 1: Card stats in API responses

**Files:**
- Modify: `backend/src/test/resources/mock-data/cards.json`
- Modify: `backend/src/test/kotlin/edu/csumb/magicshroom/api/controller/ApiRoutesTest.kt`
- Modify: `backend/src/main/kotlin/edu/csumb/magicshroom/api/model/Card.kt`
- Modify: `backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/PostgresRepositories.kt:22,40-41`

- [ ] **Step 1: Add the real stats to the mock fixtures**

The file has a compact hand-made layout, so insert text after each `"cardName": "…",` instead of re-serializing. Values come from the Supabase `cards` table.

```bash
cd backend && python3 - <<'EOF'
import pathlib
path = pathlib.Path("src/test/resources/mock-data/cards.json")
stats = {  # name: (power, toughness, edhrecRank)
    "Lightning Bolt": (None, None, 576), "Counterspell": (None, None, 17),
    "Llanowar Elves": ("1", "1", 65), "Serra Angel": ("4", "4", 4698),
    "Dark Ritual": (None, None, 227), "Giant Growth": (None, None, 4537),
    "Wrath of God": (None, None, 117), "Swords to Plowshares": (None, None, 13),
    "Shivan Dragon": ("5", "5", 5359), "Sol Ring": (None, None, 1),
    "Birds of Paradise": ("0", "1", 76), "Goblin Guide": ("2", "2", 6808),
    "Brainstorm": (None, None, 29), "Doom Blade": (None, None, 573),
    "Elvish Mystic": ("1", "1", 97), "Lava Spike": (None, None, 6601),
    "Smash to Smithereens": (None, None, 4445), "Lightning Helix": (None, None, 2953),
    "Thoughtseize": (None, None, 2109), "Tarmogoyf": ("*", "1+*", 8100),
    "Ornithopter": ("0", "2", 1252),
}
text = path.read_text(encoding="utf-8")
for name, (power, toughness, rank) in stats.items():
    extra = f' "power": "{power}", "toughness": "{toughness}",' if power is not None else ""
    extra += f' "edhrecRank": {rank},'
    marker = f'"cardName": "{name}",'
    assert text.count(marker) == 1, name
    text = text.replace(marker, marker + extra)
path.write_text(text, encoding="utf-8")
EOF
python3 -c 'import json; c=json.load(open("src/test/resources/mock-data/cards.json")); print(sum("edhrecRank" in x for x in c), "ranked,", sum("power" in x for x in c), "with power")'
```

Expected output: `21 ranked, 8 with power` (Forest stays unranked).

- [ ] **Step 2: Write the failing route test**

In `ApiRoutesTest.kt`, insert after the test `card detail contains home-screen fields and structured metadata` (it ends at the closing brace before `/** Returns unique full card records in request order from the public batch route. */`):

```kotlin
	/** Returns printed stats and popularity, keeping values such as `*` as text. */
	@Test
	fun `card responses include printed stats and popularity`() {
		mockMvc.get("/api/v1/cards/4").andExpect {
			status { isOk() }
			jsonPath("$.power") { value("4") }
			jsonPath("$.toughness") { value("4") }
			jsonPath("$.edhrecRank") { value(4698) }
		}
		mockMvc.get("/api/v1/cards/20").andExpect {
			status { isOk() }
			jsonPath("$.power") { value("*") }
			jsonPath("$.toughness") { value("1+*") }
		}
	}
```

- [ ] **Step 3: Run it to verify it fails**

Run: `cd backend && ./gradlew test --tests 'edu.csumb.magicshroom.api.controller.ApiRoutesTest'`
Expected: FAIL in `card responses include printed stats and popularity` with `No value at JSON path "$.power"` (if Jackson rejects the unknown fixture keys instead, every test in the class fails to load; either way the new fields are missing).

- [ ] **Step 4: Add the fields to `Card`**

In `Card.kt`, replace:

```kotlin
	val imageFaces: List<CardImageFace> = emptyList(),
) {
```

with:

```kotlin
	val imageFaces: List<CardImageFace> = emptyList(),
	/** Printed power, as text because of values such as `*`; null for cards without one. Read-only. */
	val power: String? = null,
	/** Printed toughness, as text like [power]. Read-only. */
	val toughness: String? = null,
	/** A planeswalker's starting loyalty, as text. Read-only. */
	val loyalty: String? = null,
	/** EDHREC popularity rank; lower is more popular, null if unranked. Read-only. */
	val edhrecRank: Int? = null,
) {
```

- [ ] **Step 5: Read the stats in `PostgresCardRepository`**

In `PostgresRepositories.kt`, in the `columns` string, replace the ending

```kotlin
(select faces from card_images where oracle_id = cards.scryfall_oracle_id) as image_faces"
```

with

```kotlin
(select faces from card_images where oracle_id = cards.scryfall_oracle_id) as image_faces, power, toughness, loyalty, edhrec_rank"
```

and in `mapper`, replace

```kotlin
            imageFaces = rs.getString("image_faces")?.let { json.readValue(it) } ?: emptyList(),
        )
```

with

```kotlin
            imageFaces = rs.getString("image_faces")?.let { json.readValue(it) } ?: emptyList(),
            power = rs.getString("power"), toughness = rs.getString("toughness"), loyalty = rs.getString("loyalty"),
            edhrecRank = rs.getInt("edhrec_rank").takeUnless { rs.wasNull() },
        )
```

Do not touch `fields`, `values`, or `bind`: admin writes keep leaving these columns alone.

- [ ] **Step 6: Run the backend tests**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (the Postgres integration test runs when Docker is up and exercises the new select list).

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/edu/csumb/magicshroom/api/model/Card.kt \
  backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/PostgresRepositories.kt \
  backend/src/test/resources/mock-data/cards.json \
  backend/src/test/kotlin/edu/csumb/magicshroom/api/controller/ApiRoutesTest.kt
git commit -m "feat(api): return power, toughness, loyalty, and EDHREC rank on cards

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Similar-card ranking rules

**Files:**
- Create: `backend/src/test/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardFixtures.kt`
- Create: `backend/src/test/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardRankingTest.kt`
- Create: `backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardRanking.kt`
- Modify: `backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/CardRepository.kt`

- [ ] **Step 1: Write the shared fixture**

`SimilarCardFixtures.kt` (each card is designed to test one rule; the list order is also insertion order in Task 4, so ids ascend the same way):

```kotlin
package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.Color
import java.util.UUID

/** Cards that exercise every similar-card rule; shared by the Kotlin and PostgreSQL ranking tests. */
object SimilarCardFixtures {
	/** The card the others are ranked against. */
	const val BASE = "Base Elf"

	val cards: List<Card> = listOf(
		card(1, BASE, "G", "Creature", listOf("Elf", "Druid"), 2.0),
		card(2, "Twin Elf", "G", "Creature", listOf("Elf", "Druid"), 2.0, rank = 500),
		card(3, "Elf Warrior", "G", "Creature", listOf("Elf", "Warrior"), 2.0, rank = 10),
		card(4, "Popular Bear", "G", "Creature", listOf("Bear"), 2.0, rank = 20),
		card(5, "Unranked Wolf", "G", "Creature", listOf("Wolf"), 2.0),
		card(6, "Second Wolf", "G", "Creature", listOf("Wolf"), 2.0),
		card(7, "Pricier Beast", "G", "Creature", emptyList(), 3.0, rank = 1),
		card(8, "Free Ooze", "G", "Creature", emptyList(), null, rank = 1),
		card(9, "Selesnya Elf", "GW", "Creature", listOf("Elf", "Druid"), 2.0, rank = 1),
		card(10, "Red Elf", "R", "Creature", listOf("Elf"), 2.0, rank = 1),
		card(11, "Green Instant", "G", "Instant", emptyList(), 2.0, rank = 1),
	)

	/**
	 * Same colors first (Selesnya Elf, sharing only green, is last); then mana value distance (a missing
	 * mana value counts as 0); then shared subtypes; then popularity, unranked last; then id. Red Elf
	 * shares no color and Green Instant shares no type, so neither appears.
	 */
	val expectedOrder = listOf(
		"Twin Elf", "Elf Warrior", "Popular Bear", "Unranked Wolf", "Second Wolf",
		"Pricier Beast", "Free Ooze", "Selesnya Elf",
	)

	private fun card(id: Long, name: String, colors: String, type: String, subtypes: List<String>, manaValue: Double?, rank: Int? = null): Card {
		val identity = colors.map { Color.valueOf(it.toString()) }
		return Card(
			id = id, uuid = UUID.nameUUIDFromBytes(name.toByteArray()), cardName = name,
			colorIdentity = identity, colors = identity, convertedManaCost = manaValue,
			types = listOf(type), subtypes = subtypes, edhrecRank = rank,
		)
	}
}
```

- [ ] **Step 2: Write the failing ranking tests**

`SimilarCardRankingTest.kt`:

```kotlin
package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Pins the similar-card order that both repository implementations must return. */
class SimilarCardRankingTest {
	private val base = SimilarCardFixtures.cards.first { it.cardName == SimilarCardFixtures.BASE }

	@Test
	fun `ranks by colors, mana value, shared subtypes, popularity, then id`() {
		val ranked = SimilarCardRanking.rank(base, SimilarCardFixtures.cards, 30)
		assertEquals(SimilarCardFixtures.expectedOrder, ranked.map(Card::cardName))
	}

	@Test
	fun `returns at most limit cards`() {
		val ranked = SimilarCardRanking.rank(base, SimilarCardFixtures.cards, 3)
		assertEquals(SimilarCardFixtures.expectedOrder.take(3), ranked.map(Card::cardName))
	}

	@Test
	fun `colorless cards match only other colorless cards`() {
		val rock = Card(
			id = 1, uuid = UUID.randomUUID(), cardName = "Colorless Rock", colorIdentity = emptyList(),
			colors = emptyList(), convertedManaCost = 1.0, types = listOf("Artifact"),
		)
		val mox = rock.copy(id = 2, uuid = UUID.randomUUID(), cardName = "Colorless Mox", convertedManaCost = 0.0)
		val relic = rock.copy(id = 3, uuid = UUID.randomUUID(), cardName = "Green Relic", colorIdentity = listOf(Color.G), colors = listOf(Color.G))

		assertEquals(listOf("Colorless Mox"), SimilarCardRanking.rank(rock, listOf(rock, mox, relic), 10).map(Card::cardName))
	}
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `cd backend && ./gradlew test --tests 'edu.csumb.magicshroom.api.repository.SimilarCardRankingTest'`
Expected: FAIL to compile with `Unresolved reference 'SimilarCardRanking'`.

- [ ] **Step 4: Implement the rules**

`SimilarCardRanking.kt`:

```kotlin
package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Card
import kotlin.math.abs

/**
 * The order `GET /api/v1/cards/{cardId}/similar` returns. `PostgresCardRepository.findSimilar` applies
 * the same rules in SQL; the shared `SimilarCardFixtures` test data keeps the two in step.
 */
object SimilarCardRanking {
	/**
	 * Up to [limit] of [candidates] that share a main type with [base], best match first: the same color
	 * identity before sharing only some colors, then closest mana value (missing counts as 0), most
	 * shared subtypes, most popular (lowest EDHREC rank, unranked last), and lowest id.
	 */
	fun rank(base: Card, candidates: Collection<Card>, limit: Int): List<Card> {
		val baseColors = base.colorIdentity.toSet()
		val baseSubtypes = base.subtypes.toSet()
		val baseManaValue = base.convertedManaCost ?: 0.0

		// 0 for the same color identity, 1 for at least one shared color, null when none is shared.
		fun tier(card: Card): Int? = when {
			card.colorIdentity.toSet() == baseColors -> 0
			card.colorIdentity.any(baseColors::contains) -> 1
			else -> null
		}

		return candidates
			.filter { it.id != base.id && it.types.any(base.types::contains) && tier(it) != null }
			.sortedWith(
				compareBy<Card> { tier(it) }
					.thenBy { abs((it.convertedManaCost ?: 0.0) - baseManaValue) }
					.thenByDescending { (it.subtypes.toSet() intersect baseSubtypes).size }
					.thenBy(nullsLast<Int>()) { it.edhrecRank }
					.thenBy { it.id },
			)
			.take(limit)
	}
}
```

- [ ] **Step 5: Run the ranking tests**

Run: `cd backend && ./gradlew test --tests 'edu.csumb.magicshroom.api.repository.SimilarCardRankingTest'`
Expected: PASS (3 tests).

- [ ] **Step 6: Give `CardRepository` a default `findSimilar`**

In `CardRepository.kt`, insert after the `findByIds` line:

```kotlin

	/** Up to [limit] cards most like [card], best first, by [SimilarCardRanking]'s rules. */
	fun findSimilar(card: Card, limit: Int): List<Card> = SimilarCardRanking.rank(card, findAll(), limit)
```

This default serves the mock profile (`JsonCardRepository`), the same way `search` does today.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardRanking.kt \
  backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/CardRepository.kt \
  backend/src/test/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardFixtures.kt \
  backend/src/test/kotlin/edu/csumb/magicshroom/api/repository/SimilarCardRankingTest.kt
git commit -m "feat(api): rank similar cards by colors, mana value, subtypes, and popularity

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: `GET /api/v1/cards/{cardId}/similar`

**Files:**
- Modify: `backend/src/test/kotlin/edu/csumb/magicshroom/api/controller/ApiRoutesTest.kt`
- Modify: `backend/src/main/kotlin/edu/csumb/magicshroom/api/service/CardService.kt`
- Modify: `backend/src/main/kotlin/edu/csumb/magicshroom/api/controller/CardController.kt`
- Modify: `backend/src/main/kotlin/edu/csumb/magicshroom/api/auth/SupabaseSecurity.kt:62-70`

- [ ] **Step 1: Write the failing route tests**

The expected orders follow from the mock data: Llanowar Elves (3) matches the green creatures Elvish Mystic (shares Elf and Druid), Birds of Paradise (same mana value), and Tarmogoyf (mana value 2). Lightning Bolt (1) matches the red instant Smash to Smithereens, then Lightning Helix, which only shares red. Serra Angel (4) is the only white creature.

In `ApiRoutesTest.kt`, insert after the test `batch cards validates ids and reports missing cards`:

```kotlin
	/** Ranks same-color cards of the same type first, then closest mana value and shared creature types. */
	@Test
	fun `similar cards rank same colors and shared creature types first`() {
		mockMvc.get("/api/v1/cards/3/similar").andExpect {
			status { isOk() }
			jsonPath("$.length()") { value(3) }
			jsonPath("$[0].cardName") { value("Elvish Mystic") }
			jsonPath("$[1].cardName") { value("Birds of Paradise") }
			jsonPath("$[2].cardName") { value("Tarmogoyf") }
		}
	}

	/** Lists cards sharing only some colors after every same-color match. */
	@Test
	fun `similar cards widen to shared colors after same colors`() {
		mockMvc.get("/api/v1/cards/1/similar").andExpect {
			status { isOk() }
			jsonPath("$.length()") { value(2) }
			jsonPath("$[0].cardName") { value("Smash to Smithereens") }
			jsonPath("$[1].cardName") { value("Lightning Helix") }
		}
	}

	/** Returns an empty list, rather than an error, when nothing is similar. */
	@Test
	fun `a card with no similar cards returns an empty list`() {
		mockMvc.get("/api/v1/cards/4/similar").andExpect {
			status { isOk() }
			jsonPath("$.length()") { value(0) }
		}
	}

	/** Applies limit, rejects limits outside 1-30, and reports an unknown card. */
	@Test
	fun `similar cards honor and validate the limit`() {
		mockMvc.get("/api/v1/cards/3/similar?limit=1").andExpect {
			status { isOk() }
			jsonPath("$.length()") { value(1) }
			jsonPath("$[0].cardName") { value("Elvish Mystic") }
		}
		mockMvc.get("/api/v1/cards/3/similar?limit=0").andExpect {
			status { isBadRequest() }
			jsonPath("$.detail") { value("limit must be greater than or equal to 1") }
		}
		mockMvc.get("/api/v1/cards/3/similar?limit=31").andExpect {
			status { isBadRequest() }
			jsonPath("$.detail") { value("limit must be less than or equal to 30") }
		}
		mockMvc.get("/api/v1/cards/999/similar").andExpect {
			status { isNotFound() }
			jsonPath("$.detail") { value("Card 999 was not found.") }
		}
	}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd backend && ./gradlew test --tests 'edu.csumb.magicshroom.api.controller.ApiRoutesTest'`
Expected: the four new tests FAIL with status 404 (no such route yet); the others pass.

- [ ] **Step 3: Add the service method**

In `CardService.kt`, insert after the closing brace of `getBatch`:

```kotlin

	/** Up to [limit] cards most like card [cardId], best first; 404 if the card does not exist. */
	fun similar(cardId: Long, limit: Int): List<Card> = cardRepository.findSimilar(get(cardId), limit)
```

- [ ] **Step 4: Add the route**

In `CardController.kt`, insert after the `batch` function:

```kotlin

	/** Up to [limit] cards most like this one, best first. Public. */
	@GetMapping("/{cardId}/similar")
	fun similar(
		@PathVariable @Min(1) cardId: Long,
		@RequestParam(defaultValue = "10") @Min(1) @Max(30) limit: Int,
	): List<Card> = cardService.similar(cardId, limit)
```

- [ ] **Step 5: Make the route public in production**

`/api/v1/cards/*` matches one path segment only. In `SupabaseSecurity.kt`, replace

```kotlin
                    "/api/v1/cards/*",
```

with

```kotlin
                    "/api/v1/cards/*",
                    "/api/v1/cards/*/similar",
```

- [ ] **Step 6: Run the tests**

Run: `cd backend && ./gradlew test --tests 'edu.csumb.magicshroom.api.controller.ApiRoutesTest'`
Expected: PASS. If a `limit` detail message differs only in wording, use the actual text the handler produces for the existing `size` check (`size must be greater than or equal to 1`) as the guide; do not change the handler.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/kotlin/edu/csumb/magicshroom/api/service/CardService.kt \
  backend/src/main/kotlin/edu/csumb/magicshroom/api/controller/CardController.kt \
  backend/src/main/kotlin/edu/csumb/magicshroom/api/auth/SupabaseSecurity.kt \
  backend/src/test/kotlin/edu/csumb/magicshroom/api/controller/ApiRoutesTest.kt
git commit -m "feat(api): add public GET /api/v1/cards/{cardId}/similar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Rank in PostgreSQL

**Files:**
- Modify: `backend/src/test/kotlin/edu/csumb/magicshroom/api/auth/SupabasePostgresAuthIntegrationTest.kt`
- Modify: `backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/PostgresRepositories.kt`

- [ ] **Step 1: Write the integration test**

In `SupabasePostgresAuthIntegrationTest.kt`, add the import (keep imports sorted):

```kotlin
import edu.csumb.magicshroom.api.repository.SimilarCardFixtures
```

and insert this test after `admin deletion cascades postgres data and stale token cannot recreate the user` (before `/** Builds an RS256 JWT …`):

```kotlin
    /** Ranks similar cards in PostgreSQL exactly like the Kotlin rules, on a route that needs no token. */
    @Test
    fun `postgres similar cards match the kotlin ranking without a token`() {
        val ids = SimilarCardFixtures.cards.map { card ->
            jdbc.queryForObject(
                """insert into cards (uuid, card_name, color_identity, colors, converted_mana_cost, types, subtypes, edhrec_rank)
                    values (?, ?, ?::text[], ?::text[], ?, ?::text[], ?::text[], ?) returning id""",
                Long::class.java,
                UUID.randomUUID(), card.cardName, textArray(card.colorIdentity.map { it.name }),
                textArray(card.colors.map { it.name }), card.convertedManaCost, textArray(card.types),
                textArray(card.subtypes), card.edhrecRank,
            )!!
        }
        val baseId = ids[SimilarCardFixtures.cards.indexOfFirst { it.cardName == SimilarCardFixtures.BASE }]
        try {
            val response = given().port(port).queryParam("limit", 30).get("/api/v1/cards/$baseId/similar")
            assertEquals(200, response.statusCode())
            assertEquals(SimilarCardFixtures.expectedOrder, response.jsonPath().getList<String>("cardName"))
        } finally {
            jdbc.update("delete from cards where id in (${ids.joinToString()})")
        }
    }
```

and this helper after the `token` function:

```kotlin
    /** Formats a PostgreSQL text-array literal for values without commas or quotes. */
    private fun textArray(values: List<String>): String = values.joinToString(",", prefix = "{", postfix = "}")
```

- [ ] **Step 2: Run it (Docker must be running)**

Run: `cd backend && ./gradlew test --tests 'edu.csumb.magicshroom.api.auth.SupabasePostgresAuthIntegrationTest'`
Expected: PASS. The default Kotlin `findSimilar` (which loads `findAll()`) already answers; this test pins the order and the no-token access that the SQL version must keep. If it is SKIPPED, Docker is not reachable: start it and rerun, because Step 4 depends on this test.

- [ ] **Step 3: Override `findSimilar` with one SQL query**

In `PostgresRepositories.kt`, inside `PostgresCardRepository`, insert after `findByIds`:

```kotlin

    /**
     * Ranks similar cards in PostgreSQL with [SimilarCardRanking]'s rules, so a request never loads the
     * whole catalog. `cards` must stay unaliased because [columns] refers to `cards.scryfall_oracle_id`.
     */
    override fun findSimilar(card: Card, limit: Int): List<Card> = jdbc.query(
        """
        with base as (
            select id as base_id, color_identity as base_colors, types as base_types, subtypes as base_subtypes,
                coalesce(converted_mana_cost, 0) as base_mana_value
            from cards where id = ?
        )
        select $columns from cards cross join base
        where id <> base_id and types && base_types
            and ((color_identity @> base_colors and color_identity <@ base_colors) or color_identity && base_colors)
        order by case when color_identity @> base_colors and color_identity <@ base_colors then 0 else 1 end,
            abs(coalesce(converted_mana_cost, 0) - base_mana_value),
            cardinality(array(select unnest(subtypes) intersect select unnest(base_subtypes))) desc,
            edhrec_rank asc nulls last,
            id
        limit ?
        """.trimIndent(),
        mapper, card.id, limit,
    )
```

Notes: `@>` plus `<@` is set equality, so two colorless cards match and a colorless card matches nothing colored; `&&` means "shares at least one".

- [ ] **Step 4: Run the integration test and the whole suite**

Run: `cd backend && ./gradlew test`
Expected: BUILD SUCCESSFUL. `postgres similar cards match the kotlin ranking without a token` still passes, now through the SQL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/edu/csumb/magicshroom/api/repository/PostgresRepositories.kt \
  backend/src/test/kotlin/edu/csumb/magicshroom/api/auth/SupabasePostgresAuthIntegrationTest.kt
git commit -m "perf(api): rank similar cards in one PostgreSQL query

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: API contract

**Files:**
- Modify: `docs/openapi.yaml`

- [ ] **Step 1: Document the four fields**

In the `Card` schema, replace

```yaml
        imageFaces:
          type: array
          readOnly: true
          description: Full-card scans from one representative Scryfall printing; empty if unavailable.
          items:
            $ref: '#/components/schemas/CardImageFace'
      allOf:
        - $ref: '#/components/schemas/CardInput'
```

with

```yaml
        imageFaces:
          type: array
          readOnly: true
          description: Full-card scans from one representative Scryfall printing; empty if unavailable.
          items:
            $ref: '#/components/schemas/CardImageFace'
        power:
          type: string
          nullable: true
          readOnly: true
          description: Printed power; text because of values such as `*`. Null for cards without one.
          example: '4'
        toughness:
          type: string
          nullable: true
          readOnly: true
          description: Printed toughness; text like power.
          example: '4'
        loyalty:
          type: string
          nullable: true
          readOnly: true
          description: A planeswalker's starting loyalty.
          example: '3'
        edhrecRank:
          type: integer
          nullable: true
          readOnly: true
          description: EDHREC popularity rank; lower is more popular. Null if unranked.
          example: 4698
      allOf:
        - $ref: '#/components/schemas/CardInput'
```

- [ ] **Step 2: Document the route**

Insert before the line `  /api/v1/decks:` (after the batch route's `'500'` response):

```yaml
  /api/v1/cards/{cardId}/similar:
    parameters:
      - $ref: '#/components/parameters/CardId'
    get:
      tags: [Cards]
      operationId: getSimilarCards
      summary: List cards similar to a card
      description: >-
        Cards that share a main type with the card, best match first. Cards with the same color
        identity come first, then cards sharing at least one color; within each, the closest mana
        value, the most shared subtypes, and the most popular (lowest EDHREC rank) come first.
      security: []
      parameters:
        - name: limit
          in: query
          description: Maximum number of cards to return
          schema:
            type: integer
            minimum: 1
            maximum: 30
            default: 10
      responses:
        '200':
          description: Similar cards, best match first; empty if there are none
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Card'
        '400':
          $ref: '#/components/responses/BadRequest'
        '404':
          $ref: '#/components/responses/NotFound'
        '500':
          $ref: '#/components/responses/InternalError'

```

- [ ] **Step 3: Validate the YAML**

Run: `python3 -c 'import yaml; d=yaml.safe_load(open("docs/openapi.yaml")); print(sorted(d["paths"]["/api/v1/cards/{cardId}/similar"]["get"]["responses"]), sorted(d["components"]["schemas"]["Card"]["properties"]))'`
Expected: `['200', '400', '404', '500'] ['edhrecRank', 'id', 'imageFaces', 'loyalty', 'power', 'toughness']`. If `npx` is available, also run `npx @redocly/cli lint docs/openapi.yaml` and expect no new errors.

- [ ] **Step 4: Commit**

```bash
git add docs/openapi.yaml
git commit -m "docs(api): document similar cards route and card stats

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: App data layer

**Files:**
- Modify: `frontend/app/src/test/java/edu/csumb/magicshroom/data/repository/CardRepositoryTest.kt`
- Modify: `frontend/app/src/main/java/edu/csumb/magicshroom/data/api/CardDtos.kt`
- Modify: `frontend/app/src/main/java/edu/csumb/magicshroom/data/api/MagicShroomApi.kt`
- Modify: `frontend/app/src/main/java/edu/csumb/magicshroom/data/repository/CardRepository.kt`

- [ ] **Step 1: Write the failing repository tests**

In `CardRepositoryTest.kt`, insert after the test `getCardsByIds chunks more than one hundred uncached ids`:

```kotlin
    /** Requests similar cards and caches them so the comparison screen needs no second request. */
    @Test
    fun `getSimilarCards sends the limit and caches the cards`() = runTest {
        server.enqueue(json(200, "[$CARD_TWO]"))

        val result = repository.getSimilarCards(1, limit = 5) as ApiResult.Success

        assertEquals("/api/v1/cards/1/similar?limit=5", server.takeRequest().path)
        assertEquals(listOf("Counterspell"), result.data.map { it.cardName })
        assertEquals("Counterspell", (repository.getCard(2) as ApiResult.Success).data.cardName)
        assertEquals(1, server.requestCount)
    }

    /** Reads stats when the server sends them and tolerates their absence from older servers. */
    @Test
    fun `card stats are parsed when present and optional otherwise`() = runTest {
        server.enqueue(json(200, """[{"id":4,"uuid":"u4","cardName":"Serra Angel","power":"4","toughness":"4","loyalty":null,"edhrecRank":4698},$CARD_TWO]"""))

        val cards = (repository.getSimilarCards(1) as ApiResult.Success).data

        assertEquals("/api/v1/cards/1/similar?limit=10", server.takeRequest().path)
        assertEquals("4", cards[0].power)
        assertEquals("4", cards[0].toughness)
        assertEquals(4698, cards[0].edhrecRank)
        assertEquals(null, cards[1].power)
        assertEquals(null, cards[1].edhrecRank)
    }

    /** A server older than the similar route answers 404, which the screen explains. */
    @Test
    fun `getSimilarCards reports a missing route as a 404`() = runTest {
        server.enqueue(json(404, """{"title":"Not Found","status":404,"detail":"No static resource api/v1/cards/1/similar."}"""))

        val result = repository.getSimilarCards(1)

        assertEquals(404, (result as ApiResult.HttpError).status)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest --tests 'edu.csumb.magicshroom.data.repository.CardRepositoryTest'`
Expected: FAIL to compile with `Unresolved reference 'getSimilarCards'`.

- [ ] **Step 3: Add the DTO fields**

In `CardDtos.kt`, replace

```kotlin
    val imageFaces: List<CardImageFaceDto> = emptyList(),
)
```

with

```kotlin
    val imageFaces: List<CardImageFaceDto> = emptyList(),
    /** Printed power, as text because of values such as `*`; null if the card has none or the server is older. */
    val power: String? = null,
    /** Printed toughness, as text like [power]. */
    val toughness: String? = null,
    /** A planeswalker's starting loyalty, as text. */
    val loyalty: String? = null,
    /** EDHREC popularity rank; lower is more popular, null if unranked. */
    val edhrecRank: Int? = null,
)
```

- [ ] **Step 4: Add the API call**

In `MagicShroomApi.kt`, insert after `getCardsBatch`:

```kotlin

    /** Up to [limit] cards most like card [cardId], best first. Public. */
    @GET("api/v1/cards/{cardId}/similar")
    suspend fun getSimilarCards(@Path("cardId") cardId: Long, @Query("limit") limit: Int? = null): List<CardDto>
```

- [ ] **Step 5: Add the repository method**

In `CardRepository.kt`, insert after `getCardsByIds` (before `/** Stores full card records …`):

```kotlin
    /** Up to [limit] cards most like card [cardId], best first; cached so the comparison opens instantly. */
    suspend fun getSimilarCards(cardId: Long, limit: Int = 10): ApiResult<List<CardDto>> =
        apiCall { api.getSimilarCards(cardId, limit) }.also { result ->
            if (result is ApiResult.Success) remember(result.data)
        }

```

- [ ] **Step 6: Run the tests**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest --tests 'edu.csumb.magicshroom.data.repository.CardRepositoryTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add frontend/app/src/main/java/edu/csumb/magicshroom/data/api/CardDtos.kt \
  frontend/app/src/main/java/edu/csumb/magicshroom/data/api/MagicShroomApi.kt \
  frontend/app/src/main/java/edu/csumb/magicshroom/data/repository/CardRepository.kt \
  frontend/app/src/test/java/edu/csumb/magicshroom/data/repository/CardRepositoryTest.kt
git commit -m "feat(app): load card stats and similar cards

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Comparison rules

**Files:**
- Create: `frontend/app/src/test/java/edu/csumb/magicshroom/ui/compare/CardComparisonTest.kt`
- Create: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/compare/CardComparison.kt`

- [ ] **Step 1: Write the failing tests**

`CardComparisonTest.kt`:

```kotlin
package edu.csumb.magicshroom.ui.compare

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verdicts are from the base card's point of view, per the card comparison spec. */
class CardComparisonTest {
    private val llanowar = card(1, "Llanowar Elves", subtypes = listOf("Elf", "Druid"), power = "1", toughness = "1", rank = 65)
    private val elvish = card(2, "Elvish Mystic", subtypes = listOf("Elf", "Druid"), power = "1", toughness = "1", rank = 97)
    private val serra = card(
        3, "Serra Angel", colors = listOf(Color.W), subtypes = listOf("Angel"), manaValue = 5.0,
        power = "4", toughness = "4", rank = 4698, text = "Flying, vigilance",
    )

    @Test
    fun `cheaper mana value and better popularity win, smaller body loses`() {
        val comparison = compareCards(llanowar, serra)

        assertEquals(
            listOf(
                StatRow("Mana value", "1", "5", Verdict.BETTER),
                StatRow("Power", "1", "4", Verdict.WORSE),
                StatRow("Toughness", "1", "4", Verdict.WORSE),
                StatRow("Popularity", "#65", "#4698", Verdict.BETTER),
            ),
            comparison.rows,
        )
        assertEquals(listOf("Creature"), comparison.shared)
        assertEquals(listOf("Elf", "Druid", "Green"), comparison.uniqueToBase)
        assertEquals(listOf("Flying", "Vigilance", "Angel", "White"), comparison.uniqueToOther)
        assertEquals(2, comparison.betterCount)
        assertEquals(2, comparison.worseCount)
        assertEquals(0, comparison.equalCount)
        assertEquals(7, comparison.uniqueCount)
    }

    @Test
    fun `same colors add an equal colors row and no color traits`() {
        val comparison = compareCards(llanowar, elvish)

        assertEquals(
            listOf(
                StatRow("Mana value", "1", "1", Verdict.EQUAL),
                StatRow("Power", "1", "1", Verdict.EQUAL),
                StatRow("Toughness", "1", "1", Verdict.EQUAL),
                StatRow("Popularity", "#65", "#97", Verdict.BETTER),
                StatRow("Colors", "G", "G", Verdict.EQUAL),
            ),
            comparison.rows,
        )
        assertEquals(listOf("Elf", "Druid", "Creature"), comparison.shared)
        assertEquals(emptyList<String>(), comparison.uniqueToBase)
        assertEquals(emptyList<String>(), comparison.uniqueToOther)
        assertEquals(4, comparison.equalCount)
        assertEquals(0, comparison.uniqueCount)
    }

    @Test
    fun `star, missing, and unranked values are shown but not compared`() {
        val goyf = card(4, "Tarmogoyf", subtypes = listOf("Lhurgoyf"), manaValue = null, power = "*", toughness = "1+*")

        val rows = compareCards(goyf, llanowar).rows

        assertEquals(StatRow("Mana value", "—", "1", Verdict.NOT_COMPARABLE), rows[0])
        assertEquals(StatRow("Power", "*", "1", Verdict.NOT_COMPARABLE), rows[1])
        assertEquals(StatRow("Toughness", "1+*", "1", Verdict.NOT_COMPARABLE), rows[2])
        assertEquals(StatRow("Popularity", "Unranked", "#65", Verdict.NOT_COMPARABLE), rows[3])
        assertEquals(0, compareCards(goyf, llanowar).betterCount)
    }

    @Test
    fun `loyalty compares higher as better and absent stats have no row`() {
        val jace = card(5, "Jace Beleren", colors = listOf(Color.U), types = listOf("Planeswalker"), manaValue = 3.0, loyalty = "3")
        val tamiyo = card(6, "Tamiyo", colors = listOf(Color.U), types = listOf("Planeswalker"), manaValue = 4.0, loyalty = "4.5")

        val rows = compareCards(jace, tamiyo).rows

        assertEquals(listOf("Mana value", "Loyalty", "Colors"), rows.map(StatRow::label))
        assertEquals(StatRow("Loyalty", "3", "4.5", Verdict.WORSE), rows[1])
    }

    @Test
    fun `keywords come only from lines that list keywords`() {
        assertEquals(listOf("Flying", "Vigilance"), keywords("Flying, vigilance"))
        assertEquals(listOf("Flying", "Vigilance"), keywords("Flying\nVigilance"))
        assertEquals(listOf("First strike", "Lifelink"), keywords("First strike, lifelink\nWhen this enters, draw a card."))
        assertEquals(listOf("Ward"), keywords("Ward {2}"))
        assertEquals(listOf("Ward"), keywords("Ward—Pay 3 life."))
        assertEquals(listOf("Protection"), keywords("Protection from red (This can't be blocked by red creatures.)"))
        assertEquals(emptyList<String>(), keywords("Creatures you control have flying."))
        assertEquals(emptyList<String>(), keywords("Flying creatures can't block this turn."))
        assertEquals(emptyList<String>(), keywords("Flashback {2}{R}"))
        assertEquals(emptyList<String>(), keywords("({T}: Add {G}.)"))
        assertEquals(emptyList<String>(), keywords(null))
    }

    @Test
    fun `colorless identity is named`() {
        val rock = card(7, "Rock", colors = emptyList(), types = listOf("Artifact"))
        val relic = card(8, "Relic", colors = listOf(Color.R), types = listOf("Artifact"))

        val comparison = compareCards(rock, relic)

        assertEquals(listOf("Artifact"), comparison.shared)
        assertEquals(listOf("Colorless"), comparison.uniqueToBase)
        assertEquals(listOf("Red"), comparison.uniqueToOther)
        assertNull(comparison.rows.firstOrNull { it.label == "Colors" })
    }

    private fun card(
        id: Long,
        name: String,
        colors: List<Color> = listOf(Color.G),
        types: List<String> = listOf("Creature"),
        subtypes: List<String> = emptyList(),
        manaValue: Double? = 1.0,
        power: String? = null,
        toughness: String? = null,
        loyalty: String? = null,
        rank: Int? = null,
        text: String? = null,
    ) = CardDto(
        id = id, uuid = "uuid-$id", cardName = name, colorIdentity = colors, colors = colors,
        convertedManaCost = manaValue, text = text, types = types, subtypes = subtypes,
        power = power, toughness = toughness, loyalty = loyalty, edhrecRank = rank,
    )
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest --tests 'edu.csumb.magicshroom.ui.compare.CardComparisonTest'`
Expected: FAIL to compile with `Unresolved reference 'compareCards'`.

- [ ] **Step 3: Implement the rules**

`CardComparison.kt`:

```kotlin
package edu.csumb.magicshroom.ui.compare

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.Color

/** How the base card's value compares with the other card's. */
enum class Verdict { BETTER, WORSE, EQUAL, NOT_COMPARABLE }

/** One table row: a stat's text on each card and the base card's verdict. */
data class StatRow(val label: String, val baseValue: String, val otherValue: String, val verdict: Verdict)

/** Everything the comparison screen shows for a base card against another card. */
data class CardComparison(
    val rows: List<StatRow>,
    val shared: List<String>,
    val uniqueToBase: List<String>,
    val uniqueToOther: List<String>,
) {
    /** Rows where the base card is better. */
    val betterCount: Int get() = rows.count { it.verdict == Verdict.BETTER }

    /** Rows where the base card is worse. */
    val worseCount: Int get() = rows.count { it.verdict == Verdict.WORSE }

    /** Rows where both cards are the same. */
    val equalCount: Int get() = rows.count { it.verdict == Verdict.EQUAL }

    /** Traits that only one of the two cards has. */
    val uniqueCount: Int get() = uniqueToBase.size + uniqueToOther.size
}

/**
 * Compares [other] with [base], always from [base]'s point of view: lower mana value and popularity
 * rank are better, higher power, toughness, and loyalty are better.
 */
fun compareCards(base: CardDto, other: CardDto): CardComparison {
    val sameColors = base.colorIdentity.toSet() == other.colorIdentity.toSet()
    val rows = buildList {
        add(numberRow("Mana value", base.convertedManaCost, other.convertedManaCost, ::formatNumber, higherIsBetter = false))
        textStatRow("Power", base.power, other.power)?.let(::add)
        textStatRow("Toughness", base.toughness, other.toughness)?.let(::add)
        textStatRow("Loyalty", base.loyalty, other.loyalty)?.let(::add)
        if (base.edhrecRank != null || other.edhrecRank != null) {
            add(numberRow("Popularity", base.edhrecRank?.toDouble(), other.edhrecRank?.toDouble(),
                { "#${it.toLong()}" }, higherIsBetter = false, missing = "Unranked"))
        }
        if (sameColors) add(StatRow("Colors", colorCodes(base), colorCodes(other), Verdict.EQUAL))
    }
    val baseTraits = traits(base, includeColors = !sameColors)
    val otherTraits = traits(other, includeColors = !sameColors)
    return CardComparison(
        rows = rows,
        shared = baseTraits.filter(otherTraits::contains),
        uniqueToBase = baseTraits.filterNot(otherTraits::contains),
        uniqueToOther = otherTraits.filterNot(baseTraits::contains),
    )
}

/** Keyword abilities from lines of [text] that list only keywords, e.g. "Flying, vigilance". */
internal fun keywords(text: String?): List<String> = text.orEmpty().lines()
    .map { it.replace(REMINDER_TEXT, "").trim() }
    .filter(String::isNotEmpty)
    .mapNotNull { line ->
        val found = line.split(',').map(String::trim).filter(String::isNotEmpty).map(::keywordOf)
        found.takeIf { it.all { keyword -> keyword != null } }?.filterNotNull()
    }
    .flatten()
    .distinct()

/** The keyword [part] is, or null: "Ward {2}" is Ward and "Protection from red" is Protection. */
private fun keywordOf(part: String): String? = KEYWORDS.firstOrNull { keyword ->
    part.startsWith(keyword, ignoreCase = true) && part.substring(keyword.length).let { rest ->
        rest.isEmpty() ||
            (keyword in FROM_KEYWORDS && rest.startsWith(" from ", ignoreCase = true)) ||
            (keyword == "Ward" && (rest.startsWith(" {") || rest.startsWith("—")))
    }
}

/** A row for text stats such as `4` or `*`; null when neither card has the stat. */
private fun textStatRow(label: String, base: String?, other: String?): StatRow? {
    if (base == null && other == null) return null
    val verdict = verdict(base?.toDoubleOrNull(), other?.toDoubleOrNull(), higherIsBetter = true)
    return StatRow(label, base ?: MISSING, other ?: MISSING, verdict)
}

/** A row for numeric stats, shown with [format], or [missing] when a card lacks it. */
private fun numberRow(
    label: String,
    base: Double?,
    other: Double?,
    format: (Double) -> String,
    higherIsBetter: Boolean,
    missing: String = MISSING,
) = StatRow(label, base?.let(format) ?: missing, other?.let(format) ?: missing, verdict(base, other, higherIsBetter))

/** BETTER or WORSE by [higherIsBetter], EQUAL for equal numbers, NOT_COMPARABLE if either is missing. */
private fun verdict(base: Double?, other: Double?, higherIsBetter: Boolean): Verdict = when {
    base == null || other == null -> Verdict.NOT_COMPARABLE
    base == other -> Verdict.EQUAL
    (base > other) == higherIsBetter -> Verdict.BETTER
    else -> Verdict.WORSE
}

/** Keywords, subtypes, supertypes, main types, and (when asked) color names, in that order. */
private fun traits(card: CardDto, includeColors: Boolean): List<String> =
    (keywords(card.text) + card.subtypes + card.supertypes + card.types +
        if (includeColors) colorNames(card) else emptyList()).distinct()

/** Color identity letters for the table, e.g. `G` or `R W`; `C` when colorless. */
private fun colorCodes(card: CardDto): String =
    card.colorIdentity.takeIf { it.isNotEmpty() }?.joinToString(" ") { it.name } ?: "C"

/** Color identity names for trait lists; "Colorless" when there are none. */
private fun colorNames(card: CardDto): List<String> =
    card.colorIdentity.map(COLOR_NAMES::getValue).ifEmpty { listOf(COLOR_NAMES.getValue(Color.C)) }

/** `5` for whole numbers, `1.5` otherwise. */
private fun formatNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

private const val MISSING = "—"
private val REMINDER_TEXT = Regex("""\s*\([^)]*\)""")
private val FROM_KEYWORDS = setOf("Protection", "Hexproof")
private val KEYWORDS = listOf(
    "Flying", "First strike", "Double strike", "Deathtouch", "Defender", "Flash", "Haste", "Hexproof",
    "Indestructible", "Lifelink", "Menace", "Prowess", "Reach", "Trample", "Vigilance", "Ward", "Protection",
)
private val COLOR_NAMES = mapOf(
    Color.W to "White", Color.U to "Blue", Color.B to "Black", Color.R to "Red", Color.G to "Green", Color.C to "Colorless",
)
```

- [ ] **Step 4: Run the tests**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest --tests 'edu.csumb.magicshroom.ui.compare.CardComparisonTest'`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/app/src/main/java/edu/csumb/magicshroom/ui/compare/CardComparison.kt \
  frontend/app/src/test/java/edu/csumb/magicshroom/ui/compare/CardComparisonTest.kt
git commit -m "feat(app): compare two cards stat by stat

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Shared back bar, "Compare with…" button, reusable card tile

**Files:**
- Create: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/common/BackTopBar.kt`
- Modify: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/detail/CardDetailScreen.kt`
- Modify: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/search/SearchScreen.kt:294-295`

- [ ] **Step 1: Extract the back bar**

`BackTopBar.kt` (the same top bar `CardDetailContent` draws today, so its "Back" control and tests keep working):

```kotlin
package edu.csumb.magicshroom.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Top app bar with [title] and a "‹" Back control, shared by the card screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            Box(
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Back" }
                    .clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", style = MaterialTheme.typography.headlineMedium) }
        },
    )
}
```

- [ ] **Step 2: Use it in the detail screen and add the button**

In `CardDetailScreen.kt`:

1. Replace the imports block's lines

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
```

with

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.border
```

2. Remove these now-unused imports: `androidx.compose.foundation.layout.size`, `androidx.compose.material3.ExperimentalMaterial3Api`, `androidx.compose.material3.TopAppBar`, `androidx.compose.ui.semantics.Role`, `androidx.compose.ui.semantics.contentDescription`, `androidx.compose.ui.semantics.semantics`.

3. Add imports `androidx.compose.material3.OutlinedButton` (after `androidx.compose.material3.MaterialTheme`) and `edu.csumb.magicshroom.ui.common.BackTopBar` (after `edu.csumb.magicshroom.data.repository.ApiResult`).

4. In `CardDetailTestTags`, add after `NEXT_FACE`:

```kotlin
    const val COMPARE = "card_detail_compare"
```

5. Replace

```kotlin
/** Loads full details for a selected card from the public API. */
@Composable
fun CardDetailScreen(cardId: Long, onBack: () -> Unit) {
```

with

```kotlin
/** Loads full details for a selected card from the public API; [onCompareClick] shows "Compare with…". */
@Composable
fun CardDetailScreen(cardId: Long, onBack: () -> Unit, onCompareClick: (() -> Unit)? = null) {
```

and in the same function replace `is ApiResult.Success -> CardDetailContent(current.data, onBack)` with `is ApiResult.Success -> CardDetailContent(current.data, onBack, onCompareClick)`.

6. Replace

```kotlin
/** Shows the selected printing's full scan and the card's catalog information. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailContent(card: CardDto, onBack: () -> Unit) {
```

with

```kotlin
/** Shows the selected printing's full scan and the card's catalog information. */
@Composable
fun CardDetailContent(card: CardDto, onBack: () -> Unit, onCompareClick: (() -> Unit)? = null) {
```

7. Replace the whole `topBar = { TopAppBar( … ) },` argument (from `topBar = {` through the `},` that closes it, just before `) { padding ->`) with:

```kotlin
        topBar = { BackTopBar(title = card.cardName, onBack = onBack) },
```

8. Replace

```kotlin
            card.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
```

with

```kotlin
            card.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (onCompareClick != null) {
                OutlinedButton(
                    onClick = onCompareClick,
                    modifier = Modifier.fillMaxWidth().testTag(CardDetailTestTags.COMPARE),
                ) { Text("Compare with…") }
            }
```

- [ ] **Step 3: Let other screens reuse the search tile**

In `SearchScreen.kt`, replace

```kotlin
/** Renders one card scan with a text fallback when the image is missing or unavailable. */
@Composable
private fun SearchCardTile(card: CardDto, onClick: () -> Unit) {
```

with

```kotlin
/** Renders one card scan with a text fallback when the image is missing or unavailable. Shared by card lists. */
@Composable
internal fun SearchCardTile(card: CardDto, onClick: () -> Unit) {
```

- [ ] **Step 4: Build and run the unit tests**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL with no unused-import warnings from `CardDetailScreen.kt`.

- [ ] **Step 5: Commit**

```bash
git add frontend/app/src/main/java/edu/csumb/magicshroom/ui/common/BackTopBar.kt \
  frontend/app/src/main/java/edu/csumb/magicshroom/ui/detail/CardDetailScreen.kt \
  frontend/app/src/main/java/edu/csumb/magicshroom/ui/search/SearchScreen.kt
git commit -m "refactor(app): share the back bar and card tile; add Compare with button

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Similar cards screen

**Files:**
- Create: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/compare/SimilarCardsScreen.kt`
- Modify: `frontend/app/src/test/java/edu/csumb/magicshroom/ui/compare/CardComparisonTest.kt`
- Modify: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/nav/AppNavHost.kt`

- [ ] **Step 1: Write the failing message test**

In `CardComparisonTest.kt`, add the import `import edu.csumb.magicshroom.data.repository.ApiResult` and this test before `private fun card(`:

```kotlin
    @Test
    fun `a 404 from the similar route asks for the latest server`() {
        assertEquals("Comparisons need the latest server version.", similarCardsErrorMessage(ApiResult.HttpError(404, "No static resource")))
        assertEquals("The server ran into a problem. Please try again.", similarCardsErrorMessage(ApiResult.HttpError(500, null)))
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest --tests 'edu.csumb.magicshroom.ui.compare.CardComparisonTest'`
Expected: FAIL to compile with `Unresolved reference 'similarCardsErrorMessage'`.

- [ ] **Step 3: Write the screen**

`SimilarCardsScreen.kt`:

```kotlin
package edu.csumb.magicshroom.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.ui.common.BackTopBar
import edu.csumb.magicshroom.ui.search.SearchCardTile
import edu.csumb.magicshroom.ui.theme.AppBackground

/** Stable identifiers for comparison UI checks. */
object CompareTestTags {
    const val SIMILAR_SCREEN = "similar_cards_screen"
    const val COMPARISON_SCREEN = "card_comparison_screen"
    const val TALLY = "card_comparison_tally"
}

/** Lists the cards most like [cardId] so the player can pick one to compare with it. */
@Composable
fun SimilarCardsScreen(cardId: Long, onBack: () -> Unit, onCardClick: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as MagicShroomApplication
    val repository = remember(app) { app.container.cardRepository }
    var retry by remember { mutableIntStateOf(0) }
    var baseName by remember(cardId) { mutableStateOf("") }
    var result by remember(cardId) { mutableStateOf<ApiResult<List<CardDto>>?>(null) }
    LaunchedEffect(cardId, retry) {
        result = null
        (repository.getCard(cardId) as? ApiResult.Success)?.let { baseName = it.data.cardName }
        result = repository.getSimilarCards(cardId)
    }
    Scaffold(
        modifier = Modifier.testTag(CompareTestTags.SIMILAR_SCREEN),
        containerColor = AppBackground,
        topBar = { BackTopBar(title = if (baseName.isEmpty()) "Compare with…" else "Compare $baseName with…", onBack = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            when (val current = result) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ApiResult.Failure -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(similarCardsErrorMessage(current))
                    Button(onClick = { retry++ }) { Text("Retry") }
                }
                is ApiResult.Success -> if (current.data.isEmpty()) {
                    Text(
                        "No similar cards found.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(current.data, key = { it.id }) { card ->
                            SearchCardTile(card, onClick = { onCardClick(card.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Text for a failed similar-cards load; a 404 means the server predates the similar route. */
internal fun similarCardsErrorMessage(failure: ApiResult.Failure): String =
    if (failure is ApiResult.HttpError && failure.status == 404) "Comparisons need the latest server version."
    else failure.userMessage
```

- [ ] **Step 4: Run the test**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest --tests 'edu.csumb.magicshroom.ui.compare.CardComparisonTest'`
Expected: PASS (7 tests).

- [ ] **Step 5: Add the route**

In `AppNavHost.kt`:

1. Add the import `import edu.csumb.magicshroom.ui.compare.SimilarCardsScreen` (after `import edu.csumb.magicshroom.ui.admin.AdminScreen`).
2. In `AppRoute`, add after `const val CARD = "cards/{cardId}"`:

```kotlin
    const val COMPARE = "cards/{cardId}/compare"
```

3. Replace

```kotlin
                    CardDetailScreen(
                        cardId = requireNotNull(entry.arguments).getLong("cardId"),
                        onBack = { navController.popBackStack() },
                    )
                }
```

with

```kotlin
                    val cardId = requireNotNull(entry.arguments).getLong("cardId")
                    CardDetailScreen(
                        cardId = cardId,
                        onBack = { navController.popBackStack() },
                        onCompareClick = { navController.navigate("cards/$cardId/compare") },
                    )
                }
                composable(AppRoute.COMPARE, arguments = listOf(navArgument("cardId") { type = NavType.LongType })) { entry ->
                    val cardId = requireNotNull(entry.arguments).getLong("cardId")
                    SimilarCardsScreen(
                        cardId = cardId,
                        onBack = { navController.popBackStack() },
                        onCardClick = { otherId -> navController.navigate("cards/$cardId/compare/$otherId") },
                    )
                }
```

- [ ] **Step 6: Build**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. (Tapping a similar card navigates to a route added in Task 10; until then it does nothing visible.)

- [ ] **Step 7: Commit**

```bash
git add frontend/app/src/main/java/edu/csumb/magicshroom/ui/compare/SimilarCardsScreen.kt \
  frontend/app/src/main/java/edu/csumb/magicshroom/ui/nav/AppNavHost.kt \
  frontend/app/src/test/java/edu/csumb/magicshroom/ui/compare/CardComparisonTest.kt
git commit -m "feat(app): list similar cards to compare with

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Comparison screen

**Files:**
- Create: `frontend/app/src/androidTest/java/edu/csumb/magicshroom/CardComparisonUiTest.kt`
- Create: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/compare/CardComparisonScreen.kt`
- Modify: `frontend/app/src/main/java/edu/csumb/magicshroom/ui/nav/AppNavHost.kt`

- [ ] **Step 1: Write the failing UI test**

`CardComparisonUiTest.kt`:

```kotlin
package edu.csumb.magicshroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.Color
import edu.csumb.magicshroom.ui.compare.CardComparisonContent
import edu.csumb.magicshroom.ui.compare.CompareTestTags
import edu.csumb.magicshroom.ui.detail.CardDetailContent
import edu.csumb.magicshroom.ui.detail.CardDetailTestTags
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies the "Compare with…" entry point and the verdicts a player sees. */
class CardComparisonUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val llanowar = CardDto(
        id = 1, uuid = "u1", cardName = "Llanowar Elves", colorIdentity = listOf(Color.G), colors = listOf(Color.G),
        convertedManaCost = 1.0, types = listOf("Creature"), subtypes = listOf("Elf", "Druid"),
        power = "1", toughness = "1", edhrecRank = 65,
    )
    private val serra = CardDto(
        id = 2, uuid = "u2", cardName = "Serra Angel", colorIdentity = listOf(Color.W), colors = listOf(Color.W),
        convertedManaCost = 5.0, text = "Flying, vigilance", types = listOf("Creature"), subtypes = listOf("Angel"),
        power = "4", toughness = "4", edhrecRank = 4698,
    )

    @Test
    fun detailCompareButtonOpensComparison() {
        var clicked = false
        composeRule.setContent { MagicShroomTheme { CardDetailContent(llanowar, onBack = {}, onCompareClick = { clicked = true }) } }

        composeRule.onNodeWithTag(CardDetailTestTags.COMPARE).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun comparisonShowsTallyVerdictsAndUniqueTraits() {
        composeRule.setContent { MagicShroomTheme { CardComparisonContent(llanowar, serra, onBack = {}) } }

        composeRule.onNodeWithTag(CompareTestTags.COMPARISON_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 better, 2 worse, 0 equal, 7 unique").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mana value: Llanowar Elves 1, Serra Angel 5, better").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Power: Llanowar Elves 1, Serra Angel 4, worse").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Unique to Serra Angel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("★ Flying  ★ Vigilance  ★ Angel  ★ White").performScrollTo().assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Verify it fails to compile**

Run: `cd frontend && ./gradlew :app:assembleDebugAndroidTest`
Expected: FAIL with `Unresolved reference 'CardComparisonContent'`.

- [ ] **Step 3: Write the screen**

`CardComparisonScreen.kt`:

```kotlin
package edu.csumb.magicshroom.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.ui.common.BackTopBar
import edu.csumb.magicshroom.ui.search.SearchCardTile
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BrandPurple

/** Loads both cards (normally from the cache the similar list filled) and compares them. */
@Composable
fun CardComparisonScreen(baseId: Long, otherId: Long, onBack: () -> Unit, onCardClick: (Long) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MagicShroomApplication
    val repository = remember(app) { app.container.cardRepository }
    var retry by remember { mutableIntStateOf(0) }
    var result by remember(baseId, otherId) { mutableStateOf<ApiResult<List<CardDto>>?>(null) }
    LaunchedEffect(baseId, otherId, retry) {
        result = null
        result = repository.getCardsByIds(listOf(baseId, otherId))
    }
    when (val current = result) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        // getCardsByIds removes duplicates, so first/last also covers comparing a card with itself.
        is ApiResult.Success -> CardComparisonContent(current.data.first(), current.data.last(), onBack, onCardClick)
        is ApiResult.Failure -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(current.userMessage)
            Button(onClick = { retry++ }) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

/** Both cards, the tally, one row per stat, and the traits each card shares or has alone. */
@Composable
fun CardComparisonContent(base: CardDto, other: CardDto, onBack: () -> Unit, onCardClick: (Long) -> Unit = {}) {
    val comparison = remember(base, other) { compareCards(base, other) }
    Scaffold(
        modifier = Modifier.testTag(CompareTestTags.COMPARISON_SCREEN),
        containerColor = AppBackground,
        topBar = { BackTopBar(title = "Comparison", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SearchCardTile(base, onClick = { onCardClick(base.id) }) }
                Text("vs", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.weight(1f)) { SearchCardTile(other, onClick = { onCardClick(other.id) }) }
            }
            Tally(comparison)
            StatTable(base.cardName, other.cardName, comparison.rows)
            if (comparison.shared.isNotEmpty()) TraitList("Shared", comparison.shared, VerdictStyle.EQUAL)
            TraitList("Unique to ${base.cardName}", comparison.uniqueToBase, VerdictStyle.UNIQUE)
            TraitList("Unique to ${other.cardName}", comparison.uniqueToOther, VerdictStyle.UNIQUE)
        }
    }
}

/** Icon, word, and color for each verdict, so color is never the only signal (WCAG 1.4.1). */
internal enum class VerdictStyle(val symbol: String, val word: String, val color: Color) {
    BETTER("▲", "Better", Color(0xFF2E7D32)),
    WORSE("▼", "Worse", Color(0xFFC62828)),
    EQUAL("=", "Equal", Color(0xFF616161)),
    UNIQUE("★", "Unique", BrandPurple),
    NOT_COMPARABLE("—", "Not comparable", Color(0xFF616161)),
}

/** The style that shows this verdict. */
internal fun Verdict.style(): VerdictStyle = when (this) {
    Verdict.BETTER -> VerdictStyle.BETTER
    Verdict.WORSE -> VerdictStyle.WORSE
    Verdict.EQUAL -> VerdictStyle.EQUAL
    Verdict.NOT_COMPARABLE -> VerdictStyle.NOT_COMPARABLE
}

/** "▲ 2 better · ▼ 2 worse · = 0 equal · ★ 7 unique", each part in its verdict color. */
@Composable
private fun Tally(comparison: CardComparison) {
    val parts = listOf(
        VerdictStyle.BETTER to comparison.betterCount,
        VerdictStyle.WORSE to comparison.worseCount,
        VerdictStyle.EQUAL to comparison.equalCount,
        VerdictStyle.UNIQUE to comparison.uniqueCount,
    )
    Text(
        text = buildAnnotatedString {
            parts.forEachIndexed { index, (style, count) ->
                if (index > 0) append("  ·  ")
                withStyle(SpanStyle(color = style.color, fontWeight = FontWeight.SemiBold)) {
                    append("${style.symbol} $count ${style.word.lowercase()}")
                }
            }
        },
        modifier = Modifier.testTag(CompareTestTags.TALLY).clearAndSetSemantics {
            contentDescription = parts.joinToString { (style, count) -> "$count ${style.word.lowercase()}" }
        },
        style = MaterialTheme.typography.titleSmall,
    )
}

/** Header plus one row per stat: label, base value, verdict badge, other value. */
@Composable
private fun StatTable(baseName: String, otherName: String, rows: List<StatRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Stat", Modifier.weight(1.1f), fontWeight = FontWeight.SemiBold)
            Text(baseName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
            Text("vs", Modifier.weight(1.3f), textAlign = TextAlign.Center)
            Text(otherName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        }
        HorizontalDivider()
        rows.forEach { row ->
            val style = row.verdict.style()
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "${row.label}: $baseName ${row.baseValue}, $otherName ${row.otherValue}, ${style.word.lowercase()}"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.label, Modifier.weight(1.1f))
                Text(row.baseValue, Modifier.weight(1f), textAlign = TextAlign.Center)
                VerdictBadge(style, Modifier.weight(1.3f))
                Text(row.otherValue, Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }
    }
}

/** A tinted pill with the verdict's symbol and word. */
@Composable
private fun VerdictBadge(style: VerdictStyle, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "${style.symbol} ${style.word}",
            modifier = Modifier.background(style.color.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = style.color,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** A titled line of traits, each marked with [style]'s symbol; "None" when empty. */
@Composable
private fun TraitList(title: String, traits: List<String>, style: VerdictStyle) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (traits.isEmpty()) "None" else traits.joinToString("  ") { "${style.symbol} $it" },
            color = if (traits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else style.color,
        )
    }
}
```

- [ ] **Step 4: Add the route**

In `AppNavHost.kt`:

1. Add the import `import edu.csumb.magicshroom.ui.compare.CardComparisonScreen` (before the `SimilarCardsScreen` import).
2. In `AppRoute`, add after `COMPARE`:

```kotlin
    const val COMPARISON = "cards/{cardId}/compare/{otherId}"
```

3. Insert after the `composable(AppRoute.COMPARE, …) { … }` block:

```kotlin
                composable(
                    AppRoute.COMPARISON,
                    arguments = listOf(
                        navArgument("cardId") { type = NavType.LongType },
                        navArgument("otherId") { type = NavType.LongType },
                    ),
                ) { entry ->
                    val arguments = requireNotNull(entry.arguments)
                    CardComparisonScreen(
                        baseId = arguments.getLong("cardId"),
                        otherId = arguments.getLong("otherId"),
                        onBack = { navController.popBackStack() },
                        onCardClick = { navController.navigate("cards/$it") },
                    )
                }
```

- [ ] **Step 5: Build, then run the UI tests on the emulator**

Run: `cd frontend && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
Expected: BUILD SUCCESSFUL.

Run (emulator running): `cd frontend && ./gradlew :app:connectedDebugAndroidTest`
Expected: all instrumented tests pass, including both `CardComparisonUiTest` tests and the existing `CardGalleryTest` (which still opens the detail screen and its Back control).

- [ ] **Step 6: Commit**

```bash
git add frontend/app/src/main/java/edu/csumb/magicshroom/ui/compare/CardComparisonScreen.kt \
  frontend/app/src/main/java/edu/csumb/magicshroom/ui/nav/AppNavHost.kt \
  frontend/app/src/androidTest/java/edu/csumb/magicshroom/CardComparisonUiTest.kt
git commit -m "feat(app): show card comparison with better, worse, equal, and unique

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: READMEs reflect the project as it stands

**Files:**
- Modify: `README.md` (rewrite)
- Modify: `frontend/README.md` (rewrite; it still describes the removed demo login)
- Modify: `backend/README.md` (Run section and a feature note)

- [ ] **Step 1: Rewrite the root README**

Replace the whole of `README.md` with:

````markdown
# MagicShroom

MagicShroom is a Magic: The Gathering card catalog with personal decks and card comparison. The
backend is a Kotlin/Spring Boot API on Supabase PostgreSQL, deployed to Render; the Android app uses
Jetpack Compose and Supabase Auth.

## What works today

| Area | Status |
|---|---|
| Card catalog | 20,478 cards from the MTGJSON archive in Supabase; search by name and color, paging, card detail, and a batch lookup. |
| Card images | Scryfall scans for 20,473 cards. The app downloads them from Scryfall with its own User-Agent. |
| Card comparison | "Compare with…" on a card lists similar cards (same type, same or overlapping colors); the comparison marks mana value, power, toughness, loyalty, popularity, and colors as better, worse, or equal, and lists keywords and types unique to each card. |
| Sign-in | Supabase email/password and Google. The API verifies each access token against the project's signing keys and creates the app account on first use. |
| Decks | Full deck API (create, rename, delete, add and remove cards). The app has the data layer but no deck screens yet. |
| Admin | Users with the `ADMIN` role see Admin tools in the app: list users, change roles, and delete users (including their Supabase login when the server has the admin key). Promote the first admin with SQL; see below. |
| Deployment | GitHub Actions tests every pull request; a push to `main` also publishes the API image to GHCR and asks Render to deploy it. |

## Run it locally

1. Copy `.env.example` to `.env` and fill in the Supabase database and URL values (never commit it).
2. Start the API from the repository root:
   ```bash
   set -a && . ./.env && set +a && cd backend && ./gradlew bootRun
   ```
   Spring Boot does not read `.env` itself; without the first part it fails with
   `Could not resolve placeholder 'SUPABASE_URL'`.
3. Give the app its settings in `~/.gradle/gradle.properties` (machine-local, never committed):
   ```properties
   supabaseUrl=https://YOUR_PROJECT_REF.supabase.co
   supabasePublishableKey=sb_publishable_...
   backendUrl=https://magicshroom.onrender.com
   ```
   To use the local API instead, build with `-PbackendUrl=http://10.0.2.2:8080` (the emulator's
   address for your computer).
4. Open `frontend/` in Android Studio and run the app. Open `backend/` as its own project; the
   repository root is not a Gradle project.

Tests: `cd backend && ./gradlew test` (the PostgreSQL test needs Docker) and
`cd frontend && ./gradlew :app:testDebugUnitTest`.

## Make someone an admin

After the person has signed in once, run in the Supabase SQL editor or `psql`:

```sql
insert into app_users (auth_user_id, email, display_name, role)
select id, email, coalesce(nullif(raw_user_meta_data->>'full_name', ''), split_part(email, '@', 1)), 'ADMIN'
from auth.users where email = 'teammate@example.com'
on conflict (auth_user_id) do update set role = 'ADMIN'
returning id, email, role;
```

## Documentation

- [`backend/README.md`](backend/README.md): API configuration, profiles, tests, and Docker.
- [`frontend/README.md`](frontend/README.md): app configuration, sign-in, screens, and tests.
- [`docs/openapi.yaml`](docs/openapi.yaml): API contract.
- [`docs/supabase-deployment.md`](docs/supabase-deployment.md): Supabase setup, schema, and the one-time card import.
- [`docs/render-deployment.md`](docs/render-deployment.md) and [`docs/CI-RENDER-DEPLOYMENT.md`](docs/CI-RENDER-DEPLOYMENT.md): Render service and the CI deploy (the `RENDER_DEPLOY_HOOK_URL` repository secret must be set for automatic deploys).
- [`docs/superpowers/specs/`](docs/superpowers/specs/): feature designs, such as card comparison.

Database credentials and the Supabase secret key stay on the server. The app only holds the
Supabase publishable key and the signed-in user's access token.
````

- [ ] **Step 2: Rewrite the frontend README**

Replace the whole of `frontend/README.md` with:

````markdown
# MagicShroom Android app

A Jetpack Compose app for browsing the card catalog, comparing cards, and managing users (admins).

## Configure

The build reads three Gradle properties. Put them in `~/.gradle/gradle.properties` so they stay on
your machine:

```properties
supabaseUrl=https://YOUR_PROJECT_REF.supabase.co
supabasePublishableKey=sb_publishable_...
backendUrl=https://magicshroom.onrender.com
```

Without them the app falls back to placeholders: sign-in opens `example.supabase.co`, which does not
exist. The app does not read the repository's `.env` file; that file is for the backend.
`backendUrl` defaults to `http://10.0.2.2:8080`, a backend running on your computer as seen from the
emulator; override it for one build with `-PbackendUrl=...`. On Android 17 (API 37), allow
local-network access when the app asks.

## Sign-in

Email/password and Google both go through Supabase Auth. Google returns to the app through the
`magicshroom://auth` link, which must be in the Supabase project's allowed redirect URLs. The app
sends the Supabase access token to the backend on every request.

## Screens

- **Search:** card gallery with name search and paging.
- **Card detail:** full scan (with a face switch for double-faced cards), mana cost, type line, rules
  text, and **Compare with…**.
- **Similar cards:** the cards most like the selected one; tap one to compare.
- **Comparison:** both cards, a tally, a stat table where each verdict has a symbol, a word, and a
  color (▲ Better, ▼ Worse, = Equal, — Not comparable), and the traits each card has alone (★).
- **Account:** profile and sign-out; **Admin tools** appear for users with the `ADMIN` role.

Card images come from Scryfall, which rejects default HTTP-library User-Agents, so the app's image
loader (`MagicShroomApplication`) sends its own.

## Build and test

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # with an emulator running
```

For UI work on mock data, run `cd backend && ./gradlew bootTestRun` and build with
`-PbackendUrl=http://10.0.2.2:8080 -PstubUserId=1`; the mock backend then treats every request as
that user (1 is the admin). You still sign in through Supabase first.
````

- [ ] **Step 3: Update the backend README**

In `backend/README.md`, replace the paragraph

```markdown
The backend validates Supabase bearer tokens with the project's JWKS endpoint. Configure an
```

with

```markdown
With a filled-in `../.env`, the same is `set -a && . ../.env && set +a && ./gradlew bootRun`;
Spring Boot does not read `.env` files itself.

The backend validates Supabase bearer tokens with the project's JWKS endpoint. Configure an
```

and add this paragraph after the paragraph that begins `The backend validates Supabase bearer tokens` (it ends with `all writes and deck routes do.`):

```markdown
`GET /api/v1/cards/{cardId}/similar` (public) ranks cards that share a main type with the card:
same color identity first, then shared colors; within each, closest mana value, most shared
subtypes, and most popular. The rules live in `SimilarCardRanking` (used by the mock profile) and in
one SQL query in `PostgresCardRepository`; `SimilarCardFixtures` tests that both agree. Card
responses also carry `power`, `toughness`, `loyalty`, and `edhrecRank` from the archive.
```

- [ ] **Step 4: Check the Markdown links resolve**

Run: `cd /home/brandon/AndroidStudioProjects/MagicShroom && grep -ohE '\]\(([^)#]+)\)' README.md frontend/README.md backend/README.md | sed -E 's/^\]\(|\)$//g' | grep -v '^http' | while read -r p; do for base in . frontend backend; do [ -e "$base/$p" ] && { echo "ok  $p"; break; }; done || echo "MISSING $p"; done | sort -u`
Expected: every line starts with `ok`.

- [ ] **Step 5: Commit**

```bash
git add README.md frontend/README.md backend/README.md
git commit -m "docs: describe current features, local setup, and card comparison

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: End-to-end verification

- [ ] **Step 1: Full test suites**

Run: `cd backend && ./gradlew clean build` then `cd ../frontend && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: both BUILD SUCCESSFUL; the backend count includes the new ranking, route, and PostgreSQL tests (none SKIPPED with Docker running).

- [ ] **Step 2: Check the SQL's cost on real data (read-only)**

Run from the repository root:

```bash
( set -a; . ./.env; set +a
  hp="${DATABASE_URL#jdbc:postgresql://}"; hp="${hp%%/*}"
  PGOPTIONS='-c default_transaction_read_only=on' PGPASSWORD="$DATABASE_PASSWORD" psql "host=${hp%%:*} port=${hp##*:} dbname=postgres user=$DATABASE_USER sslmode=require" -X -q <<'SQL'
explain (analyze, costs off, summary on)
with base as (
    select id as base_id, color_identity as base_colors, types as base_types, subtypes as base_subtypes,
        coalesce(converted_mana_cost, 0) as base_mana_value
    from cards where id = (select id from cards where card_name = 'Llanowar Elves')
)
select id, card_name from cards cross join base
where id <> base_id and types && base_types
    and ((color_identity @> base_colors and color_identity <@ base_colors) or color_identity && base_colors)
order by case when color_identity @> base_colors and color_identity <@ base_colors then 0 else 1 end,
    abs(coalesce(converted_mana_cost, 0) - base_mana_value),
    cardinality(array(select unnest(subtypes) intersect select unnest(base_subtypes))) desc,
    edhrec_rank asc nulls last, id
limit 10;
SQL
)
```

Expected: `Execution Time` well under 200 ms. If it is slower, report the plan; do not add an index without asking (the database is shared and in production).

- [ ] **Step 3: Try it in the app against the local backend**

1. Start the API: `set -a && . ./.env && set +a && cd backend && ./gradlew bootRun` and wait for `Started MagicShroomApplicationKt`.
2. `curl -s 'http://localhost:8080/api/v1/cards/10156/similar?limit=3'` (Llanowar Elves) returns three green creatures, Elvish Mystic-like elves first.
3. Install against it: `cd frontend && ./gradlew :app:installDebug -PbackendUrl=http://10.0.2.2:8080`.
4. In the emulator: sign in, open Llanowar Elves, tap **Compare with…**, pick a card, and check the images, tally, table, and unique lists. Back returns to the list, then to the card.
5. Stop the API.

- [ ] **Step 4: Report**

Summarize the commits on `feature/card-comparison`, the test counts, the query timing, and that Render needs a deploy of `main` after merge (the `RENDER_DEPLOY_HOOK_URL` secret, or a Manual Deploy) before the installed app's default Render backend serves comparisons.
