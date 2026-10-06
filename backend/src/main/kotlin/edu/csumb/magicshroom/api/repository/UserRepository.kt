package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.User

/** Storage for user accounts. */
interface UserRepository {
	/** The user with [id], or null. */
	fun findById(id: Long): User?

	/** Deletes the user with [id]. Returns false if there was none. Does not touch their decks. */
	fun deleteById(id: Long): Boolean
}
