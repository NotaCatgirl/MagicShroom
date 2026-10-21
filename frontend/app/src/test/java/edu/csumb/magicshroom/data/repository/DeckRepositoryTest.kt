package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.BearerTokenInterceptor
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

/** Contract tests for resolving deck entries with a single bulk card lookup. */
class DeckRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: DeckRepository

    /** Creates the repositories over an isolated API server and shared card cache. */
    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(BearerTokenInterceptor { "signed-test-token" })
            .build()
        val api = createMagicShroomApi(server.url("/").toString(), client)
        repository = DeckRepository(api, CardRepository(api))
    }

    /** Stops the isolated API server after each repository test. */
    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Fetches deck entries with two HTTP calls regardless of overlap between decks. */
    @Test
    fun `lists decks and resolves all unique cards in one batch`() = runTest {
        server.enqueue(json(200, DECKS))
        server.enqueue(json(200, CARDS))

        val result = repository.listMyDecksWithCards()

        assertTrue(result is ApiResult.Success)
        val decks = (result as ApiResult.Success).data
        assertEquals(listOf("Burn", "Commander"), decks.map { it.name })
        assertEquals("Counterspell", decks[0].cards[0].card.cardName)
        assertEquals(4, decks[0].cards[0].quantity)
        assertEquals("Lightning Bolt", decks[0].cards[1].card.cardName)
        assertEquals("Counterspell", decks[1].cards.single().card.cardName)

        val decksRequest = server.takeRequest()
        assertEquals("/api/v1/decks", decksRequest.path)
        assertEquals("Bearer signed-test-token", decksRequest.getHeader("Authorization"))
        val batchRequest = server.takeRequest()
        assertEquals("POST", batchRequest.method)
        assertEquals("/api/v1/cards/batch", batchRequest.path)
        assertEquals("""{"cardIds":[10,11]}""", batchRequest.body.readUtf8())
        assertEquals(2, server.requestCount)
    }

    /** Builds a JSON response for one isolated API call. */
    private fun json(status: Int, body: String) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val DECKS = """[
            {"id":3,"userId":7,"name":"Burn","cards":[
                {"cardId":10,"quantity":4,"section":"MAINBOARD"},
                {"cardId":11,"quantity":2,"section":"SIDEBOARD"}
            ]},
            {"id":4,"userId":7,"name":"Commander","cards":[
                {"cardId":10,"quantity":1,"section":"COMMANDER"}
            ]}
        ]"""
        const val CARDS = """[
            {"id":10,"uuid":"a318d56b-647f-4b0f-9306-9adc3312619d","cardName":"Counterspell"},
            {"id":11,"uuid":"0318d56b-647f-4b0f-9306-9adc3312619d","cardName":"Lightning Bolt"}
        ]"""
    }
}
