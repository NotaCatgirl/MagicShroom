package edu.csumb.magicshroom.data.auth

/** Basic information about the signed-in user. */
data class UserProfile(
    val email: String,
    val displayName: String,
    val role: String,
)

/** The authentication state used by the app's screens. */
data class AuthSession(
    val user: UserProfile,
)

/** Authentication boundary that can later be implemented with Supabase. */
interface AuthRepository {
    val currentSession: AuthSession?

    /** Returns a session when the supplied credentials are accepted. */
    suspend fun signIn(email: String, password: String): AuthSession?

    fun signOut()
}
