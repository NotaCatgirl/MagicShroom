package edu.csumb.magicshroom.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the deterministic authentication fake used by UI tests. */
class DemoAuthRepositoryTest {
    private val repository = DemoAuthRepository()

    @Test
    fun validCredentialsReturnDemoUser() = runTest {
        val session = repository.signIn(
            DemoAuthRepository.TEST_EMAIL,
            DemoAuthRepository.TEST_PASSWORD,
        )

        assertEquals("testUser1", session?.user?.displayName)
        assertEquals("USER", session?.user?.role)
        assertEquals(AuthState.SignedIn(requireNotNull(session)), repository.authState.value)
    }

    @Test
    fun invalidPasswordIsRejected() = runTest {
        val session = repository.signIn(
            DemoAuthRepository.TEST_EMAIL,
            "incorrect",
        )

        assertNull(session)
        assertEquals(AuthState.SignedOut, repository.authState.value)
    }
}
