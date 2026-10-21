package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.CardBatchRequestDto
import edu.csumb.magicshroom.data.api.CardPageDto
import edu.csumb.magicshroom.data.api.Color
import edu.csumb.magicshroom.data.api.MagicShroomApi
import java.util.concurrent.ConcurrentHashMap

/** Card catalog access for ViewModels. Never throws for HTTP or network failures; see [ApiResult]. */
class CardRepository(private val api: MagicShroomApi) {
    private val cardsById = ConcurrentHashMap<Long, CardDto>()

    /** One page of cards whose name contains [name] and whose color identity includes [color]. */
    suspend fun listCards(
        name: String? = null,
        color: Color? = null,
        page: Int = 0,
        size: Int = 20,
    ): ApiResult<CardPageDto> {
        val result = apiCall { api.listCards(name = name, color = color, page = page, size = size) }
        if (result is ApiResult.Success) remember(result.data.content)
        return result
    }

    /** One card by id. */
    suspend fun getCard(cardId: Long): ApiResult<CardDto> = cardsById[cardId]?.let { ApiResult.Success(it) }
        ?: apiCall { api.getCard(cardId) }.also { result ->
            if (result is ApiResult.Success) remember(listOf(result.data))
        }

    /** Returns unique requested cards in order, making one batch request for uncached IDs. */
    suspend fun getCardsByIds(cardIds: Collection<Long>): ApiResult<List<CardDto>> {
        val uniqueIds = cardIds.distinct()
        if (uniqueIds.isEmpty()) return ApiResult.Success(emptyList())

        val missingIds = uniqueIds.filterNot(cardsById::containsKey)
        for (batchIds in missingIds.chunked(MAX_BATCH_SIZE)) {
            val result = apiCall { api.getCardsBatch(CardBatchRequestDto(batchIds)) }
            if (result !is ApiResult.Success) return result
            remember(result.data)
        }

        val resolved = uniqueIds.mapNotNull(cardsById::get)
        if (resolved.size != uniqueIds.size) {
            return ApiResult.NetworkError(IllegalStateException("Batch response omitted one or more requested cards."))
        }
        return ApiResult.Success(resolved)
    }

    /** Stores full card records in the process-wide repository cache by API ID. */
    private fun remember(cards: Iterable<CardDto>) {
        cards.forEach { cardsById[it.id] = it }
    }

    private companion object {
        const val MAX_BATCH_SIZE = 100
    }
}
