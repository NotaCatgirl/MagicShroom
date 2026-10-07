package edu.csumb.magicshroom.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the temporary local authentication behavior. */
class FakeAuthRepositoryTest {
    private val repository = FakeAuthRepository()

    @Test
    fun validCredentialsReturnMockUser() {
        val user = repository.authenticate(
            FakeAuthRepository.TEST_EMAIL,
            FakeAuthRepository.TEST_PASSWORD,
        )

        assertEquals("testUser1", user?.displayName)
        assertEquals("USER", user?.role)
    }

    @Test
    fun invalidPasswordIsRejected() {
        val user = repository.authenticate(
            FakeAuthRepository.TEST_EMAIL,
            "incorrect",
        )

        assertNull(user)
    }
}
