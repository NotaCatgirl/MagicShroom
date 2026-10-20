package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.Role
import edu.csumb.magicshroom.data.api.createMagicShroomApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Contract tests for the admin calls: the exact requests sent and how responses are read. */
class UserRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: UserRepository

    @Before
    fun setUp() {
        server.start()
        repository = ApiUserRepository(createMagicShroomApi(server.url("/").toString()))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listUsers pages through the admin route and reads the page`() = runTest {
        server.enqueue(json(200, """{"content":[{"id":1,"email":"admin@example.com","displayName":"Admin Shroom","role":"ADMIN"}],"page":{"size":20,"number":0,"totalElements":1,"totalPages":1}}"""))

        val page = (repository.listUsers(page = 0) as ApiResult.Success).data

        assertEquals("/api/v1/users?page=0&size=20", server.takeRequest().path)
        assertEquals(Role.ADMIN, page.content.single().role)
    }

    @Test
    fun `updateRole sends PATCH with the role body`() = runTest {
        server.enqueue(json(200, """{"id":2,"email":"player.one@example.com","displayName":"Player One","role":"ADMIN"}"""))

        val user = (repository.updateRole(2, Role.ADMIN) as ApiResult.Success).data

        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/v1/users/2", request.path)
        assertEquals("""{"role":"ADMIN"}""", request.body.readUtf8())
        assertEquals(Role.ADMIN, user.role)
    }

    @Test
    fun `last-admin conflict surfaces the server's explanation`() = runTest {
        server.enqueue(json(409, """{"title":"Conflict","status":409,"detail":"At least one admin must remain."}"""))

        val result = repository.updateRole(1, Role.USER) as ApiResult.HttpError

        assertEquals("At least one admin must remain.", result.userMessage)
    }

    @Test
    fun `deleteUser sends DELETE and accepts 204`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = repository.deleteUser(3)

        val request = server.takeRequest()
        assertEquals("DELETE", request.method)
        assertEquals("/api/v1/users/3", request.path)
        assertEquals(ApiResult.Success(Unit), result)
    }

    private fun json(status: Int, body: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", if (status < 400) "application/json" else "application/problem+json")
        .setBody(body)
}
