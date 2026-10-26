package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.error.ServiceUnavailableException
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.test.assertFailsWith

/** Verifies server-only credentials and safe failure mapping for the Supabase Auth admin API. */
class SupabaseAuthAdminClientTest {
    /** New secret keys authenticate through apikey without being treated as bearer JWTs. */
    @Test
    fun `secret key is sent only as apikey and surrounding whitespace is removed`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            start()
        }
        try {
            val userId = UUID.randomUUID()
            val client = SupabaseRestAuthAdminClient(server.url("/auth/v1").toString().trimEnd('/'), " sb_secret_test_key \n")

            client.deleteUser(userId)

            val request = server.takeRequest()
            assertEquals("DELETE", request.method)
            assertEquals("/auth/v1/admin/users/$userId", request.path)
            assertEquals("sb_secret_test_key", request.getHeader("apikey"))
            assertNull(request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    /** Existing service_role JWT deployments keep their bearer authentication. */
    @Test
    fun `legacy JWT key is also sent as bearer`() {
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            start()
        }
        try {
            val client = SupabaseRestAuthAdminClient(server.url("/auth/v1").toString().trimEnd('/'), "eyJlegacy.payload.signature")

            client.deleteUser(UUID.randomUUID())

            val request = server.takeRequest()
            assertEquals("eyJlegacy.payload.signature", request.getHeader("apikey"))
            assertEquals("Bearer eyJlegacy.payload.signature", request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

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
            val client = SupabaseRestAuthAdminClient(server.url("/auth/v1").toString().trimEnd('/'), "sb_secret_test_key")
            assertFailsWith<ServiceUnavailableException> { client.deleteUser(userId) }
            val request = server.takeRequest()
            assertEquals("/auth/v1/admin/users/$userId", request.path)
            assertEquals("sb_secret_test_key", request.getHeader("apikey"))
            assertNull(request.getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    /** Diagnoses upstream failures without leaking the key or free-form response into logs. */
    @Test
    fun `upstream status and error code are logged without credentials or response details`() {
        val key = "sb_secret_test_key"
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(401).setBody("""{"error_code":"bad_jwt","msg":"private response containing $key"}"""))
            start()
        }
        val logger = LoggerFactory.getLogger(SupabaseRestAuthAdminClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val client = SupabaseRestAuthAdminClient(server.url("/auth/v1").toString().trimEnd('/'), key)

            val error = assertFailsWith<ServiceUnavailableException> { client.deleteUser(UUID.randomUUID()) }

            assertEquals("Supabase Auth could not delete the account.", error.message)
            val event = appender.list.single()
            assertTrue(event.formattedMessage.contains("HTTP 401"))
            assertTrue(event.formattedMessage.contains("error_code=bad_jwt"))
            assertFalse(event.formattedMessage.contains(key))
            assertFalse(event.formattedMessage.contains("private response"))
            assertNull(event.throwableProxy)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            server.shutdown()
        }
    }

    /** An echoed key is never accepted as a safe error identifier. */
    @Test
    fun `credentials in the error code are omitted from diagnostics`() {
        val key = "sb_secret_test_key"
        val server = MockWebServer().apply {
            enqueue(MockResponse().setResponseCode(401).setBody("""{"error_code":"$key"}"""))
            start()
        }
        val logger = LoggerFactory.getLogger(SupabaseRestAuthAdminClient::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            val client = SupabaseRestAuthAdminClient(server.url("/auth/v1").toString().trimEnd('/'), key)

            assertFailsWith<ServiceUnavailableException> { client.deleteUser(UUID.randomUUID()) }

            assertTrue(appender.list.single().formattedMessage.contains("error_code=unknown"))
            assertFalse(appender.list.single().formattedMessage.contains(key))
        } finally {
            logger.detachAppender(appender)
            appender.stop()
            server.shutdown()
        }
    }
}
