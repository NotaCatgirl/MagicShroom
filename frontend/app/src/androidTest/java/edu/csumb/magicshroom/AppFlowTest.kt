package edu.csumb.magicshroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import edu.csumb.magicshroom.data.auth.FakeAuthRepository
import edu.csumb.magicshroom.ui.account.UserTestTags
import edu.csumb.magicshroom.ui.login.LoginTestTags
import edu.csumb.magicshroom.ui.nav.AppNavHost
import edu.csumb.magicshroom.ui.search.SearchTestTags
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import org.junit.Rule
import org.junit.Test

/** Verifies the mock login, search, and account screen flow. */
class AppFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validLoginOpensSearchThenAccount() {
        composeRule.setContent {
            MagicShroomTheme {
                AppNavHost()
            }
        }

        composeRule.onNodeWithTag(LoginTestTags.EMAIL)
            .performTextInput(FakeAuthRepository.TEST_EMAIL)
        composeRule.onNodeWithTag(LoginTestTags.PASSWORD)
            .performTextInput(FakeAuthRepository.TEST_PASSWORD)
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).performClick()

        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).performClick()
        composeRule.onNodeWithTag(UserTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("testUser1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(FakeAuthRepository.TEST_EMAIL).assertIsDisplayed()
        composeRule.onNodeWithText("USER").assertIsDisplayed()
    }
}
