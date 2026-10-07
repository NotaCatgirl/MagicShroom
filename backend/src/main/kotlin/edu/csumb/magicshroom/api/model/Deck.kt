package edu.csumb.magicshroom.api.model

/** Where a card sits inside a deck. */
enum class DeckSection { MAINBOARD, SIDEBOARD, COMMANDER, MAYBEBOARD }

/** A named deck owned by one user. Its cards are stored separately as [DeckCard] rows. */
data class Deck(
	val id: Long,
	val userId: Long,
	val name: String,
)

/** One card in one deck. A card appears at most once per deck; [quantity] counts the copies. */
data class DeckCard(
	val deckId: Long,
	val cardId: Long,
	val quantity: Int,
	val section: DeckSection,
)
