package edu.csumb.magicshroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.auth.AuthRepository
import edu.csumb.magicshroom.data.auth.AuthState
import edu.csumb.magicshroom.data.auth.UserProfile
import edu.csumb.magicshroom.ui.account.UserTestTags
import edu.csumb.magicshroom.ui.login.LoginTestTags
import edu.csumb.magicshroom.ui.nav.AppNavHost
import edu.csumb.magicshroom.ui.search.SearchTestTags
import edu.csumb.magicshroom.ui.search.SearchContent
import edu.csumb.magicshroom.ui.search.SearchUiState
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Guest browsing stays public; only the account screen needs a confirmed session. */
class AppFlowTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun guestCanSignInAndSignOutBackToPublicSearch() {
        val auth = FakeGoogleAuthRepository()
        composeRule.setContent {
            MagicShroomTheme { AppNavHost(authRepository = auth, canAccessBackend = false) }
        }

        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("MagicShroom").assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).assertDoesNotExist()
        composeRule.onNodeWithTag(SearchTestTags.LOGIN).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsDisplayed()
        composeRule.runOnIdle { auth.completeSignIn() }

        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).performClick()
        composeRule.onNodeWithTag(UserTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("tony@example.com").assertIsDisplayed()
        composeRule.onNodeWithTag(UserTestTags.SIGN_OUT).performClick()
        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.LOGIN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).assertDoesNotExist()
        composeRule.onNodeWithTag(UserTestTags.SCREEN).assertDoesNotExist()
    }

    @Test
    fun guestCanReturnFromLoginWithoutSigningIn() {
        val auth = FakeGoogleAuthRepository()
        composeRule.setContent {
            MagicShroomTheme { AppNavHost(authRepository = auth, canAccessBackend = false) }
        }

        composeRule.onNodeWithTag(SearchTestTags.LOGIN).performClick()
        composeRule.onNodeWithText("Back to search").performClick()
        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.LOGIN).assertIsDisplayed()
    }

    @Test
    fun guestSearchDisplaysCardsAndAcceptsQuery() {
        var enteredQuery = ""
        composeRule.setContent {
            MagicShroomTheme {
                SearchContent(
                    query = "",
                    state = SearchUiState(
                        cards = listOf(CardDto(id = 1, uuid = "test-card", cardName = "Lightning Bolt")),
                        totalCards = 1,
                        isLoading = false,
                    ),
                    onQueryChange = { enteredQuery = it },
                    onSearchNow = {},
                    onRetry = {},
                    onLoadMore = {},
                    onAccountClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Lightning Bolt").assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.FIELD).assertIsEnabled().performTextInput("bolt")
        composeRule.runOnIdle { assertEquals("bolt", enteredQuery) }
        composeRule.onNodeWithTag(SearchTestTags.LOGIN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).assertDoesNotExist()
    }

    private class FakeGoogleAuthRepository : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
        override val errorMessage = MutableStateFlow<String?>(null)

        override suspend fun signInWithGoogle() = Unit // The browser launch alone is not a session.

        fun completeSignIn() {
            authState.value = AuthState.SignedIn(UserProfile("tony@example.com", "Tony Tester"))
        }

        override suspend fun signOut() {
            authState.value = AuthState.SignedOut
        }

        override fun clearError() {
            errorMessage.value = null
        }
    }
}
