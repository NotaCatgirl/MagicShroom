package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.Color
import edu.csumb.magicshroom.data.api.StubAuthInterceptor
import edu.csumb.magicshroom.data.api.createMagicShroomApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Contract tests for the API client: the requests it sends and how it reads responses. The JSON
 * bodies below were captured from the real backend (`GET /api/v1/cards?name=bolt`, `/cards/999`).
 */
class CardRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: CardRepository

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(StubAuthInterceptor("2")).build()
        repository = CardRepository(createMagicShroomApi(server.url("/").toString(), client))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listCards sends filters as query parameters and parses the page`() = runTest {
        server.enqueue(json(200, CARD_PAGE))

        val result = repository.listCards(name = "bolt", color = Color.R)

        val request = server.takeRequest()
        assertEquals("/api/v1/cards?name=bolt&color=R&page=0&size=20", request.path)
        assertEquals("2", request.getHeader(StubAuthInterceptor.HEADER))
        val page = (result as ApiResult.Success).data
        assertEquals(1L, page.page.totalElements)
        val card = page.content.single()
        assertEquals("Lightning Bolt", card.cardName)
        assertEquals(listOf(Color.R), card.colorIdentity)
        assertEquals("Relámpago", card.foreignData.single().name)
        assertEquals("Lightning Bolt", card.imageFaces.single().name)
    }

    @Test
    fun `404 keeps the server's problem detail for display`() = runTest {
        server.enqueue(json(404, NOT_FOUND_PROBLEM))

        val result = repository.getCard(999)

        assertEquals(ApiResult.HttpError(404, "Card 999 was not found."), result)
        assertEquals("Card 999 was not found.", (result as ApiResult.HttpError).userMessage)
    }

    @Test
    fun `401 and 500 map to friendly messages`() = runTest {
        server.enqueue(json(401, """{"title":"Unauthorized","status":401}"""))
        server.enqueue(json(500, """{"title":"Internal Server Error","status":500}"""))

        val unauthorized = repository.getCard(1) as ApiResult.HttpError
        val serverError = repository.getCard(1) as ApiResult.HttpError

        assertEquals("Please sign in again.", unauthorized.userMessage)
        assertEquals("The server ran into a problem. Please try again.", serverError.userMessage)
    }

    @Test
    fun `an unreachable server is a network error, not a crash`() = runTest {
        server.shutdown()

        val result = repository.listCards()

        assertTrue(result is ApiResult.NetworkError)
    }

    private fun json(status: Int, body: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", if (status < 400) "application/json" else "application/problem+json")
        .setBody(body)

    private companion object {
        const val CARD_PAGE = """{"content":[{"id":1,"uuid":"0318d56b-647f-4b0f-9306-9adc3312619d","cardName":"Lightning Bolt","colorIdentity":["R"],"colors":["R"],"convertedManaCost":1.0,"manaCost":"{R}","scryfallOracleId":null,"text":"Lightning Bolt deals 3 damage to any target.","types":["Instant"],"subtypes":[],"supertypes":[],"printings":["LEA","M10","2XM"],"foreignData":[{"language":"Spanish","name":"Relámpago","text":null}],"imageFaces":[{"name":"Lightning Bolt","smallUrl":"https://cards.scryfall.io/small/front/6/7/example.jpg","normalUrl":"https://cards.scryfall.io/normal/front/6/7/example.jpg"}],"legalities":[{"format":"modern","status":"LEGAL"},{"format":"legacy","status":"LEGAL"},{"format":"vintage","status":"LEGAL"},{"format":"commander","status":"LEGAL"}],"purchaseUrls":[{"vendor":"example","url":"https://example.com/cards/lightning-bolt"}],"rulings":[]}],"page":{"size":20,"number":0,"totalElements":1,"totalPages":1}}"""
        const val NOT_FOUND_PROBLEM = """{"detail":"Card 999 was not found.","instance":"/api/v1/cards/999","status":404,"title":"Not Found"}"""
    }
}
