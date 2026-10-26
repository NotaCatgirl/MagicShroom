package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.error.ServiceUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

/** Server-side operations against Supabase Auth's admin API. */
interface SupabaseAuthAdminClient {
	/** Permanently deletes the Auth identity with [authUserId]. */
	fun deleteUser(authUserId: UUID)
}

/** Calls the Supabase Auth Admin API without exposing its secret key to the Android client. */
@Component
@Profile("!mock")
class SupabaseRestAuthAdminClient(
	@Value("\${magicshroom.auth.issuer}") private val issuer: String,
	@Value("\${magicshroom.auth.admin-key:}") private val adminKey: String,
) : SupabaseAuthAdminClient {
	private val client = RestClient.create()
	private val log = LoggerFactory.getLogger(javaClass)
	private val json = JsonMapper.builder().build()

	/** Deletes a Supabase Auth user so the database foreign-key cascade removes app data. */
	override fun deleteUser(authUserId: UUID) {
		val key = adminKey.trim()
		if (key.isEmpty()) {
			throw ServiceUnavailableException("Account deletion is not configured on this server.")
		}
		try {
			client.delete()
				.uri("$issuer/admin/users/$authUserId")
				.header("apikey", key)
				// Secret keys are not JWTs; only legacy service_role JWTs belong in Authorization.
				.headers { if (key.startsWith("eyJ")) it.setBearerAuth(key) }
				.retrieve()
				.toBodilessEntity()
		} catch (exception: RestClientException) {
			// Exception messages and response bodies may contain credentials or personal data.
			if (exception is RestClientResponseException) {
				log.warn(
					"Supabase Auth account deletion failed: HTTP {}, error_code={}",
					exception.statusCode.value(),
					errorCode(exception),
				)
			} else {
				log.warn("Supabase Auth account deletion failed without a usable response: {}", exception.javaClass.simpleName)
			}
			throw ServiceUnavailableException("Supabase Auth could not delete the account.")
		}
	}

	/** Logs only a bounded error identifier, never the raw response, headers, or credentials. */
	private fun errorCode(exception: RestClientResponseException): String = runCatching {
		json.readTree(exception.responseBodyAsString).path("error_code").asString()
			.takeIf { it.matches(Regex("[a-z][a-z0-9_]{0,63}")) && !it.startsWith("sb_") && !it.contains(adminKey.trim()) }
	}.getOrNull() ?: "unknown"
}

/** No-op Auth client for the isolated mock profile; repository cleanup is exercised by route tests. */
@Component
@Profile("mock")
class MockSupabaseAuthAdminClient : SupabaseAuthAdminClient {
	/** Accepts the mock identity deletion without contacting Supabase. */
	override fun deleteUser(authUserId: UUID) = Unit
}
