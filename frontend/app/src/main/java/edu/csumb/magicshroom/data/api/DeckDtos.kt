package edu.csumb.magicshroom.data.api

import kotlinx.serialization.Serializable

/** Where a card sits inside a deck (`DeckSection`). */
@Serializable
enum class DeckSection { MAINBOARD, SIDEBOARD, COMMANDER, MAYBEBOARD }

/** One of the signed-in user's decks with its cards (`Deck`). */
@Serializable
data class DeckDto(val id: Long, val userId: Long, val name: String, val cards: List<DeckCardDto> = emptyList())

/** One card in a deck (`DeckCard`). */
@Serializable
data class DeckCardDto(val cardId: Long, val quantity: Int, val section: DeckSection)

/** Body of POST /api/v1/decks (`DeckCreate`). */
@Serializable
data class DeckCreateDto(val name: String)

/** Body of PATCH /api/v1/decks/{deckId} (`DeckPatch`). */
@Serializable
data class DeckPatchDto(val name: String)

/** Body of POST /api/v1/decks/{deckId}/cards (`DeckCardCreate`). */
@Serializable
data class DeckCardCreateDto(
    val cardId: Long,
    val quantity: Int = 1,
    val section: DeckSection = DeckSection.MAINBOARD,
)

/** Body of PATCH /api/v1/decks/{deckId}/cards/{cardId} (`DeckCardPatch`). Send at least one field. */
@Serializable
data class DeckCardPatchDto(val quantity: Int? = null, val section: DeckSection? = null)
