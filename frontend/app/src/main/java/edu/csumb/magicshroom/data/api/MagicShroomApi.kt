package edu.csumb.magicshroom.data.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Every route in docs/openapi.yaml. Functions throw `retrofit2.HttpException` on an error status
 * and `IOException` when the server can't be reached; call them through a repository, which turns
 * both into an [edu.csumb.magicshroom.data.repository.ApiResult]. Null query parameters are omitted.
 */
interface MagicShroomApi {

    /** One page of cards, optionally filtered. [sort] is e.g. `cardName,asc`. Public. */
    @GET("api/v1/cards")
    suspend fun listCards(
        @Query("name") name: String? = null,
        @Query("color") color: Color? = null,
        @Query("sort") sort: String? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): CardPageDto

    /** One card. Public. */
    @GET("api/v1/cards/{cardId}")
    suspend fun getCard(@Path("cardId") cardId: Long): CardDto

    /** Fetches up to one hundred full card records in one public request. */
    @POST("api/v1/cards/batch")
    suspend fun getCardsBatch(@Body request: CardBatchRequestDto): List<CardDto>

    /** Creates a card. Admin only. */
    @POST("api/v1/cards")
    suspend fun createCard(@Body card: CardInputDto): CardDto

    /** Replaces a card completely. Admin only. */
    @PUT("api/v1/cards/{cardId}")
    suspend fun replaceCard(@Path("cardId") cardId: Long, @Body card: CardInputDto): CardDto

    /** Changes some fields of a card. Admin only. */
    @PATCH("api/v1/cards/{cardId}")
    suspend fun updateCard(@Path("cardId") cardId: Long, @Body patch: CardPatchDto): CardDto

    /** Deletes a card no deck uses. Admin only. */
    @DELETE("api/v1/cards/{cardId}")
    suspend fun deleteCard(@Path("cardId") cardId: Long)

    /** The signed-in user's decks, optionally filtered by name. */
    @GET("api/v1/decks")
    suspend fun listMyDecks(@Query("name") name: String? = null): List<DeckDto>

    /** Creates an empty deck. */
    @POST("api/v1/decks")
    suspend fun createDeck(@Body deck: DeckCreateDto): DeckDto

    /** One of my decks with its cards. */
    @GET("api/v1/decks/{deckId}")
    suspend fun getMyDeck(@Path("deckId") deckId: Long): DeckDto

    /** Renames one of my decks. */
    @PATCH("api/v1/decks/{deckId}")
    suspend fun renameMyDeck(@Path("deckId") deckId: Long, @Body patch: DeckPatchDto): DeckDto

    /** Deletes one of my decks. */
    @DELETE("api/v1/decks/{deckId}")
    suspend fun deleteMyDeck(@Path("deckId") deckId: Long)

    /** Adds a card to one of my decks. */
    @POST("api/v1/decks/{deckId}/cards")
    suspend fun addCardToMyDeck(@Path("deckId") deckId: Long, @Body card: DeckCardCreateDto): DeckCardDto

    /** Changes a card's quantity or section in one of my decks. */
    @PATCH("api/v1/decks/{deckId}/cards/{cardId}")
    suspend fun updateCardInMyDeck(
        @Path("deckId") deckId: Long,
        @Path("cardId") cardId: Long,
        @Body patch: DeckCardPatchDto,
    ): DeckCardDto

    /** Removes a card from one of my decks. */
    @DELETE("api/v1/decks/{deckId}/cards/{cardId}")
    suspend fun removeCardFromMyDeck(@Path("deckId") deckId: Long, @Path("cardId") cardId: Long)

    /** The signed-in user's own account, including their role. */
    @GET("api/v1/users/me")
    suspend fun getMe(): UserDto

    /** Deletes the signed-in user's account and all their data. */
    @DELETE("api/v1/users/me")
    suspend fun deleteMe()

    /** One page of users, optionally only one role. Admin only. */
    @GET("api/v1/users")
    suspend fun listUsers(
        @Query("role") role: Role? = null,
        @Query("page") page: Int? = null,
        @Query("size") size: Int? = null,
    ): UserPageDto

    /** One user. Admin only. */
    @GET("api/v1/users/{userId}")
    suspend fun getUser(@Path("userId") userId: Long): UserDto

    /** Grants or revokes the ADMIN role. Admin only. */
    @PATCH("api/v1/users/{userId}")
    suspend fun updateUserRole(@Path("userId") userId: Long, @Body patch: UserRolePatchDto): UserDto

    /** Deletes a user and all their data. Admin only. */
    @DELETE("api/v1/users/{userId}")
    suspend fun deleteUser(@Path("userId") userId: Long)
}
