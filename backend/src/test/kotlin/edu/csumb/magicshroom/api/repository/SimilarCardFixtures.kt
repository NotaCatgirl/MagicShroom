package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.Color
import java.util.UUID

/** Cards that exercise every similar-card rule; shared by the Kotlin and PostgreSQL ranking tests. */
object SimilarCardFixtures {
	/** The card the others are ranked against. */
	const val BASE = "Base Elf"

	private val baseOracleId = UUID.nameUUIDFromBytes("Base Elf oracle".toByteArray())

	/** List order is also insertion order in the PostgreSQL test, which the id tie-break (Unranked Wolf before Second Wolf) relies on. */
	val cards: List<Card> = listOf(
		card(1, BASE, "G", "Creature", listOf("Elf", "Druid"), 2.0, oracleId = baseOracleId),
		card(2, "Base Elf Night Side", "G", "Creature", listOf("Elf", "Druid"), 2.0, rank = 1, oracleId = baseOracleId),
		card(3, "Twin Elf", "G", "Creature", listOf("Elf", "Druid"), 2.0, rank = 500, power = "2", toughness = "3"),
		card(4, "Elf Warrior", "G", "Creature", listOf("Elf", "Warrior"), 2.0, rank = 10),
		card(5, "Popular Bear", "G", "Creature", listOf("Bear"), 2.0, rank = 20),
		card(6, "Unranked Wolf", "G", "Creature", listOf("Wolf"), 2.0),
		card(7, "Second Wolf", "G", "Creature", listOf("Wolf"), 2.0),
		card(8, "Pricier Beast", "G", "Creature", emptyList(), 3.0, rank = 1),
		card(9, "Free Ooze", "G", "Creature", emptyList(), null, rank = 1),
		card(10, "Selesnya Elf", "GW", "Creature", listOf("Elf", "Druid"), 2.0, rank = 1),
		card(11, "Red Elf", "R", "Creature", listOf("Elf"), 2.0, rank = 1),
		card(12, "Green Instant", "G", "Instant", emptyList(), 2.0, rank = 1),
	)

	/**
	 * Same colors first (Selesnya Elf, sharing only green, is last); then mana value distance (a missing
	 * mana value counts as 0); then shared subtypes; then popularity, unranked last; then id. Red Elf
	 * shares no color and Green Instant shares no type, so neither appears; Base Elf Night Side is
	 * another face of the base card (same Scryfall oracle id), so it is excluded too.
	 */
	val expectedOrder = listOf(
		"Twin Elf", "Elf Warrior", "Popular Bear", "Unranked Wolf", "Second Wolf",
		"Pricier Beast", "Free Ooze", "Selesnya Elf",
	)

	private fun card(id: Long, name: String, colors: String, type: String, subtypes: List<String>, manaValue: Double?, rank: Int? = null,
		oracleId: UUID? = null, power: String? = null, toughness: String? = null): Card {
		val identity = colors.map { Color.valueOf(it.toString()) }
		return Card(
			id = id, uuid = UUID.nameUUIDFromBytes(name.toByteArray()), cardName = name,
			colorIdentity = identity, colors = identity, convertedManaCost = manaValue,
			types = listOf(type), subtypes = subtypes, edhrecRank = rank,
			scryfallOracleId = oracleId, power = power, toughness = toughness,
		)
	}
}
