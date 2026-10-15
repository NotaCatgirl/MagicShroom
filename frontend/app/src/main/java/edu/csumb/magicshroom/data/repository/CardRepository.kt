package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.CardPageDto
import edu.csumb.magicshroom.data.api.Color
import edu.csumb.magicshroom.data.api.MagicShroomApi

/** Card catalog access for ViewModels. Never throws for HTTP or network failures; see [ApiResult]. */
class CardRepository(private val api: MagicShroomApi) {

    /** One page of cards whose name contains [name] and whose color identity includes [color]. */
    suspend fun listCards(
        name: String? = null,
        color: Color? = null,
        page: Int = 0,
        size: Int = 20,
    ): ApiResult<CardPageDto> = apiCall { api.listCards(name = name, color = color, page = page, size = size) }

    /** One card by id. */
    suspend fun getCard(cardId: Long): ApiResult<CardDto> = apiCall { api.getCard(cardId) }
}
