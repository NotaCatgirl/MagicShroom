package edu.csumb.magicshroom.ui.compare

import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.Color
import edu.csumb.magicshroom.data.repository.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Verdicts are from the base card's point of view, per the card comparison spec. */
class CardComparisonTest {
    private val llanowar = card(1, "Llanowar Elves", subtypes = listOf("Elf", "Druid"), power = "1", toughness = "1", rank = 65)
    private val elvish = card(2, "Elvish Mystic", subtypes = listOf("Elf", "Druid"), power = "1", toughness = "1", rank = 97)
    private val serra = card(
        3, "Serra Angel", colors = listOf(Color.W), subtypes = listOf("Angel"), manaValue = 5.0,
        power = "4", toughness = "4", rank = 4698, text = "Flying, vigilance",
    )

    @Test
    fun `cheaper mana value and better popularity win, smaller body loses`() {
        val comparison = compareCards(llanowar, serra)

        assertEquals(
            listOf(
                StatRow("Mana value", "1", "5", Verdict.BETTER),
                StatRow("Power", "1", "4", Verdict.WORSE),
                StatRow("Toughness", "1", "4", Verdict.WORSE),
                StatRow("Popularity", "#65", "#4698", Verdict.BETTER),
            ),
            comparison.rows,
        )
        assertEquals(listOf("Creature"), comparison.shared)
        assertEquals(listOf("Elf", "Druid", "Green"), comparison.uniqueToBase)
        assertEquals(listOf("Flying", "Vigilance", "Angel", "White"), comparison.uniqueToOther)
        assertEquals(2, comparison.betterCount)
        assertEquals(2, comparison.worseCount)
        assertEquals(0, comparison.equalCount)
        assertEquals(7, comparison.uniqueCount)
    }

    @Test
    fun `same colors add an equal colors row and no color traits`() {
        val comparison = compareCards(llanowar, elvish)

        assertEquals(
            listOf(
                StatRow("Mana value", "1", "1", Verdict.EQUAL),
                StatRow("Power", "1", "1", Verdict.EQUAL),
                StatRow("Toughness", "1", "1", Verdict.EQUAL),
                StatRow("Popularity", "#65", "#97", Verdict.BETTER),
                StatRow("Colors", "G", "G", Verdict.EQUAL),
            ),
            comparison.rows,
        )
        assertEquals(listOf("Elf", "Druid", "Creature"), comparison.shared)
        assertEquals(emptyList<String>(), comparison.uniqueToBase)
        assertEquals(emptyList<String>(), comparison.uniqueToOther)
        assertEquals(4, comparison.equalCount)
        assertEquals(0, comparison.uniqueCount)
    }

    @Test
    fun `star, missing, and unranked values are shown but not compared`() {
        val goyf = card(4, "Tarmogoyf", subtypes = listOf("Lhurgoyf"), manaValue = null, power = "*", toughness = "1+*")

        val rows = compareCards(goyf, llanowar).rows

        assertEquals(StatRow("Mana value", "—", "1", Verdict.NOT_COMPARABLE), rows[0])
        assertEquals(StatRow("Power", "*", "1", Verdict.NOT_COMPARABLE), rows[1])
        assertEquals(StatRow("Toughness", "1+*", "1", Verdict.NOT_COMPARABLE), rows[2])
        assertEquals(StatRow("Popularity", "Unranked", "#65", Verdict.NOT_COMPARABLE), rows[3])
        assertEquals(0, compareCards(goyf, llanowar).betterCount)
    }

    @Test
    fun `loyalty compares higher as better and absent stats have no row`() {
        val jace = card(5, "Jace Beleren", colors = listOf(Color.U), types = listOf("Planeswalker"), manaValue = 3.0, loyalty = "3")
        val tamiyo = card(6, "Tamiyo", colors = listOf(Color.U), types = listOf("Planeswalker"), manaValue = 4.0, loyalty = "4.5")

        val rows = compareCards(jace, tamiyo).rows

        assertEquals(listOf("Mana value", "Loyalty", "Colors"), rows.map(StatRow::label))
        assertEquals(StatRow("Loyalty", "3", "4.5", Verdict.WORSE), rows[1])
    }

    @Test
    fun `keywords come only from lines that list keywords`() {
        assertEquals(listOf("Flying", "Vigilance"), keywords("Flying, vigilance"))
        assertEquals(listOf("Flying", "Vigilance"), keywords("Flying\nVigilance"))
        assertEquals(listOf("First strike", "Lifelink"), keywords("First strike, lifelink\nWhen this enters, draw a card."))
        assertEquals(listOf("Ward"), keywords("Ward {2}"))
        assertEquals(listOf("Ward"), keywords("Ward—Pay 3 life."))
        assertEquals(listOf("Protection"), keywords("Protection from red (This can't be blocked by red creatures.)"))
        assertEquals(emptyList<String>(), keywords("Creatures you control have flying."))
        assertEquals(emptyList<String>(), keywords("Flying creatures can't block this turn."))
        assertEquals(emptyList<String>(), keywords("Flashback {2}{R}"))
        assertEquals(emptyList<String>(), keywords("({T}: Add {G}.)"))
        assertEquals(emptyList<String>(), keywords(null))
    }

    @Test
    fun `comparing a card with itself is all equal`() {
        val comparison = compareCards(serra, serra)

        assertEquals(
            listOf(
                StatRow("Mana value", "5", "5", Verdict.EQUAL),
                StatRow("Power", "4", "4", Verdict.EQUAL),
                StatRow("Toughness", "4", "4", Verdict.EQUAL),
                StatRow("Popularity", "#4698", "#4698", Verdict.EQUAL),
                StatRow("Colors", "W", "W", Verdict.EQUAL),
            ),
            comparison.rows,
        )
        assertEquals(listOf("Flying", "Vigilance", "Angel", "Creature"), comparison.shared)
        assertEquals(0, comparison.uniqueCount)
    }

    @Test
    fun `a stat on only one card is shown but not compared`() {
        val rows = compareCards(llanowar, card(10, "Green Relic", types = listOf("Artifact"))).rows

        assertEquals(StatRow("Power", "1", "—", Verdict.NOT_COMPARABLE), rows.first { it.label == "Power" })
        assertEquals(StatRow("Toughness", "1", "—", Verdict.NOT_COMPARABLE), rows.first { it.label == "Toughness" })
    }

    @Test
    fun `no popularity row when neither card is ranked`() {
        val rows = compareCards(card(13, "A"), card(14, "B")).rows

        assertNull(rows.firstOrNull { it.label == "Popularity" })
    }

    @Test
    fun `traits are ordered subtypes then supertypes then types`() {
        val comparison = compareCards(
            card(11, "Legendary Elf", subtypes = listOf("Elf"), supertypes = listOf("Legendary")),
            card(12, "Plain Bear", subtypes = listOf("Bear")),
        )

        assertEquals(listOf("Elf", "Legendary"), comparison.uniqueToBase)
        assertEquals(listOf("Creature"), comparison.shared)
    }

    @Test
    fun `more keyword forms`() {
        assertEquals(listOf("Flying", "Ward"), keywords("Flying, ward {2}"))
        assertEquals(listOf("Hexproof"), keywords("Hexproof from white"))
        assertEquals(listOf("Flying"), keywords("Flying\nFlying"))
    }

    @Test
    fun `colors are listed in WUBRG order`() {
        val comparison = compareCards(card(15, "A", colors = listOf(Color.R, Color.W)), card(16, "B", colors = listOf(Color.W, Color.R)))

        assertEquals(StatRow("Colors", "W R", "W R", Verdict.EQUAL), comparison.rows.first { it.label == "Colors" })
    }

    @Test
    fun `colorless identity is named`() {
        val rock = card(7, "Rock", colors = emptyList(), types = listOf("Artifact"))
        val relic = card(8, "Relic", colors = listOf(Color.R), types = listOf("Artifact"))

        val comparison = compareCards(rock, relic)

        assertEquals(listOf("Artifact"), comparison.shared)
        assertEquals(listOf("Colorless"), comparison.uniqueToBase)
        assertEquals(listOf("Red"), comparison.uniqueToOther)
        assertNull(comparison.rows.firstOrNull { it.label == "Colors" })
    }

    @Test
    fun `a 404 from the similar route asks for the latest server`() {
        assertEquals("Comparisons need the latest server version.", similarCardsErrorMessage(ApiResult.HttpError(404, "No static resource")))
        assertEquals("The server ran into a problem. Please try again.", similarCardsErrorMessage(ApiResult.HttpError(500, null)))
    }

    private fun card(
        id: Long,
        name: String,
        colors: List<Color> = listOf(Color.G),
        types: List<String> = listOf("Creature"),
        subtypes: List<String> = emptyList(),
        supertypes: List<String> = emptyList(),
        manaValue: Double? = 1.0,
        power: String? = null,
        toughness: String? = null,
        loyalty: String? = null,
        rank: Int? = null,
        text: String? = null,
    ) = CardDto(
        id = id, uuid = "uuid-$id", cardName = name, colorIdentity = colors, colors = colors,
        convertedManaCost = manaValue, text = text, types = types, subtypes = subtypes, supertypes = supertypes,
        power = power, toughness = toughness, loyalty = loyalty, edhrecRank = rank,
    )
}
