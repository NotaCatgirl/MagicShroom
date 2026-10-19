package edu.csumb.magicshroom.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import edu.csumb.magicshroom.data.auth.AuthRepository
import edu.csumb.magicshroom.data.auth.AuthState
import edu.csumb.magicshroom.ui.account.UserScreen
import edu.csumb.magicshroom.ui.login.LoginScreen
import edu.csumb.magicshroom.ui.search.SearchScreen

private object AppRoute {
    const val SEARCH = "search"
    const val ACCOUNT = "account"
}

/** Shows sign-in or authenticated destinations based on the persisted Supabase session. */
@Composable
fun AppNavHost(
    authRepository: AuthRepository,
    canAccessBackend: Boolean = true,
    onRequestLocalNetworkPermission: () -> Unit = {},
) {
    val authState by authRepository.authState.collectAsState()
    when (val state = authState) {
        AuthState.Loading -> AuthLoadingScreen()
        AuthState.SignedOut -> LoginScreen(
            onLogin = { email, password ->
                runCatching { authRepository.signIn(email, password) }.getOrNull() != null
            },
            onGoogleSignIn = { authRepository.signInWithGoogle() },
        )
        is AuthState.SignedIn -> {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = AppRoute.SEARCH) {
                composable(AppRoute.SEARCH) {
                    SearchScreen(
                        canAccessBackend = canAccessBackend,
                        onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
                        onAccountClick = { navController.navigate(AppRoute.ACCOUNT) { launchSingleTop = true } },
                    )
                }
                composable(AppRoute.ACCOUNT) {
                    UserScreen(
                        user = state.session.user,
                        onBack = { navController.popBackStack() },
                        onSignOut = { authRepository.signOut() },
                    )
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
