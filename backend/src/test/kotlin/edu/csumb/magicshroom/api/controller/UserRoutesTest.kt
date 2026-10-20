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
import org.springframework.test.web.servlet.patch

/**
 * Exercises the account and admin user-management routes against the mock data: user 1 is the only
 * admin, users 2 and 3 are regular users. Tests that change data reset the context afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UserRoutesTest(@Autowired private val mockMvc: MockMvc) {

	private val admin = "1"
	private val playerOne = "2"
	private val playerTwo = "3"

	/** Admins page through every user, optionally filtered by role. */
	@Test
	fun `admin lists users and can filter by role`() {
		mockMvc.get("/api/v1/users") { header("X-Stub-User-Id", admin) }.andExpect {
			status { isOk() }
			jsonPath("$.page.totalElements") { value(3) }
			jsonPath("$.content[0].role") { value("ADMIN") }
		}
		mockMvc.get("/api/v1/users?role=USER") { header("X-Stub-User-Id", admin) }.andExpect {
			status { isOk() }
			jsonPath("$.page.totalElements") { value(2) }
		}
	}

	/** Regular users cannot see the user list. */
	@Test
	fun `regular user cannot list users`() {
		mockMvc.get("/api/v1/users") { header("X-Stub-User-Id", playerOne) }
			.andExpect { status { isForbidden() } }
	}

	/** Any signed-in user can read their own account and role. */
	@Test
	fun `me returns the caller's account`() {
		mockMvc.get("/api/v1/users/me") { header("X-Stub-User-Id", playerOne) }.andExpect {
			status { isOk() }
			jsonPath("$.displayName") { value("Player One") }
			jsonPath("$.role") { value("USER") }
		}
	}

	/** An unknown user id is a 404 for admins. */
	@Test
	fun `admin gets a missing user as 404`() {
		mockMvc.get("/api/v1/users/999") { header("X-Stub-User-Id", admin) }
			.andExpect { status { isNotFound() } }
	}

	/** Granting ADMIN takes effect immediately on the next request. */
	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	fun `admin grants the admin role`() {
		mockMvc.patch("/api/v1/users/2") { header("X-Stub-User-Id", admin); json("""{"role": "ADMIN"}""") }.andExpect {
			status { isOk() }
			jsonPath("$.role") { value("ADMIN") }
		}
		mockMvc.get("/api/v1/users") { header("X-Stub-User-Id", playerOne) }
			.andExpect { status { isOk() } }
	}

	/** The last admin cannot be demoted, so the app can never lock itself out. */
	@Test
	fun `demoting the last admin is a conflict`() {
		mockMvc.patch("/api/v1/users/1") { header("X-Stub-User-Id", admin); json("""{"role": "USER"}""") }
			.andExpect { status { isConflict() } }
	}

	/** A role outside the contract's enum is rejected with a field-specific 400. */
	@Test
	fun `invalid role is a 400 naming the field`() {
		mockMvc.patch("/api/v1/users/2") { header("X-Stub-User-Id", admin); json("""{"role": "OWNER"}""") }.andExpect {
			status { isBadRequest() }
			jsonPath("$.detail") { value("Missing or invalid value for 'role'.") }
		}
	}

	/** Deleting your own account removes the account and the decks you owned. */
	@Test
	@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
	fun `deleting my account removes it and my decks`() {
		mockMvc.delete("/api/v1/users/me") { header("X-Stub-User-Id", playerTwo) }
			.andExpect { status { isNoContent() } }
		mockMvc.get("/api/v1/users/3") { header("X-Stub-User-Id", admin) }
			.andExpect { status { isNotFound() } }
		// Card 2 was only in user 3's deck, so it can be deleted now that the deck is gone.
		mockMvc.delete("/api/v1/cards/2") { header("X-Stub-User-Id", admin) }
			.andExpect { status { isNoContent() } }
	}

	/** The only admin cannot delete their own account. */
	@Test
	fun `last admin cannot delete their own account`() {
		mockMvc.delete("/api/v1/users/me") { header("X-Stub-User-Id", admin) }
			.andExpect { status { isConflict() } }
	}

	private fun org.springframework.test.web.servlet.MockHttpServletRequestDsl.json(body: String) {
		contentType = MediaType.APPLICATION_JSON
		content = body
	}
}
