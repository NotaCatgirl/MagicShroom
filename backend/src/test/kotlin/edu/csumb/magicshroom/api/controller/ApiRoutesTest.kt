package edu.csumb.magicshroom.api.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Exercises the routes end to end against the mock JSON data in src/test/resources/mock-data.
 * Tests that change data reset the application context afterwards so they cannot affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiRoutesTest(@Autowired private val mockMvc: MockMvc) {

	private val admin = "1"
	private val playerTwo = "3"

	/** Reports a healthy API for the container and Render health checks. */
	@Test
	fun `health endpoint reports up`() {
		mockMvc.get("/actuator/health").andExpect {
			status { isOk() }
			jsonPath("$.status") { value("UP") }
		}
	}

	@Test
	fun `lists the first page of cards with paging info`() {
		mockMvc.get("/api/v1/cards").andExpect {
			status { isOk() }
			jsonPath("$.content.length()") { value(20) }
			jsonPath("$.content[0].id") { isNumber() }
			jsonPath("$.content[0].cardName") { isString() }
			jsonPath("$.page.totalElements") { value(22) }
			jsonPath("$.page.totalPages") { value(2) }
		}
	}

	/** Supplies the second home-screen page without repeating the first twenty cards. */
	@Test
	fun `lists the second page of cards`() {
		mockMvc.get("/api/v1/cards?page=1&size=20").andExpect {
			status { isOk() }
			jsonPath("$.content.length()") { value(2) }
			jsonPath("$.page.number") { value(1) }
			jsonPath("$.page.totalElements") { value(22) }
		}
	}

	/** Returns the name, mana cost, and text shown when a home-screen card is selected. */
	@Test
	fun `card detail contains home-screen fields and structured metadata`() {
		mockMvc.get("/api/v1/cards/1").andExpect {
			status { isOk() }
			jsonPath("$.id") { value(1) }
			jsonPath("$.cardName") { value("Lightning Bolt") }
			jsonPath("$.manaCost") { value("{R}") }
			jsonPath("$.text") { value("Lightning Bolt deals 3 damage to any target.") }
			jsonPath("$.colorIdentity[0]") { value("R") }
			jsonPath("$.foreignData[0].name") { value("Relámpago") }
			jsonPath("$.legalities[0].format") { value("modern") }
			jsonPath("$.purchaseUrls[0].vendor") { value("example") }
			jsonPath("$.imageFaces[0].normalUrl") { value("https://cards.scryfall.io/normal/front/6/7/example.jpg") }
		}
	}

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

	/** Returns unique full card records in request order from the public batch route. */
	@Test
	fun `batch cards preserves request order and removes duplicate ids`() {
		mockMvc.post("/api/v1/cards/batch") { json("""{"cardIds":[2,1,2]}""") }.andExpect {
			status { isOk() }
			jsonPath("$.length()") { value(2) }
			jsonPath("$[0].id") { value(2) }
			jsonPath("$[1].id") { value(1) }
			jsonPath("$[1].cardName") { value("Lightning Bolt") }
		}
	}

	/** Rejects empty, oversized, non-positive, and unknown card batches. */
	@Test
	fun `batch cards validates ids and reports missing cards`() {
		mockMvc.post("/api/v1/cards/batch") { json("""{"cardIds":[]}""") }
			.andExpect { status { isBadRequest() } }
		val oversizedIds = (1..101).joinToString(",")
		mockMvc.post("/api/v1/cards/batch") { json("""{"cardIds":[$oversizedIds]}""") }
			.andExpect { status { isBadRequest() } }
		mockMvc.post("/api/v1/cards/batch") { json("""{"cardIds":[0]}""") }
			.andExpect { status { isBadRequest() } }
		mockMvc.post("/api/v1/cards/batch") { json("""{"cardIds":[1,999]}""") }.andExpect {
			status { isNotFound() }
			jsonPath("$.detail") { value("Cards not found: 999.") }
		}
	}

	@Test
	fun `filters cards by name and color identity together`() {
		mockMvc.get("/api/v1/cards?name=LIGHT&color=W").andExpect {
			status { isOk() }
			jsonPath("$.content.length()") { value(1) }
			jsonPath("$.content[0].cardName") { value("Lightning Helix") }
		}
	}

	/** Returns an empty list, rather than an error, for a search with no matches. */
	@Test
	fun `unmatched card search returns an empty page`() {
		mockMvc.get("/api/v1/cards?name=not-a-real-card-name").andExpect {
			status { isOk() }
			jsonPath("$.content.length()") { value(0) }
			jsonPath("$.page.totalElements") { value(0) }
		}
	}

	/** Lists only the current user's decks for the signed-in home-screen section. */
	@Test
	fun `lists only the current users decks`() {
		mockMvc.get("/api/v1/decks").andExpect {
			status { isOk() }
			jsonPath("$.length()") { value(2) }
			jsonPath("$[0].name") { value("Mono Red Burn") }
			jsonPath("$[0].cards[0].cardId") { value(1) }
			jsonPath("$[0].cards[0].quantity") { value(4) }
			jsonPath("$[0].cards[0].section") { value("MAINBOARD") }
		}
	}

	/** Rejects an unknown test caller on the personal-decks route. */
	@Test
	fun `unknown caller cannot list decks`() {
		mockMvc.get("/api/v1/decks") { header("X-Stub-User-Id", "999") }
			.andExpect { status { isUnauthorized() } }
	}

	@Test
	fun `missing card is a 404 problem`() {
		mockMvc.get("/api/v1/cards/999").andExpect {
			status { isNotFound() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
			jsonPath("$.detail") { value("Card 999 was not found.") }
		}
	}

	@Test
	fun `out-of-range page size is a 400 naming the parameter`() {
		mockMvc.get("/api/v1/cards?size=0").andExpect {
			status { isBadRequest() }
			jsonPath("$.detail") { value("size must be greater than or equal to 1") }
		}
	}

	@Test
	fun `creating a card needs a known user with the admin role`() {
		mockMvc.post("/api/v1/cards") { json(newCard) }.andExpect { status { isForbidden() } }
		mockMvc.post("/api/v1/cards") { json(newCard); header("X-Stub-User-Id", "999") }
			.andExpect { status { isUnauthorized() } }
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	fun `admin creates a card once, then the same uuid conflicts`() {
		mockMvc.post("/api/v1/cards") { json(newCard); header("X-Stub-User-Id", admin) }.andExpect {
			status { isCreated() }
			header { string("Location", "/api/v1/cards/23") }
			jsonPath("$.cardName") { value("Test Card") }
		}
		mockMvc.post("/api/v1/cards") { json(newCard); header("X-Stub-User-Id", admin) }
			.andExpect { status { isConflict() } }
	}

	@Test
	fun `a card still used by a deck cannot be deleted`() {
		mockMvc.delete("/api/v1/cards/1") { header("X-Stub-User-Id", admin) }
			.andExpect { status { isConflict() } }
	}

	@Test
	fun `a deck owned by someone else looks like it does not exist`() {
		mockMvc.get("/api/v1/decks/3").andExpect { status { isNotFound() } }
		mockMvc.get("/api/v1/decks/3") { header("X-Stub-User-Id", playerTwo) }.andExpect {
			status { isOk() }
			jsonPath("$.name") { value("Azorius Control") }
			jsonPath("$.cards.length()") { value(4) }
		}
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	fun `adds a card to a deck with default quantity and section, but only once`() {
		mockMvc.post("/api/v1/decks/1/cards") { json("""{"cardId": 9}""") }.andExpect {
			status { isCreated() }
			jsonPath("$.quantity") { value(1) }
			jsonPath("$.section") { value("MAINBOARD") }
		}
		mockMvc.post("/api/v1/decks/1/cards") { json("""{"cardId": 9}""") }
			.andExpect { status { isConflict() } }
	}

	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	fun `deleting a user also deletes their decks`() {
		mockMvc.delete("/api/v1/users/3") { header("X-Stub-User-Id", admin) }
			.andExpect { status { isNoContent() } }
		// Card 2 was only in user 3's deck, so it is free to delete now.
		mockMvc.delete("/api/v1/cards/2") { header("X-Stub-User-Id", admin) }
			.andExpect { status { isNoContent() } }
	}

	private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.json(body: String) {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}

	private val newCard = """
		{"uuid": "11111111-2222-4333-8444-555555555555", "cardName": "Test Card",
		 "colorIdentity": ["G"], "colors": ["G"], "types": ["Creature"]}
	""".trimIndent()
}
