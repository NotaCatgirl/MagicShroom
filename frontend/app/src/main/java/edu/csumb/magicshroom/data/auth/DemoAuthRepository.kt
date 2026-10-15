package edu.csumb.magicshroom.data.auth

/**
 * Local authentication for the current UI milestone.
 *
 * The UI depends on [AuthRepository], so this class can later be replaced by a Supabase-backed
 * implementation without changing the login, search, or account screens.
 */
class DemoAuthRepository : AuthRepository {
    private val demoUser = UserProfile(
        email = TEST_EMAIL,
        displayName = "testUser1",
        role = "USER",
    )

    override var currentSession: AuthSession? = null
        private set

    override suspend fun signIn(email: String, password: String): AuthSession? {
        val user = demoUser.takeIf {
            email.trim().equals(TEST_EMAIL, ignoreCase = true) && password == TEST_PASSWORD
        }
        return user?.let(::AuthSession).also { currentSession = it }
    }

    override fun signOut() {
        currentSession = null
    }

    companion object {
        const val TEST_EMAIL = "testEmail1@gmail.com"
        const val TEST_PASSWORD = "password1"
    }
}
