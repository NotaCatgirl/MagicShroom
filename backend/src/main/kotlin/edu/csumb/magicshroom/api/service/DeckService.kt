package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.dto.DeckCardCreate
import edu.csumb.magicshroom.api.dto.DeckCardPatch
import edu.csumb.magicshroom.api.dto.DeckCardResponse
import edu.csumb.magicshroom.api.dto.DeckCreate
import edu.csumb.magicshroom.api.dto.DeckPatch
import edu.csumb.magicshroom.api.dto.DeckResponse
import edu.csumb.magicshroom.api.error.BadRequestException
import edu.csumb.magicshroom.api.error.ConflictException
import edu.csumb.magicshroom.api.error.NotFoundException
import edu.csumb.magicshroom.api.model.Deck
import edu.csumb.magicshroom.api.model.DeckCard
import edu.csumb.magicshroom.api.repository.CardRepository
import edu.csumb.magicshroom.api.repository.DeckCardRepository
import edu.csumb.magicshroom.api.repository.DeckRepository
import org.springframework.stereotype.Service

/**
 * The signed-in user's decks and the cards in them. A deck that belongs to someone else is
 * reported as 404, never 403, so callers cannot discover which deck ids exist.
 */
@Service
class DeckService(
	private val deckRepository: DeckRepository,
	private val deckCardRepository: DeckCardRepository,
	private val cardRepository: CardRepository,
) {
	/** [user]'s decks, optionally only those whose name contains [name] (ignoring case). */
	fun list(user: CurrentUser, name: String?): List<DeckResponse> =
		deckRepository.findByUserId(user.id)
			.filter { name == null || it.name.contains(name.trim(), ignoreCase = true) }
			.map { it.toResponse() }

	/** One of [user]'s decks with its cards. */
	fun get(user: CurrentUser, deckId: Long): DeckResponse = ownedDeck(user, deckId).toResponse()

	/** Creates an empty deck owned by [user]. */
	fun create(user: CurrentUser, request: DeckCreate): DeckResponse =
		deckRepository.insert(Deck(id = 0, userId = user.id, name = request.name.trim())).toResponse()

	/** Renames one of [user]'s decks. */
	fun rename(user: CurrentUser, deckId: Long, patch: DeckPatch): DeckResponse {
		val deck = ownedDeck(user, deckId)
		return deckRepository.update(deck.copy(name = patch.name.trim())).toResponse()
	}

	/** Deletes one of [user]'s decks and every card entry in it. */
	fun delete(user: CurrentUser, deckId: Long) {
		ownedDeck(user, deckId)
		deckCardRepository.deleteByDeckId(deckId)
		deckRepository.deleteById(deckId)
	}

	/** Adds a card to one of [user]'s decks. 404 if the card does not exist; 409 if it is already in the deck. */
	fun addCard(user: CurrentUser, deckId: Long, request: DeckCardCreate): DeckCardResponse {
		ownedDeck(user, deckId)
		cardRepository.findById(request.cardId) ?: throw NotFoundException("Card ${request.cardId} was not found.")
		if (deckCardRepository.find(deckId, request.cardId) != null) {
			throw ConflictException("Card ${request.cardId} is already in deck $deckId; PATCH it to change the quantity.")
		}
		val saved = deckCardRepository.save(DeckCard(deckId, request.cardId, request.quantity, request.section))
		return DeckCardResponse.from(saved)
	}

	/** Changes the quantity and/or section of a card in one of [user]'s decks. */
	fun updateCard(user: CurrentUser, deckId: Long, cardId: Long, patch: DeckCardPatch): DeckCardResponse {
		if (patch.isEmpty()) throw BadRequestException("Send quantity, section, or both.")
		val entry = deckCard(user, deckId, cardId)
		val saved = deckCardRepository.save(
			entry.copy(quantity = patch.quantity ?: entry.quantity, section = patch.section ?: entry.section),
		)
		return DeckCardResponse.from(saved)
	}

	/** Removes a card from one of [user]'s decks. */
	fun removeCard(user: CurrentUser, deckId: Long, cardId: Long) {
		deckCard(user, deckId, cardId)
		deckCardRepository.delete(deckId, cardId)
	}

	private fun ownedDeck(user: CurrentUser, deckId: Long): Deck =
		deckRepository.findById(deckId)?.takeIf { it.userId == user.id }
			?: throw NotFoundException("Deck $deckId was not found.")

	private fun deckCard(user: CurrentUser, deckId: Long, cardId: Long): DeckCard {
		ownedDeck(user, deckId)
		return deckCardRepository.find(deckId, cardId)
			?: throw NotFoundException("Card $cardId is not in deck $deckId.")
	}

	private fun Deck.toResponse() =
		DeckResponse(id, userId, name, deckCardRepository.findByDeckId(id).map(DeckCardResponse::from))
}
