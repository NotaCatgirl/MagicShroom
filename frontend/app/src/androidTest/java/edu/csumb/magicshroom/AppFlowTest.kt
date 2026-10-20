package edu.csumb.magicshroom

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.auth.AuthRepository
import edu.csumb.magicshroom.data.auth.AuthSession
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

/** UI navigation checks with simulated sessions; these do not exercise Google or Supabase OAuth. */
class AppFlowTest {
    @get:Rule val composeRule = createComposeRule()

    /** A simulated session unlocks Account, and signing out returns to public Search. */
    @Test
    fun simulatedSessionCanOpenAccountAndSignOutToPublicSearch() {
        val auth = FakeGoogleAuthRepository()
        composeRule.setContent {
            MagicShroomTheme { AppNavHost(authRepository = auth, canAccessBackend = false) }
        }

        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("MagicShroom").assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).assertDoesNotExist()
        composeRule.onNodeWithTag(SearchTestTags.LOGIN).assertIsDisplayed().performClick()
        composeRule.onNodeWithText("Email").assertDoesNotExist()
        composeRule.onNodeWithText("Password").assertDoesNotExist()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(LoginTestTags.SUBMIT).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertDoesNotExist()
        composeRule.onNodeWithTag(UserTestTags.SCREEN).assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(1, auth.googleSignInRequests)
            auth.completeSignIn()
        }

        composeRule.onNodeWithTag(SearchTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.LOGIN).assertDoesNotExist()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).performClick()

        composeRule.onNodeWithTag(UserTestTags.SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("tony@example.com").assertIsDisplayed()
        composeRule.onNodeWithTag(UserTestTags.SIGN_OUT).performScrollTo().performClick()
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
                    onCardClick = {},
                )
            }
        }

        composeRule.onNodeWithTag(SearchTestTags.CARD + 1).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.FIELD).assertIsEnabled().performTextInput("bolt")
        composeRule.runOnIdle { assertEquals("bolt", enteredQuery) }
        composeRule.onNodeWithTag(SearchTestTags.LOGIN).assertIsDisplayed()
        composeRule.onNodeWithTag(SearchTestTags.ACCOUNT).assertDoesNotExist()
    }

    /** Test-only session control: no credentials, browser launch, or provider requests. */
    private class FakeGoogleAuthRepository : AuthRepository {
        override val authState = MutableStateFlow<AuthState>(AuthState.SignedOut)
        override val errorMessage = MutableStateFlow<String?>(null)
        var googleSignInRequests = 0
            private set

        override suspend fun signInWithGoogle() {
            googleSignInRequests += 1 // A provider launch alone must not create a signed-in session.
        }

        fun completeSignIn() {
            authState.value = AuthState.SignedIn(
                AuthSession(UserProfile("tony@example.com", "Tony Tester")),
            )
        }

        override suspend fun signOut() {
            authState.value = AuthState.SignedOut
        }

        override fun clearError() {
            errorMessage.value = null
        }

        override fun accessToken(): String? = null
    }
}
