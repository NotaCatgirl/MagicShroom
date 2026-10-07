package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Deck

/** Storage for decks. Ownership checks happen in the service layer, not here. */
interface DeckRepository {
	/** All decks owned by [userId], ordered by id. */
	fun findByUserId(userId: Long): List<Deck>

	/** The deck with [id], or null. */
	fun findById(id: Long): Deck?

	/** Stores a new deck under a newly assigned id (the id on [deck] is ignored) and returns it. */
	fun insert(deck: Deck): Deck

	/** Overwrites the stored deck that has the same id and returns it. */
	fun update(deck: Deck): Deck

	/** Deletes the deck with [id]. Returns false if there was none. Does not touch its cards. */
	fun deleteById(id: Long): Boolean
}
