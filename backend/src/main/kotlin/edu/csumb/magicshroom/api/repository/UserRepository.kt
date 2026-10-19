package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.User
import java.util.UUID

/** Storage for user accounts. */
interface UserRepository {
	/** The user with [id], or null. */
	fun findById(id: Long): User?

	/** The Supabase Auth UUID linked to [id], or null. */
	fun findAuthUserIdById(id: Long): UUID?

	/** Finds or creates the application account linked to a validated Supabase Auth subject. */
	fun findOrCreate(authUserId: UUID, email: String, displayName: String): User =
		error("Auth account creation is unavailable in the mock profile")

	/** Deletes the user with [id]. Returns false if there was none. */
	fun deleteById(id: Long): Boolean
}
