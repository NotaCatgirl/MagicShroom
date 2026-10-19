package edu.csumb.magicshroom.api.auth

import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import java.util.UUID
import kotlin.test.assertFalse

/** Checks the exact Supabase Auth admin request, since it cannot be exercised without a real key. */
class AuthAccountDeleterTest {
	private val issuer = "https://ref.supabase.co/auth/v1"
	private val userId = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e")
	private val builder = RestClient.builder()
	private val server = MockRestServiceServer.bindTo(builder).build()

	/** New sb_secret_ keys go in the apikey header only. */
	@Test
	fun `secret key is sent as apikey without a bearer token`() {
		server.expect(requestTo("$issuer/admin/users/$userId"))
			.andExpect(method(HttpMethod.DELETE))
			.andExpect(header("apikey", "sb_secret_test"))
			.andExpect(headerDoesNotExist("Authorization"))
			.andRespond(withStatus(HttpStatus.OK))

		AuthAccountDeleter(issuer, "sb_secret_test", builder.build()).delete(userId)

		server.verify()
	}

	/** The legacy service_role key is a JWT and is also sent as the bearer token. */
	@Test
	fun `legacy service role key is also sent as bearer`() {
		server.expect(requestTo("$issuer/admin/users/$userId"))
			.andExpect(header("apikey", "eyJlegacy"))
			.andExpect(header("Authorization", "Bearer eyJlegacy"))
			.andRespond(withStatus(HttpStatus.OK))

		AuthAccountDeleter(issuer, "eyJlegacy", builder.build()).delete(userId)

		server.verify()
	}

	/** A user Supabase no longer has is already deleted, not an error. */
	@Test
	fun `missing auth user is treated as deleted`() {
		server.expect(requestTo("$issuer/admin/users/$userId")).andRespond(withStatus(HttpStatus.NOT_FOUND))

		AuthAccountDeleter(issuer, "sb_secret_test", builder.build()).delete(userId)

		server.verify()
	}

	/** Without a key nothing is sent; the caller still deletes the app data. */
	@Test
	fun `without a key no request is made`() {
		val deleter = AuthAccountDeleter(issuer, "", builder.build())

		deleter.delete(userId)

		assertFalse(deleter.enabled)
		server.verify()
	}
}
