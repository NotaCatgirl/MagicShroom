package edu.csumb.magicshroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.Color
import edu.csumb.magicshroom.ui.compare.CardComparisonContent
import edu.csumb.magicshroom.ui.compare.CompareTestTags
import edu.csumb.magicshroom.ui.detail.CardDetailContent
import edu.csumb.magicshroom.ui.detail.CardDetailTestTags
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Verifies the "Compare with…" entry point and the verdicts a player sees. */
class CardComparisonUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val llanowar = CardDto(
        id = 1, uuid = "u1", cardName = "Llanowar Elves", colorIdentity = listOf(Color.G), colors = listOf(Color.G),
        convertedManaCost = 1.0, types = listOf("Creature"), subtypes = listOf("Elf", "Druid"),
        power = "1", toughness = "1", edhrecRank = 65,
    )
    private val serra = CardDto(
        id = 2, uuid = "u2", cardName = "Serra Angel", colorIdentity = listOf(Color.W), colors = listOf(Color.W),
        convertedManaCost = 5.0, text = "Flying, vigilance", types = listOf("Creature"), subtypes = listOf("Angel"),
        power = "4", toughness = "4", edhrecRank = 4698,
    )

    @Test
    fun detailCompareButtonOpensComparison() {
        var clicked = false
        composeRule.setContent { MagicShroomTheme { CardDetailContent(llanowar, onBack = {}, onCompareClick = { clicked = true }) } }

        composeRule.onNodeWithTag(CardDetailTestTags.COMPARE).performScrollTo().assertIsDisplayed().performClick()
        assertTrue(clicked)
    }

    @Test
    fun comparisonShowsTallyVerdictsAndUniqueTraits() {
        composeRule.setContent { MagicShroomTheme { CardComparisonContent(llanowar, serra, onBack = {}) } }

        composeRule.onNodeWithTag(CompareTestTags.COMPARISON_SCREEN).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("2 better, 2 worse, 0 equal, 7 unique").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Mana value: Llanowar Elves 1, Serra Angel 5, better").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Power: Llanowar Elves 1, Serra Angel 4, worse").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Unique to Serra Angel").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("★ Flying  ★ Vigilance  ★ Angel  ★ White").performScrollTo().assertIsDisplayed()
    }
}
