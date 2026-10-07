package edu.csumb.magicshroom.data.auth

/** Basic user information shown by the mock account screen. */
data class MockUser(
    val email: String,
    val displayName: String,
    val role: String,
)

/**
 * Temporary local authentication used until OAuth2 Authorization Code with PKCE is implemented.
 *
 * These credentials are test data, not secrets, and must not be used for production authentication.
 */
class FakeAuthRepository {
    /** The single user available to the mock UI. */
    val mockUser = MockUser(
        email = TEST_EMAIL,
        displayName = "testUser1",
        role = "USER",
    )

    /** Returns the mock user when both credentials match; otherwise returns null. */
    fun authenticate(email: String, password: String): MockUser? =
        mockUser.takeIf {
            email.trim().equals(TEST_EMAIL, ignoreCase = true) && password == TEST_PASSWORD
        }

    companion object {
        const val TEST_EMAIL = "testEmail1@gmail.com"
        const val TEST_PASSWORD = "password1"
    }
}
