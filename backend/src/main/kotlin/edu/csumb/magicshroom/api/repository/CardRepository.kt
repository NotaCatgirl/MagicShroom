package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Card
import java.util.UUID

/** Storage for the card catalog. Services depend on this interface, never on an implementation. */
interface CardRepository {
	/** Every card, ordered by id. */
	fun findAll(): List<Card>

	/** The card with [id], or null. */
	fun findById(id: Long): Card?

	/** The card with this MTGJSON [uuid], or null. */
	fun findByUuid(uuid: UUID): Card?

	/** Stores a new card under a newly assigned id (the id on [card] is ignored) and returns it. */
	fun insert(card: Card): Card

	/** Overwrites the stored card that has the same id and returns it. */
	fun update(card: Card): Card

	/** Deletes the card with [id]. Returns false if there was none. */
	fun deleteById(id: Long): Boolean
}
