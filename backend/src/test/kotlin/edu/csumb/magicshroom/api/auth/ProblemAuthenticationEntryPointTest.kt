package edu.csumb.magicshroom.api.auth

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.InsufficientAuthenticationException
import tools.jackson.databind.json.JsonMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Security-filter 401s must use the same Problem Details body as every other API error. */
class ProblemAuthenticationEntryPointTest {
	private val json = JsonMapper.builder().build()
	private val entryPoint = ProblemAuthenticationEntryPoint(json)

	/** A request without a token gets a 401 problem body plus the bearer challenge header. */
	@Test
	fun `missing token is a problem body with the bearer challenge`() {
		val request = MockHttpServletRequest("GET", "/api/v1/decks")
		val response = MockHttpServletResponse()

		entryPoint.commence(request, response, InsufficientAuthenticationException("no token"))

		assertEquals(401, response.status)
		assertEquals("application/problem+json", response.contentType)
		assertTrue(response.getHeader("WWW-Authenticate").orEmpty().startsWith("Bearer"))
		val body = json.readTree(response.contentAsString)
		assertEquals(401, body.path("status").asInt())
		assertEquals("A bearer token is required.", body.path("detail").asString())
		assertEquals("/api/v1/decks", body.path("instance").asString())
	}

	/** A request that sent a token is told the token itself was the problem. */
	@Test
	fun `rejected token says it is invalid or expired`() {
		val request = MockHttpServletRequest("GET", "/api/v1/decks").apply { addHeader("Authorization", "Bearer expired") }
		val response = MockHttpServletResponse()

		entryPoint.commence(request, response, InsufficientAuthenticationException("bad token"))

		assertEquals("The bearer token is invalid or expired.", json.readTree(response.contentAsString).path("detail").asString())
	}
}
