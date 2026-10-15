package edu.csumb.magicshroom

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

/** Checks the API-backed card information displayed on the Android home screen. */
class HomeCardDisplayTest {
    @get:Rule val composeRule = createComposeRule()

    /** Shows card names in the browse list and the selected card's cost and text. */
    @Test
    fun selectingCardDisplaysItsDetails() {
        val bolt = CardSummary(1, "Lightning Bolt", "Deals 3 damage to any target.", "{R}")
        val elves = CardSummary(2, "Llanowar Elves", "{T}: Add {G}.", "{G}")
        var selected by mutableStateOf<CardSummary?>(null)
        composeRule.setContent {
            Column {
                selected?.let { CardDetails(it) }
                CardList(listOf(bolt, elves)) { selected = it }
            }
        }

        composeRule.onNodeWithText("Lightning Bolt").assertExists().performClick()
        composeRule.onNodeWithText("Lightning Bolt {R}").assertExists()
        composeRule.onNodeWithText("Deals 3 damage to any target.").assertExists()

        composeRule.onNodeWithText("Llanowar Elves").assertExists().performClick()
        composeRule.onNodeWithText("Llanowar Elves {G}").assertExists()
        composeRule.onNodeWithText("{T}: Add {G}.").assertExists()
    }
}
