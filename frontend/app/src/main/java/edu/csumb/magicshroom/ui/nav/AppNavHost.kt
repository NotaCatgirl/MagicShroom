package edu.csumb.magicshroom.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import edu.csumb.magicshroom.data.auth.AuthRepository
import edu.csumb.magicshroom.data.auth.AuthState
import edu.csumb.magicshroom.ui.account.UserScreen
import edu.csumb.magicshroom.ui.login.LoginScreen
import edu.csumb.magicshroom.ui.search.SearchScreen

private object AppRoute {
    const val SEARCH = "search"
    const val LOGIN = "login"
    const val ACCOUNT = "account"
}

/** Public card browsing with optional Google sign-in and a protected account screen. */
@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    canAccessBackend: Boolean = true,
    onRequestLocalNetworkPermission: () -> Unit = {},
) {
    val authState by authRepository.authState.collectAsState(initial = AuthState.Loading)
    val errorMessage by authRepository.errorMessage.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val user = (authState as? AuthState.SignedIn)?.user

    // Keep the Search destination (and its query/results) alive across sign-in and sign-out.
    LaunchedEffect(authState, route) {
        if ((user != null && route == AppRoute.LOGIN) ||
            (authState == AuthState.SignedOut && route == AppRoute.ACCOUNT)
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
            )
        }
        composable(AppRoute.LOGIN) {
            if (authState == AuthState.Loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
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
            if (user != null) {
                UserScreen(
                    user = user,
                    onBack = { navController.popBackStack() },
                    onSignOut = authRepository::signOut,
                    errorMessage = errorMessage,
                )
            }
        }
    }
}
