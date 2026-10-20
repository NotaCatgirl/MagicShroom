package edu.csumb.magicshroom.data.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Basic information about the signed-in user. */
data class UserProfile(
    val email: String,
    val displayName: String,
)

/** Session state for optional sign-in and access to account information. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val user: UserProfile) : AuthState
}

/** Authentication boundary; screens do not handle OAuth tokens themselves. */
interface AuthRepository {
    val authState: Flow<AuthState>
    val errorMessage: StateFlow<String?>

    /** Opens the Google flow. Completion is reported later through [authState]. */
    suspend fun signInWithGoogle()
    suspend fun signOut()
    fun clearError()
}
