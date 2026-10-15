package edu.csumb.magicshroom.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import edu.csumb.magicshroom.data.auth.AuthRepository
import edu.csumb.magicshroom.data.auth.AuthSession
import edu.csumb.magicshroom.data.auth.DemoAuthRepository
import edu.csumb.magicshroom.ui.account.UserScreen
import edu.csumb.magicshroom.ui.login.LoginScreen
import edu.csumb.magicshroom.ui.search.SearchScreen

private object AppRoute {
    const val LOGIN = "login"
    const val SEARCH = "search"
    const val ACCOUNT = "account"
}

/**
 * Defines the login, search, and account navigation flow.
 */
@Composable
fun AppNavHost(
    authRepository: AuthRepository = remember { DemoAuthRepository() },
    canAccessBackend: Boolean = true,
    onRequestLocalNetworkPermission: () -> Unit = {},
) {
    val navController = rememberNavController()
    var session by remember { mutableStateOf<AuthSession?>(authRepository.currentSession) }

    NavHost(
        navController = navController,
        startDestination = AppRoute.LOGIN,
    ) {
        composable(AppRoute.LOGIN) {
            LoginScreen(
                onLogin = { email, password ->
                    val newSession = authRepository.signIn(email, password)
                    if (newSession != null) {
                        session = newSession
                        navController.navigate(AppRoute.SEARCH) {
                            popUpTo(AppRoute.LOGIN) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    newSession != null
                },
            )
        }

        composable(AppRoute.SEARCH) {
            SearchScreen(
                canAccessBackend = canAccessBackend,
                onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                onAccountClick = {
                    if (session != null) {
                        navController.navigate(AppRoute.ACCOUNT) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(AppRoute.ACCOUNT) {
            session?.let { activeSession ->
                UserScreen(
                    user = activeSession.user,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
