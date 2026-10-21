package edu.csumb.magicshroom.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import edu.csumb.magicshroom.data.api.Role
import edu.csumb.magicshroom.data.auth.AuthRepository
import edu.csumb.magicshroom.data.auth.AuthState
import edu.csumb.magicshroom.ui.account.AccountViewModel
import edu.csumb.magicshroom.ui.account.UserScreen
import edu.csumb.magicshroom.ui.admin.AdminScreen
import edu.csumb.magicshroom.ui.detail.CardDetailScreen
import edu.csumb.magicshroom.ui.deck.DeckDetailScreen
import edu.csumb.magicshroom.ui.deck.DeckListScreen
import edu.csumb.magicshroom.ui.login.LoginScreen
import edu.csumb.magicshroom.ui.search.SearchScreen

private object AppRoute {
    const val SEARCH = "search"
    const val LOGIN = "login"
    const val ACCOUNT = "account"
    const val ADMIN = "admin"
    const val CARD = "cards/{cardId}"
    const val DECKS = "decks"
    const val DECK = "decks/{deckId}"
}

/** Public card browsing with optional Google sign-in and protected account and admin screens. */
@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    canAccessBackend: Boolean = true,
    onRequestLocalNetworkPermission: () -> Unit = {},
) {
    val authState by authRepository.authState.collectAsState()
    val errorMessage by authRepository.errorMessage.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val user = (authState as? AuthState.SignedIn)?.session?.user

    // Keep the Search destination (and its query/results) alive across sign-in and sign-out.
    LaunchedEffect(authState, route) {
        if ((user != null && route == AppRoute.LOGIN) ||
                (authState == AuthState.SignedOut &&
                    (route == AppRoute.ACCOUNT || route == AppRoute.ADMIN ||
                        route == AppRoute.DECKS || route == AppRoute.DECK))
        ) {
            navController.popBackStack(AppRoute.SEARCH, inclusive = false)
        }
    }

    NavHost(navController = navController, startDestination = AppRoute.SEARCH) {
        composable(AppRoute.SEARCH) {
            SearchScreen(
                canAccessBackend = canAccessBackend,
                onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                isSignedIn = user != null,
                onLoginClick = {
                    authRepository.clearError()
                    navController.navigate(AppRoute.LOGIN) { launchSingleTop = true }
                },
                onAccountClick = {
                    if (user != null) {
                        authRepository.clearError()
                        navController.navigate(AppRoute.ACCOUNT) { launchSingleTop = true }
                    }
                },
                onCardClick = { cardId ->
                    navController.navigate("cards/$cardId") { launchSingleTop = true }
                },
            )
        }
        composable(
            AppRoute.CARD,
            arguments = listOf(navArgument("cardId") { type = NavType.LongType }),
        ) { entry ->
            CardDetailScreen(
                cardId = requireNotNull(entry.arguments).getLong("cardId"),
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoute.LOGIN) {
            if (authState == AuthState.Loading) {
                AuthLoadingScreen()
            } else if (user == null) {
                LoginScreen(
                    onGoogleLogin = authRepository::signInWithGoogle,
                    errorMessage = errorMessage,
                    onBackToSearch = {
                        authRepository.clearError()
                        navController.popBackStack()
                    },
                )
            }
        }
        composable(AppRoute.ACCOUNT) {
            if (authState == AuthState.Loading) {
                AuthLoadingScreen()
            } else if (user != null) {
                val accountViewModel: AccountViewModel = viewModel(factory = AccountViewModel.Factory)
                val me by accountViewModel.me.collectAsState()
                UserScreen(
                    user = user.copy(role = me?.role?.name ?: "USER"),
                    onBack = { navController.popBackStack() },
                    onSignOut = authRepository::signOut,
                    errorMessage = errorMessage,
                    onAdminClick = if (me?.role == Role.ADMIN) {
                        { navController.navigate(AppRoute.ADMIN) { launchSingleTop = true } }
                    } else {
                        null
                    },
                    onDecksClick = {
                        navController.navigate(AppRoute.DECKS) { launchSingleTop = true }
                    },
                )
            }
        }
        composable(AppRoute.DECKS) {
            if (authState == AuthState.Loading) {
                AuthLoadingScreen()
            } else if (user != null) {
                DeckListScreen(
                    onBack = { navController.popBackStack() },
                    onDeckClick = { deckId -> navController.navigate("decks/$deckId") { launchSingleTop = true } },
                )
            }
        }
        composable(
            AppRoute.DECK,
            arguments = listOf(navArgument("deckId") { type = NavType.LongType }),
        ) { entry ->
            if (authState == AuthState.Loading) {
                AuthLoadingScreen()
            } else if (user != null) {
                DeckDetailScreen(
                    deckId = requireNotNull(entry.arguments).getLong("deckId"),
                    onBack = { navController.popBackStack() },
                    onCardClick = { cardId -> navController.navigate("cards/$cardId") { launchSingleTop = true } },
                )
            }
        }
        composable(AppRoute.ADMIN) { entry ->
            if (authState == AuthState.Loading) {
                AuthLoadingScreen()
            } else if (user != null) {
                // Reuse Account's backend-confirmed role, not a role supplied by Google metadata.
                val accountEntry = remember(entry) { navController.getBackStackEntry(AppRoute.ACCOUNT) }
                val accountViewModel: AccountViewModel = viewModel(
                    viewModelStoreOwner = accountEntry,
                    factory = AccountViewModel.Factory,
                )
                val me by accountViewModel.me.collectAsState()
                LaunchedEffect(me?.role) {
                    if (me?.role != Role.ADMIN) {
                        navController.popBackStack(AppRoute.ACCOUNT, inclusive = false)
                    }
                }
                if (me?.role == Role.ADMIN) {
                    AdminScreen(onBack = { navController.popBackStack() })
                } else {
                    AuthLoadingScreen()
                }
            }
        }
    }
}

/** Keeps rotation and session restoration from briefly presenting an empty account screen. */
@Composable
private fun AuthLoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
