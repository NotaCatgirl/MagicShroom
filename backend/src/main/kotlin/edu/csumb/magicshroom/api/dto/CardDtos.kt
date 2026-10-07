package edu.csumb.magicshroom.api.dto

import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.CardLegality
import edu.csumb.magicshroom.api.model.Color
import edu.csumb.magicshroom.api.model.ForeignCardData
import edu.csumb.magicshroom.api.model.PurchaseUrl
import edu.csumb.magicshroom.api.model.Ruling
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import java.util.UUID

/** Body of POST and PUT /api/v1/cards (`CardInput` in the contract). Non-null fields are required. */
data class CardInput(
	val uuid: UUID,
	@field:NotBlank val cardName: String,
	val colorIdentity: Set<Color>,
	val colors: Set<Color>,
	@field:PositiveOrZero val convertedManaCost: Double? = null,
	val manaCost: String? = null,
	val scryfallOracleId: UUID? = null,
	val text: String? = null,
	val types: List<String>,
	val subtypes: List<String> = emptyList(),
	val supertypes: List<String> = emptyList(),
	val printings: List<String> = emptyList(),
	val foreignData: List<ForeignCardData> = emptyList(),
	val legalities: List<CardLegality> = emptyList(),
	val purchaseUrls: List<PurchaseUrl> = emptyList(),
	val rulings: List<Ruling> = emptyList(),
) {
	/** This input as a [Card] stored under [id]. */
	fun toCard(id: Long) = Card(
		id = id,
		uuid = uuid,
		cardName = cardName.trim(),
		colorIdentity = colorIdentity.toList(),
		colors = colors.toList(),
		convertedManaCost = convertedManaCost,
		manaCost = manaCost,
		scryfallOracleId = scryfallOracleId,
		text = text,
		types = types,
		subtypes = subtypes,
		supertypes = supertypes,
		printings = printings,
		foreignData = foreignData,
		legalities = legalities,
		purchaseUrls = purchaseUrls,
		rulings = rulings,
	)
}

/** Body of PATCH /api/v1/cards/{cardId} (`CardPatch`). Absent or null fields are left unchanged. */
data class CardPatch(
	val uuid: UUID? = null,
	@field:Pattern(regexp = ".*\\S.*", message = "must not be blank") val cardName: String? = null,
	val colorIdentity: Set<Color>? = null,
	val colors: Set<Color>? = null,
	@field:PositiveOrZero val convertedManaCost: Double? = null,
	val manaCost: String? = null,
	val scryfallOracleId: UUID? = null,
	val text: String? = null,
	val types: List<String>? = null,
	val subtypes: List<String>? = null,
	val supertypes: List<String>? = null,
	val printings: List<String>? = null,
	val foreignData: List<ForeignCardData>? = null,
	val legalities: List<CardLegality>? = null,
	val purchaseUrls: List<PurchaseUrl>? = null,
	val rulings: List<Ruling>? = null,
) {
	/** True if the client sent no fields; the contract requires at least one. */
	fun isEmpty(): Boolean = this == CardPatch()

	/** [card] with every field present in this patch replaced. */
	fun applyTo(card: Card): Card = card.copy(
		uuid = uuid ?: card.uuid,
		cardName = cardName?.trim() ?: card.cardName,
		colorIdentity = colorIdentity?.toList() ?: card.colorIdentity,
		colors = colors?.toList() ?: card.colors,
		convertedManaCost = convertedManaCost ?: card.convertedManaCost,
		manaCost = manaCost ?: card.manaCost,
		scryfallOracleId = scryfallOracleId ?: card.scryfallOracleId,
		text = text ?: card.text,
		types = types ?: card.types,
		subtypes = subtypes ?: card.subtypes,
		supertypes = supertypes ?: card.supertypes,
		printings = printings ?: card.printings,
		foreignData = foreignData ?: card.foreignData,
		legalities = legalities ?: card.legalities,
		purchaseUrls = purchaseUrls ?: card.purchaseUrls,
		rulings = rulings ?: card.rulings,
	)
}

/** One page of a collection, shaped like Spring Data's `PagedModel` (`CardPage` in the contract). */
data class PageResponse<T>(val content: List<T>, val page: PageMeta) {
	companion object {
		/** Page [number] (zero-based) of [size] items, cut from the complete, already-sorted [items]. */
		fun <T> of(items: List<T>, number: Int, size: Int): PageResponse<T> {
			val offset = number.toLong() * size
			val content = if (offset >= items.size) emptyList() else {
				items.subList(offset.toInt(), minOf(items.size, offset.toInt() + size))
			}
			val totalPages = (items.size + size - 1) / size
			return PageResponse(content, PageMeta(size, number, items.size.toLong(), totalPages))
		}
	}
}

/** Paging information that accompanies every [PageResponse]. */
data class PageMeta(val size: Int, val number: Int, val totalElements: Long, val totalPages: Int)
