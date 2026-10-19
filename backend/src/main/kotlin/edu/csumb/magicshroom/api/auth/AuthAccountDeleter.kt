package edu.csumb.magicshroom.api.auth

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

/**
 * Deletes users from Supabase Auth through its admin API, so a deleted account is not re-created
 * on the user's next request. The `auth.users` row cascades to `app_users`, `decks` and `deck_cards`.
 *
 * Needs a server-only Supabase secret key (`SUPABASE_SECRET_KEY`). Without one, [delete] only logs a
 * warning: the caller still deletes the app data, but the person can sign in again to an empty account.
 */
@Component
class AuthAccountDeleter internal constructor(
	private val issuer: String,
	private val secretKey: String,
	private val restClient: RestClient,
) {
	/** Spring constructor: reads the Auth issuer and the optional secret key from configuration. */
	@Autowired
	constructor(
		@Value("\${magicshroom.auth.issuer:}") issuer: String,
		@Value("\${magicshroom.auth.secret-key:}") secretKey: String,
	) : this(issuer, secretKey, RestClient.create())

	private val log = LoggerFactory.getLogger(javaClass)

	/** True when a secret key is configured, so Auth users are really deleted. */
	val enabled: Boolean get() = issuer.isNotBlank() && secretKey.isNotBlank()

	/** Deletes Supabase Auth user [authUserId]. A user that is already gone counts as deleted. */
	fun delete(authUserId: UUID) {
		if (!enabled) {
			log.warn("SUPABASE_SECRET_KEY is not set: Auth user {} was not deleted and can sign in again.", authUserId)
			return
		}
		restClient.delete()
			.uri("$issuer/admin/users/{id}", authUserId)
			// New sb_secret_ keys go in the apikey header only; the legacy service_role key is a JWT
			// and is also sent as the bearer token.
			.header("apikey", secretKey)
			.headers { if (secretKey.startsWith("eyJ")) it.setBearerAuth(secretKey) }
			.retrieve()
			.onStatus({ it.value() == 404 }) { _, _ -> }
			.toBodilessEntity()
	}
}
