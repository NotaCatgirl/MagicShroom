package edu.csumb.magicshroom.api.repository.json

import edu.csumb.magicshroom.api.model.Deck
import edu.csumb.magicshroom.api.repository.DeckRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** [DeckRepository] backed by `mock-data/decks.json`. Changes live in memory until restart. */
@Repository
@Profile("mock")
class JsonDeckRepository(jsonMapper: JsonMapper) : DeckRepository {
	private val decks = ConcurrentHashMap<Long, Deck>()
	private val lastId: AtomicLong

	init {
		jsonMapper.readMockData<Deck>("decks.json").forEach { decks[it.id] = it }
		lastId = AtomicLong(decks.keys.maxOrNull() ?: 0)
	}

	override fun findByUserId(userId: Long): List<Deck> =
		decks.values.filter { it.userId == userId }.sortedBy { it.id }

	override fun findById(id: Long): Deck? = decks[id]

	override fun insert(deck: Deck): Deck =
		deck.copy(id = lastId.incrementAndGet()).also { decks[it.id] = it }

	override fun update(deck: Deck): Deck = deck.also { decks[it.id] = it }

	override fun deleteById(id: Long): Boolean = decks.remove(id) != null
}
