package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.error.ServiceUnavailableException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
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

	/** Deletes a Supabase Auth user so the database foreign-key cascade removes app data. */
	override fun deleteUser(authUserId: UUID) {
		if (adminKey.isBlank()) {
			throw ServiceUnavailableException("Account deletion is not configured on this server.")
		}
		try {
			client.delete()
				.uri("$issuer/admin/users/$authUserId")
				.header("apikey", adminKey)
				.header("Authorization", "Bearer $adminKey")
				.retrieve()
				.toBodilessEntity()
		} catch (exception: RestClientException) {
			throw ServiceUnavailableException("Supabase Auth could not delete the account.")
		}
	}
}

/** No-op Auth client for the isolated mock profile; repository cleanup is exercised by route tests. */
@Component
@Profile("mock")
class MockSupabaseAuthAdminClient : SupabaseAuthAdminClient {
	/** Accepts the mock identity deletion without contacting Supabase. */
	override fun deleteUser(authUserId: UUID) = Unit
}
