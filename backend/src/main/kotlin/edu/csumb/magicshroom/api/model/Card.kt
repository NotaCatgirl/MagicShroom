package edu.csumb.magicshroom.api.model

import java.time.LocalDate
import java.util.UUID

/** Magic color identity code. [C] means colorless. */
enum class Color { W, U, B, R, G, C }

/** Whether a card may be played in a format. */
enum class LegalityStatus { LEGAL, NOT_LEGAL, RESTRICTED, BANNED }

/** A card in the catalog, shaped like the `Card` schema in docs/openapi.yaml. */
data class Card(
	val id: Long,
	val uuid: UUID,
	val cardName: String,
	val colorIdentity: List<Color>,
	val colors: List<Color>,
	val convertedManaCost: Double? = null,
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
	/** True if this card's color identity includes [color]. Colorless ([Color.C]) matches an empty identity. */
	fun hasColorIdentity(color: Color): Boolean =
		color in colorIdentity || (color == Color.C && colorIdentity.isEmpty())
}

/** A card's name and text in another language. */
data class ForeignCardData(val language: String, val name: String, val text: String? = null)

/** A card's legality in one format, e.g. `modern` → [LegalityStatus.LEGAL]. */
data class CardLegality(val format: String, val status: LegalityStatus)

/** Where to buy a card. */
data class PurchaseUrl(val vendor: String, val url: String)

/** An official rules clarification for a card. */
data class Ruling(val date: LocalDate? = null, val text: String)
