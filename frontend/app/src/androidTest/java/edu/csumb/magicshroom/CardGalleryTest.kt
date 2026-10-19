package edu.csumb.magicshroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.CardImageFaceDto
import edu.csumb.magicshroom.ui.detail.CardDetailContent
import edu.csumb.magicshroom.ui.detail.CardDetailTestTags
import edu.csumb.magicshroom.ui.search.SearchContent
import edu.csumb.magicshroom.ui.search.SearchTestTags
import edu.csumb.magicshroom.ui.search.SearchUiState
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import org.junit.Rule
import org.junit.Test

/** Verifies real-card gallery navigation and multiple-face detail behavior. */
class CardGalleryTest {
    @get:Rule val composeRule = createComposeRule()

    /** The public gallery shows card tiles and clicking one opens that card's details. */
    @Test
    fun galleryTileOpensCardDetail() {
        val card = testCard()
        var selected by mutableStateOf<CardDto?>(null)
        composeRule.setContent {
            MagicShroomTheme {
                if (selected == null) {
                    SearchContent(
                        query = "",
                        state = SearchUiState(cards = listOf(card), totalCards = 1, isLoading = false),
                        onQueryChange = {}, onSearchNow = {}, onRetry = {}, onLoadMore = {},
                        onAccountClick = {}, onCardClick = { selected = card },
                    )
                } else {
                    CardDetailContent(requireNotNull(selected), onBack = {})
                }
            }
        }
        composeRule.onNodeWithTag(SearchTestTags.CARD + card.id).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(CardDetailTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("Lightning Bolt").assertIsDisplayed()
    }

    /** A multi-face card exposes a control that advances through its actual face scans. */
    @Test
    fun detailCanSwitchToOtherCardFace() {
        composeRule.setContent {
            MagicShroomTheme { CardDetailContent(testCard(), onBack = {}) }
        }
        composeRule.onNodeWithText("1 of 2: Front").assertIsDisplayed()
        composeRule.onNodeWithTag(CardDetailTestTags.NEXT_FACE).performClick()
        composeRule.onNodeWithText("2 of 2: Back").assertIsDisplayed()
    }

    /** Provides a two-face sample card for gallery and detail UI tests. */
    private fun testCard() = CardDto(
        id = 1,
        uuid = "0318d56b-647f-4b0f-9306-9adc3312619d",
        cardName = "Lightning Bolt",
        manaCost = "{R}",
        types = listOf("Instant"),
        text = "Deals 3 damage to any target.",
        imageFaces = listOf(
            CardImageFaceDto("Front", "https://cards.scryfall.io/small/front/a.jpg", "https://cards.scryfall.io/normal/front/a.jpg"),
            CardImageFaceDto("Back", "https://cards.scryfall.io/small/back/a.jpg", "https://cards.scryfall.io/normal/back/a.jpg"),
        ),
    )
}
