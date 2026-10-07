package edu.csumb.magicshroom.api.dto

import edu.csumb.magicshroom.api.model.DeckCard
import edu.csumb.magicshroom.api.model.DeckSection
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Body of POST /api/v1/decks (`DeckCreate`). */
data class DeckCreate(@field:NotBlank @field:Size(max = 100) val name: String)

/** Body of PATCH /api/v1/decks/{deckId} (`DeckPatch`): the new name. */
data class DeckPatch(@field:NotBlank @field:Size(max = 100) val name: String)

/** Body of POST /api/v1/decks/{deckId}/cards (`DeckCardCreate`). */
data class DeckCardCreate(
	@field:Min(1) val cardId: Long,
	@field:Min(1) val quantity: Int = 1,
	val section: DeckSection = DeckSection.MAINBOARD,
)

/** Body of PATCH /api/v1/decks/{deckId}/cards/{cardId} (`DeckCardPatch`). Send at least one field. */
data class DeckCardPatch(
	@field:Min(1) val quantity: Int? = null,
	val section: DeckSection? = null,
) {
	/** True if the client sent no fields. */
	fun isEmpty(): Boolean = quantity == null && section == null
}

/** A deck and its cards (`Deck` in the contract). */
data class DeckResponse(val id: Long, val userId: Long, val name: String, val cards: List<DeckCardResponse>)

/** One card in a deck (`DeckCard` in the contract). */
data class DeckCardResponse(val cardId: Long, val quantity: Int, val section: DeckSection) {
	companion object {
		/** The API view of a stored [DeckCard]. */
		fun from(deckCard: DeckCard) = DeckCardResponse(deckCard.cardId, deckCard.quantity, deckCard.section)
	}
}
