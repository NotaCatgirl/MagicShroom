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
 * Exercises the routes end to end against the mock JSON data (see src/main/resources/mock-data).
 * Tests that change data reset the application context afterwards so they cannot affect each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiRoutesTest(@Autowired private val mockMvc: MockMvc) {

	private val admin = "1"
	private val playerTwo = "3"

	@Test
	fun `lists the first page of cards with paging info`() {
		mockMvc.get("/api/v1/cards").andExpect {
			status { isOk() }
			jsonPath("$.content.length()") { value(20) }
			jsonPath("$.page.totalElements") { value(22) }
			jsonPath("$.page.totalPages") { value(2) }
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
