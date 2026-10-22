package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/** Pins the similar-card order that both repository implementations must return. */
class SimilarCardRankingTest {
	private val base = SimilarCardFixtures.cards.first { it.cardName == SimilarCardFixtures.BASE }

	@Test
	fun `ranks by colors, mana value, shared subtypes, popularity, then id`() {
		val ranked = SimilarCardRanking.rank(base, SimilarCardFixtures.cards, 30)
		assertEquals(SimilarCardFixtures.expectedOrder, ranked.map(Card::cardName))
	}

	@Test
	fun `returns at most limit cards`() {
		val ranked = SimilarCardRanking.rank(base, SimilarCardFixtures.cards, 3)
		assertEquals(SimilarCardFixtures.expectedOrder.take(3), ranked.map(Card::cardName))
	}

	@Test
	fun `colorless cards match only other colorless cards`() {
		val rock = Card(
			id = 1, uuid = UUID.randomUUID(), cardName = "Colorless Rock", colorIdentity = emptyList(),
			colors = emptyList(), convertedManaCost = 1.0, types = listOf("Artifact"),
		)
		val mox = rock.copy(id = 2, uuid = UUID.randomUUID(), cardName = "Colorless Mox", convertedManaCost = 0.0)
		val relic = rock.copy(id = 3, uuid = UUID.randomUUID(), cardName = "Green Relic", colorIdentity = listOf(Color.G), colors = listOf(Color.G))

		assertEquals(listOf("Colorless Mox"), SimilarCardRanking.rank(rock, listOf(rock, mox, relic), 10).map(Card::cardName))
	}
}
