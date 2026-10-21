package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.error.ServiceUnavailableException
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

/** Verifies server-only credentials and safe failure mapping for the Supabase Auth admin API. */
class SupabaseAuthAdminClientTest {
    /** Refuses deletion when the server-side Supabase admin key has not been configured. */
    @Test
    fun `missing admin key reports service unavailable without making a request`() {
        val server = MockWebServer().apply { start() }
        try {
            val client = SupabaseRestAuthAdminClient(server.url("/auth/v1").toString().trimEnd('/'), " ")
            assertFailsWith<ServiceUnavailableException> { client.deleteUser(UUID.randomUUID()) }
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    /** Sends the admin key only in server-side headers and maps an Auth API error to 503. */
    @Test
    fun `supabase admin errors become service unavailable`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(500))
            start()
        }
        try {
            val userId = UUID.randomUUID()
            val client = SupabaseRestAuthAdminClient(server.url("/auth/v1").toString().trimEnd('/'), "server-only-key")
            assertFailsWith<ServiceUnavailableException> { client.deleteUser(userId) }
            val request = server.takeRequest()
            assertEquals("/auth/v1/admin/users/$userId", request.path)
            assertEquals("server-only-key", request.getHeader("apikey"))
            assertEquals("Bearer server-only-key", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }
}
