package edu.csumb.magicshroom.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Deterministic in-memory authentication fake used only by UI tests. */
class DemoAuthRepository : AuthRepository {
    private val demoUser = UserProfile(TEST_EMAIL, "testUser1", "USER")
    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.SignedOut)

    /** Emits the fake user's signed-in or signed-out state. */
    override val authState = mutableAuthState.asStateFlow()

    /** Accepts only the fixture credentials used by instrumented UI tests. */
    override suspend fun signIn(email: String, password: String): AuthSession? {
        val session = demoUser.takeIf { email.trim().equals(TEST_EMAIL, ignoreCase = true) && password == TEST_PASSWORD }
            ?.let(::AuthSession)
        mutableAuthState.value = session?.let(AuthState::SignedIn) ?: AuthState.SignedOut
        return session
    }

    /** Rejects Google OAuth because the UI fake does not start external authentication. */
    override suspend fun signInWithGoogle() = Unit

    /** Clears the fake signed-in session. */
    override suspend fun signOut() { mutableAuthState.value = AuthState.SignedOut }

    /** Returns no API token because this fake never authenticates with Supabase. */
    override fun accessToken(): String? = null

    companion object {
        const val TEST_EMAIL = "testEmail1@gmail.com"
        const val TEST_PASSWORD = "password1"
    }
}
