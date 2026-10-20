package edu.csumb.magicshroom.data.auth

import edu.csumb.magicshroom.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Google-only sign-in with persisted Supabase sessions and safe user-facing errors. */
class SupabaseAuthRepository(private val supabase: SupabaseClient) : AuthRepository {
    private val auth = supabase.auth
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Loading)
    private val mutableErrorMessage = MutableStateFlow<String?>(null)

    /** Reflects Supabase session restoration, sign-in, refresh, and sign-out events. */
    override val authState = mutableAuthState.asStateFlow()

    override val errorMessage = mutableErrorMessage.asStateFlow()

    init {
        scope.launch {
            auth.sessionStatus.collect { status -> mutableAuthState.value = status.toAuthState() }
        }
    }

    /** Opens Google; the session observer confirms completion after the OAuth callback. */
    override suspend fun signInWithGoogle() {
        clearError()
        if (BuildConfig.SUPABASE_URL == "https://example.supabase.co" ||
            BuildConfig.SUPABASE_PUBLISHABLE_KEY == "sb_publishable_replace_me"
        ) {
            mutableErrorMessage.value = "Configure the Supabase URL and publishable key to use Google sign-in."
            return
        }
        try {
            auth.signInWith(Google)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableErrorMessage.value = "Google sign-in couldn't start. Please try again."
        }
    }

    /** Signs out of Supabase and clears its persisted local session. */
    override suspend fun signOut() {
        clearError()
        try {
            auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableErrorMessage.value = "Couldn't sign out. Please try again."
        }
    }

    fun onCallbackError() {
        mutableErrorMessage.value = "Google sign-in didn't finish. Please try again."
    }

    override fun clearError() {
        mutableErrorMessage.value = null
    }

    /** Supplies the current Supabase access token to the API's Authorization interceptor. */
    override fun accessToken(): String? = auth.currentAccessTokenOrNull()

    /** Converts Supabase's initialization and session events to the app's auth state. */
    private fun SessionStatus.toAuthState(): AuthState = when (this) {
        SessionStatus.Initializing -> AuthState.Loading
        is SessionStatus.Authenticated -> session.toAuthSession()?.let(AuthState::SignedIn) ?: AuthState.SignedOut
        is SessionStatus.NotAuthenticated -> AuthState.SignedOut
        is SessionStatus.RefreshFailure -> auth.currentSessionOrNull()?.toAuthSession()
            ?.let(AuthState::SignedIn) ?: AuthState.SignedOut
    }

    /** Projects Supabase profile claims into the account screen's non-authoritative display model. */
    private fun UserSession.toAuthSession(): AuthSession? {
        val profile = user ?: auth.currentUserOrNull() ?: return null
        if (profile.email.isNullOrBlank()) return null
        return AuthSession(profile.toProfile())
    }
}

/** Google profile data is display-only; application roles must come from the backend. */
internal fun UserInfo.toProfile(): UserProfile {
    val userEmail = email.orEmpty()
    val name = listOf("full_name", "name")
        .firstNotNullOfOrNull { key ->
            (userMetadata?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }
        ?: userEmail.substringBefore('@').ifBlank { "Google user" }
    return UserProfile(email = userEmail, displayName = name)
}
