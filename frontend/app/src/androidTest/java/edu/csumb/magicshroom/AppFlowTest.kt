package edu.csumb.magicshroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import edu.csumb.magicshroom.data.auth.DemoAuthRepository
import edu.csumb.magicshroom.ui.account.UserTestTags
import edu.csumb.magicshroom.ui.login.LoginTestTags
import edu.csumb.magicshroom.ui.nav.AppNavHost
import edu.csumb.magicshroom.ui.search.SearchTestTags
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import org.junit.Rule
import org.junit.Test

/** Verifies public browsing and sign-in for account features. */
class AppFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** Checks public card browsing and navigation into the account sign-in flow. */
    @Test
    fun publicSearchCanOpenSignInAndAccount() {
        composeRule.setContent {
            MagicShroomTheme {
                AppNavHost()
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).performClick()
        composeRule.onNodeWithTag(LoginTestTags.EMAIL).assertIsDisplayed()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).performClick()

        composeRule.onNodeWithTag(UserTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText(DemoAuthRepository.TEST_EMAIL).assertIsDisplayed()
        composeRule.onNodeWithText("User").assertIsDisplayed()
        composeRule.onNodeWithTag(UserTestTags.BACK).performClick()
        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
    }
}
