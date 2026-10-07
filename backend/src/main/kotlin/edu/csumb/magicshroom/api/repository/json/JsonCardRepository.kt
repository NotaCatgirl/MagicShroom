package edu.csumb.magicshroom.api.repository.json

import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.repository.CardRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** [CardRepository] backed by `mock-data/cards.json`. Changes live in memory until restart. */
@Repository
@Profile("mock")
class JsonCardRepository(jsonMapper: JsonMapper) : CardRepository {
	private val cards = ConcurrentHashMap<Long, Card>()
	private val lastId: AtomicLong

	init {
		jsonMapper.readMockData<Card>("cards.json").forEach { cards[it.id] = it }
		lastId = AtomicLong(cards.keys.maxOrNull() ?: 0)
	}

	override fun findAll(): List<Card> = cards.values.sortedBy { it.id }

	override fun findById(id: Long): Card? = cards[id]

	override fun findByUuid(uuid: UUID): Card? = cards.values.firstOrNull { it.uuid == uuid }

	override fun insert(card: Card): Card =
		card.copy(id = lastId.incrementAndGet()).also { cards[it.id] = it }

	override fun update(card: Card): Card = card.also { cards[it.id] = it }

	override fun deleteById(id: Long): Boolean = cards.remove(id) != null
}
