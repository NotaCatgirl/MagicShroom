package edu.csumb.magicshroom.data.auth

import kotlinx.coroutines.flow.StateFlow

/** Basic information about the signed-in user. */
data class UserProfile(
    val email: String,
    val displayName: String,
    val role: String,
)

/** The user-facing account data held for a signed-in session. */
data class AuthSession(val user: UserProfile)

/** Authentication state used to restore the correct screen while Supabase loads its saved session. */
sealed interface AuthState {
    /** Session storage has not finished loading. */
    data object Loading : AuthState

    /** No user is signed in. */
    data object SignedOut : AuthState

    /** Supabase has a signed-in user. */
    data class SignedIn(val session: AuthSession) : AuthState
}

/** Authentication boundary implemented by Supabase in production and a deterministic fake in tests. */
interface AuthRepository {
    /** Emits session changes, including the initial restoration from secure platform storage. */
    val authState: StateFlow<AuthState>

    /** Signs in an existing Supabase email/password account, or returns null when rejected. */
    suspend fun signIn(email: String, password: String): AuthSession?

    /** Starts Google OAuth in the system browser; the configured app link completes the session. */
    suspend fun signInWithGoogle()

    /** Ends the active Supabase session. */
    suspend fun signOut()

    /** Returns the latest access token for attaching to API requests. */
    fun accessToken(): String?
}
