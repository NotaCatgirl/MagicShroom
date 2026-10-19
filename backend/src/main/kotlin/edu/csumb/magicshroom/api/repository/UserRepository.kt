package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.model.Role
import edu.csumb.magicshroom.api.model.User
import java.util.UUID

/** Storage for user accounts. */
interface UserRepository {
	/** The user with [id], or null. */
	fun findById(id: Long): User?

	/** One page of users ordered by id, optionally only those with [role]. */
	fun findPage(role: Role?, page: Int, size: Int): PageResponse<User>

	/** How many users have [role]. */
	fun countByRole(role: Role): Long

	/** Sets the role of user [id] and returns the updated user, or null if there is none. */
	fun updateRole(id: Long, role: Role): User?

	/** The Supabase Auth user linked to [id], or null when there is none (always null for mock users). */
	fun findAuthUserId(id: Long): UUID? = null

	/** Finds or creates the application account linked to a validated Supabase Auth subject. */
	fun findOrCreate(authUserId: UUID, email: String, displayName: String): User =
		error("Auth account creation is unavailable in the mock profile")

	/** Deletes the user with [id]. Returns false if there was none. Does not touch their decks. */
	fun deleteById(id: Long): Boolean
}
