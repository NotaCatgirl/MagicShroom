package edu.csumb.magicshroom.data.api

import kotlinx.serialization.Serializable

/** Magic color identity code; [C] means colorless (`Color` in docs/openapi.yaml). */
@Serializable
enum class Color { W, U, B, R, G, C }

/** Whether a card may be played in a format. */
@Serializable
enum class LegalityStatus { LEGAL, NOT_LEGAL, RESTRICTED, BANNED }

/** A card as the API returns it (`Card`). */
@Serializable
data class CardDto(
    val id: Long,
    val uuid: String,
    val cardName: String,
    val colorIdentity: List<Color> = emptyList(),
    val colors: List<Color> = emptyList(),
    val convertedManaCost: Double? = null,
    val manaCost: String? = null,
    val scryfallOracleId: String? = null,
    val text: String? = null,
    val types: List<String> = emptyList(),
    val subtypes: List<String> = emptyList(),
    val supertypes: List<String> = emptyList(),
    val printings: List<String> = emptyList(),
    val foreignData: List<ForeignCardDataDto> = emptyList(),
    val legalities: List<CardLegalityDto> = emptyList(),
    val purchaseUrls: List<PurchaseUrlDto> = emptyList(),
    val rulings: List<RulingDto> = emptyList(),
    val imageFaces: List<CardImageFaceDto> = emptyList(),
)

/** A full-card scan from the representative printing, in display order. */
@Serializable
data class CardImageFaceDto(val name: String, val smallUrl: String, val normalUrl: String)

/** A card's name and text in another language. */
@Serializable
data class ForeignCardDataDto(val language: String, val name: String, val text: String? = null)

/** A card's legality in one format. */
@Serializable
data class CardLegalityDto(val format: String, val status: LegalityStatus)

/** Where to buy a card. */
@Serializable
data class PurchaseUrlDto(val vendor: String, val url: String)

/** An official rules clarification; [date] is `yyyy-MM-dd`. */
@Serializable
data class RulingDto(val date: String? = null, val text: String)

/** Body of POST and PUT /api/v1/cards (`CardInput`). Admin only. */
@Serializable
data class CardInputDto(
    val uuid: String,
    val cardName: String,
    val colorIdentity: List<Color>,
    val colors: List<Color>,
    val types: List<String>,
    val convertedManaCost: Double? = null,
    val manaCost: String? = null,
    val scryfallOracleId: String? = null,
    val text: String? = null,
    val subtypes: List<String> = emptyList(),
    val supertypes: List<String> = emptyList(),
    val printings: List<String> = emptyList(),
    val foreignData: List<ForeignCardDataDto> = emptyList(),
    val legalities: List<CardLegalityDto> = emptyList(),
    val purchaseUrls: List<PurchaseUrlDto> = emptyList(),
    val rulings: List<RulingDto> = emptyList(),
)

/** Body of PATCH /api/v1/cards/{cardId} (`CardPatch`). Null fields are left out of the request. */
@Serializable
data class CardPatchDto(
    val uuid: String? = null,
    val cardName: String? = null,
    val colorIdentity: List<Color>? = null,
    val colors: List<Color>? = null,
    val convertedManaCost: Double? = null,
    val manaCost: String? = null,
    val scryfallOracleId: String? = null,
    val text: String? = null,
    val types: List<String>? = null,
    val subtypes: List<String>? = null,
    val supertypes: List<String>? = null,
    val printings: List<String>? = null,
    val foreignData: List<ForeignCardDataDto>? = null,
    val legalities: List<CardLegalityDto>? = null,
    val purchaseUrls: List<PurchaseUrlDto>? = null,
    val rulings: List<RulingDto>? = null,
)

/** Paging information for a collection response (`PageMeta`). */
@Serializable
data class PageMetaDto(val size: Int, val number: Int, val totalElements: Long, val totalPages: Int)

/** One page of cards (`CardPage`). */
@Serializable
data class CardPageDto(val content: List<CardDto>, val page: PageMetaDto)
