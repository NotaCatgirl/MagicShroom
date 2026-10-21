package edu.csumb.magicshroom.data.auth

import kotlinx.coroutines.flow.StateFlow

/** Basic information about the signed-in user. */
data class UserProfile(
    val email: String,
    val displayName: String,
    /** Display value; account permissions must be confirmed by the backend. */
    val role: String = "USER",
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

/** Google-only authentication; screens observe sessions and never handle OAuth tokens themselves. */
interface AuthRepository {
    /** Emits session changes, including the initial restoration from secure platform storage. */
    val authState: StateFlow<AuthState>

    /** A safe message for a failed sign-in, callback, or sign-out; null when there is no error. */
    val errorMessage: StateFlow<String?>

    /** Starts Google OAuth in the system browser; the configured app link completes the session. */
    suspend fun signInWithGoogle()

    /** Ends the active Supabase session. */
    suspend fun signOut()

    /** Clears an error when the user retries or leaves the sign-in screen. */
    fun clearError()

    /** Returns the latest access token for attaching to API requests. */
    fun accessToken(): String?
}
