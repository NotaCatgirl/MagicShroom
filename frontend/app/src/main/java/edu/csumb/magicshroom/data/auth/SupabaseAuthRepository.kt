package edu.csumb.magicshroom.data.auth

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Supabase-backed email/password and Google sign-in with persisted SDK-managed sessions. */
class SupabaseAuthRepository(private val supabase: SupabaseClient) : AuthRepository {
    private val auth = supabase.auth
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Loading)

    /** Reflects Supabase session restoration, sign-in, refresh, and sign-out events. */
    override val authState = mutableAuthState.asStateFlow()

    init {
        scope.launch {
            auth.sessionStatus.collect { status -> mutableAuthState.value = status.toAuthState() }
        }
    }

    /** Signs into an existing Supabase account using its email and password. */
    override suspend fun signIn(email: String, password: String): AuthSession? {
        auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
        return auth.currentSessionOrNull()?.toAuthSession()
    }

    /** Opens the configured Google OAuth flow and handles its return through the app deep link. */
    override suspend fun signInWithGoogle() { auth.signInWith(Google) }

    /** Signs out of Supabase and clears its persisted local session. */
    override suspend fun signOut() { auth.signOut() }

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
        val email = profile.email?.takeIf(String::isNotBlank) ?: return null
        val displayName = profile.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
            ?: profile.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
            ?: email.substringBefore('@')
        return AuthSession(UserProfile(email, displayName, "USER"))
    }
}
