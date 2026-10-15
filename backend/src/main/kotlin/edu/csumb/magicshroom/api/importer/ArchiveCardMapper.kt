package edu.csumb.magicshroom.api.importer

import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.CardLegality
import edu.csumb.magicshroom.api.model.Color
import edu.csumb.magicshroom.api.model.ForeignCardData
import edu.csumb.magicshroom.api.model.LegalityStatus
import edu.csumb.magicshroom.api.model.PurchaseUrl
import edu.csumb.magicshroom.api.model.Ruling
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID

/** Maps unchanged MTGJSON archive entries into the stable card API model. */
internal object ArchiveCardMapper {
    /** Maps one archive card, preserving the existing API's scalar and detail response fields. */
    fun map(node: JsonNode): Card = with(node) {
        Card(
            id = 0, uuid = UUID.fromString(path("uuid").asString()),
            cardName = path("name").asString(),
            colorIdentity = path("colorIdentity").toList().map { Color.valueOf(it.asString()) },
            colors = path("colors").toList().map { Color.valueOf(it.asString()) },
            convertedManaCost = path("convertedManaCost").takeUnless { it.isMissingNode || it.isNull }?.asDouble(),
            manaCost = path("manaCost").textOrNull(),
            scryfallOracleId = path("scryfallOracleId").textOrNull()?.let(UUID::fromString),
            text = path("text").textOrNull(),
            types = path("types").toList().map { it.asString() },
            subtypes = path("subtypes").toList().map { it.asString() },
            supertypes = path("supertypes").toList().map { it.asString() },
            printings = path("printings").toList().map { it.asString() },
            foreignData = path("foreignData").toList().map {
                ForeignCardData(it.path("language").asString(), it.path("name").asString(), it.path("text").textOrNull())
            },
            legalities = path("legalities").properties().map { (format, status) ->
                CardLegality(format, when (status.asString().lowercase()) {
                    "legal" -> LegalityStatus.LEGAL
                    "not legal" -> LegalityStatus.NOT_LEGAL
                    "restricted" -> LegalityStatus.RESTRICTED
                    "banned" -> LegalityStatus.BANNED
                    else -> error("Unknown legality for $format: $status")
                })
            },
            purchaseUrls = path("purchaseUrls").properties().map { (vendor, url) -> PurchaseUrl(vendor, url.asString()) },
            rulings = path("rulings").toList().map {
                Ruling(it.path("date").textOrNull()?.let(LocalDate::parse), it.path("text").asString())
            },
        )
    }

    /** Returns null for an absent or JSON null archive field. */
    private fun JsonNode.textOrNull(): String? = takeUnless { it.isMissingNode || it.isNull }?.asString()
}
