package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.DeckCardCreateDto
import edu.csumb.magicshroom.data.api.DeckDto
import edu.csumb.magicshroom.data.api.DeckCreateDto
import edu.csumb.magicshroom.data.api.DeckCardPatchDto
import edu.csumb.magicshroom.data.api.DeckPatchDto
import edu.csumb.magicshroom.data.api.DeckSection
import edu.csumb.magicshroom.data.api.MagicShroomApi

/** A deck entry combined with its full catalog card record. */
data class ResolvedDeckCard(val card: CardDto, val quantity: Int, val section: DeckSection)

/** A deck whose card IDs have been resolved to full card records. */
data class ResolvedDeck(val id: Long, val userId: Long, val name: String, val cards: List<ResolvedDeckCard>)

/** Loads a user's decks and resolves their cards through the shared bulk card cache. */
class DeckRepository(private val api: MagicShroomApi, private val cards: CardRepository) {

    /** Lists decks while fetching all uncached card details in one batch request. */
    suspend fun listMyDecksWithCards(): ApiResult<List<ResolvedDeck>> {
        val deckResult = apiCall { api.listMyDecks() }
        if (deckResult is ApiResult.Failure) return deckResult

        val deckDtos: List<DeckDto> = (deckResult as ApiResult.Success).data
        return resolveDecks(deckDtos)
    }

    /** Loads one deck and resolves its cards through the shared card cache. */
    suspend fun getMyDeckWithCards(deckId: Long): ApiResult<ResolvedDeck> =
        apiCall { api.getMyDeck(deckId) }.flatMap { resolveDecks(listOf(it)).map { decks -> decks.single() } }

    /** Creates a deck for the signed-in user. */
    suspend fun createDeck(name: String) = apiCall { api.createDeck(DeckCreateDto(name.trim())) }

    /** Renames one of the signed-in user's decks. */
    suspend fun renameDeck(deckId: Long, name: String) =
        apiCall { api.renameMyDeck(deckId, DeckPatchDto(name.trim())) }

    /** Deletes one of the signed-in user's decks. */
    suspend fun deleteDeck(deckId: Long): ApiResult<Unit> = apiCall { api.deleteMyDeck(deckId) }

    /** Adds a catalog card to a deck. */
    suspend fun addCard(deckId: Long, cardId: Long, quantity: Int, section: DeckSection) =
        apiCall { api.addCardToMyDeck(deckId, DeckCardCreateDto(cardId, quantity, section)) }

    /** Changes a deck card's quantity and/or section. */
    suspend fun updateCard(deckId: Long, cardId: Long, patch: DeckCardPatchDto) =
        apiCall { api.updateCardInMyDeck(deckId, cardId, patch) }

    /** Removes a card from a deck. */
    suspend fun removeCard(deckId: Long, cardId: Long): ApiResult<Unit> =
        apiCall { api.removeCardFromMyDeck(deckId, cardId) }

    /** Resolves deck card IDs to full records using the shared card cache. */
    private suspend fun resolveDecks(deckDtos: List<DeckDto>): ApiResult<List<ResolvedDeck>> {
        val cardResult = cards.getCardsByIds(deckDtos.flatMap { deck -> deck.cards.map { it.cardId } })
        if (cardResult is ApiResult.Failure) return cardResult

        val cardsById = (cardResult as ApiResult.Success).data.associateBy(CardDto::id)
        val resolved = deckDtos.map { deck ->
            ResolvedDeck(
                id = deck.id,
                userId = deck.userId,
                name = deck.name,
                cards = deck.cards.map { entry ->
                    ResolvedDeckCard(
                        card = cardsById.getValue(entry.cardId),
                        quantity = entry.quantity,
                        section = entry.section,
                    )
                },
            )
        }
        return ApiResult.Success(resolved)
    }

    /** Adds a card and returns the deck updated from the API's authoritative deck-entry response. */
    suspend fun addCardToDeck(
        deck: ResolvedDeck,
        cardId: Long,
        quantity: Int = 1,
        section: DeckSection = DeckSection.MAINBOARD,
    ): ApiResult<ResolvedDeck> {
        val cardResult = cards.getCard(cardId)
        if (cardResult is ApiResult.Failure) return cardResult
        val card = (cardResult as ApiResult.Success).data

        val addResult = apiCall {
            api.addCardToMyDeck(deck.id, DeckCardCreateDto(cardId, quantity, section))
        }
        if (addResult is ApiResult.Failure) return addResult

        val entry = (addResult as ApiResult.Success).data
        return ApiResult.Success(
            deck.copy(cards = deck.cards + ResolvedDeckCard(card, entry.quantity, entry.section)),
        )
    }
}

/** Maps a successful API result while preserving any failure. */
private inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> this
}

/** Chains a successful API result while preserving any failure. */
private inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(data)
    is ApiResult.Failure -> this
}
