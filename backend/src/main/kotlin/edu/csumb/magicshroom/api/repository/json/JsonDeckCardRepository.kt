package edu.csumb.magicshroom.api.repository.json

import edu.csumb.magicshroom.api.model.DeckCard
import edu.csumb.magicshroom.api.repository.DeckCardRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentHashMap

/** [DeckCardRepository] backed by `mock-data/deck_cards.json`. Changes live in memory until restart. */
@Repository
@Profile("mock")
class JsonDeckCardRepository(jsonMapper: JsonMapper) : DeckCardRepository {
	private data class Key(val deckId: Long, val cardId: Long)

	private val entries = ConcurrentHashMap<Key, DeckCard>()

	init {
		jsonMapper.readMockData<DeckCard>("deck_cards.json").forEach { entries[it.key()] = it }
	}

	override fun findByDeckId(deckId: Long): List<DeckCard> =
		entries.values.filter { it.deckId == deckId }.sortedBy { it.cardId }

	override fun find(deckId: Long, cardId: Long): DeckCard? = entries[Key(deckId, cardId)]

	override fun existsByCardId(cardId: Long): Boolean = entries.values.any { it.cardId == cardId }

	override fun save(deckCard: DeckCard): DeckCard = deckCard.also { entries[it.key()] = it }

	override fun delete(deckId: Long, cardId: Long): Boolean = entries.remove(Key(deckId, cardId)) != null

	override fun deleteByDeckId(deckId: Long) {
		entries.keys.removeIf { it.deckId == deckId }
	}

	private fun DeckCard.key() = Key(deckId, cardId)
}
