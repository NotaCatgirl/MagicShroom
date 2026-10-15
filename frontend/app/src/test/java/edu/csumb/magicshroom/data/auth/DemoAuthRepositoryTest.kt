package edu.csumb.magicshroom.data.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the temporary local authentication behavior. */
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
        assertEquals(session, repository.currentSession)
    }

    @Test
    fun invalidPasswordIsRejected() = runTest {
        val session = repository.signIn(
            DemoAuthRepository.TEST_EMAIL,
            "incorrect",
        )

        assertNull(session)
        assertNull(repository.currentSession)
    }
}
