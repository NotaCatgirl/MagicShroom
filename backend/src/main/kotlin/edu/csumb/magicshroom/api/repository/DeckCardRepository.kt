package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.DeckCard

/** Storage for the cards inside decks, keyed by (deckId, cardId). */
interface DeckCardRepository {
	/** The cards in deck [deckId], ordered by card id. */
	fun findByDeckId(deckId: Long): List<DeckCard>

	/** The entry for [cardId] in [deckId], or null. */
	fun find(deckId: Long, cardId: Long): DeckCard?

	/** True if any deck contains [cardId]. */
	fun existsByCardId(cardId: Long): Boolean

	/** Inserts or overwrites the entry for (deckId, cardId) and returns it. */
	fun save(deckCard: DeckCard): DeckCard

	/** Removes [cardId] from [deckId]. Returns false if it was not there. */
	fun delete(deckId: Long, cardId: Long): Boolean

	/** Removes every card from [deckId]. */
	fun deleteByDeckId(deckId: Long)
}
