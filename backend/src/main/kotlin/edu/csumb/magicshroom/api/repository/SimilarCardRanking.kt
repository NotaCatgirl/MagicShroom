package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Card
import kotlin.math.abs

/**
 * The order `GET /api/v1/cards/{cardId}/similar` returns. `PostgresCardRepository.findSimilar` applies
 * the same rules in SQL; the shared `SimilarCardFixtures` test data keeps the two in step.
 */
object SimilarCardRanking {
	/**
	 * Up to [limit] of [candidates] that share a main type with [base] and are not another face of the
	 * same card (same Scryfall oracle id), best match first: the same color
	 * identity before sharing only some colors, then closest mana value (missing counts as 0), most
	 * shared subtypes, most popular (lowest EDHREC rank, unranked last), and lowest id.
	 */
	fun rank(base: Card, candidates: Collection<Card>, limit: Int): List<Card> {
		val baseColors = base.colorIdentity.toSet()
		val baseSubtypes = base.subtypes.toSet()
		val baseManaValue = base.convertedManaCost ?: 0.0

		// 0 for the same color identity, 1 for at least one shared color, null when none is shared.
		fun tier(card: Card): Int? = when {
			card.colorIdentity.toSet() == baseColors -> 0
			card.colorIdentity.any(baseColors::contains) -> 1
			else -> null
		}

		return candidates
			.filter {
				it.id != base.id && (base.scryfallOracleId == null || it.scryfallOracleId != base.scryfallOracleId) && it.types.any(base.types::contains) && tier(it) != null
			}
			.sortedWith(
				compareBy<Card> { tier(it) }
					.thenBy { abs((it.convertedManaCost ?: 0.0) - baseManaValue) }
					.thenByDescending { (it.subtypes.toSet() intersect baseSubtypes).size }
					.thenBy(nullsLast<Int>()) { it.edhrecRank }
					.thenBy { it.id },
			)
			.take(limit)
	}
}
