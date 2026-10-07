package edu.csumb.magicshroom.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import edu.csumb.magicshroom.data.auth.FakeAuthRepository
import edu.csumb.magicshroom.ui.account.UserScreen
import edu.csumb.magicshroom.ui.login.LoginScreen
import edu.csumb.magicshroom.ui.search.SearchScreen

private object AppRoute {
    const val LOGIN = "login"
    const val SEARCH = "search"
    const val ACCOUNT = "account"
}

/**
 * Defines the mock login, search, and account navigation flow.
 *
 * Authentication is intentionally held in memory until OAuth is implemented.
 */
@Composable
fun AppNavHost(authRepository: FakeAuthRepository = remember { FakeAuthRepository() }) {
    val navController = rememberNavController()
    var isLoggedIn by rememberSaveable { mutableStateOf(false) }

    NavHost(
        navController = navController,
        startDestination = AppRoute.LOGIN,
    ) {
        composable(AppRoute.LOGIN) {
            LoginScreen(
                onLogin = { email, password ->
                    val authenticated = authRepository.authenticate(email, password) != null
                    if (authenticated) {
                        isLoggedIn = true
                        navController.navigate(AppRoute.SEARCH) {
                            popUpTo(AppRoute.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    authenticated
                },
            )
        }

        composable(AppRoute.SEARCH) {
            SearchScreen(
                onAccountClick = {
                    if (isLoggedIn) {
                        navController.navigate(AppRoute.ACCOUNT) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(AppRoute.ACCOUNT) {
            UserScreen(
                user = authRepository.mockUser,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
