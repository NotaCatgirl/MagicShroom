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

    /** Exercises every deck mutation and verifies each Retrofit route and request body. */
    @Test
    fun `deck create detail rename and card mutations call expected routes`() = runTest {
        server.enqueue(json(201, EMPTY_DECK))
        server.enqueue(json(200, EMPTY_DECK_RENAMED))
        server.enqueue(json(200, DECK_WITH_CARD))
        server.enqueue(json(200, CARDS))
        server.enqueue(json(201, """{"cardId":10,"quantity":2,"section":"MAINBOARD"}"""))
        server.enqueue(json(200, """{"cardId":10,"quantity":3,"section":"SIDEBOARD"}"""))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))

        assertTrue(repository.createDeck("  Burn  ") is ApiResult.Success)
        assertTrue(repository.renameDeck(3, "  Burn v2  ") is ApiResult.Success)
        val detail = repository.getMyDeckWithCards(3)
        assertTrue(detail is ApiResult.Success)
        assertEquals("Lightning Bolt", (detail as ApiResult.Success).data.cards.single().card.cardName)
        assertTrue(repository.addCard(3, 10, 2, edu.csumb.magicshroom.data.api.DeckSection.MAINBOARD) is ApiResult.Success)
        assertTrue(
            repository.updateCard(
                3,
                10,
                edu.csumb.magicshroom.data.api.DeckCardPatchDto(quantity = 3),
            ) is ApiResult.Success,
        )
        assertTrue(repository.removeCard(3, 10) is ApiResult.Success)
        assertTrue(repository.deleteDeck(3) is ApiResult.Success)

        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertEquals("/api/v1/decks", create.path)
        assertEquals("""{"name":"Burn"}""", create.body.readUtf8())
        val rename = server.takeRequest()
        assertEquals("PATCH", rename.method)
        assertEquals("/api/v1/decks/3", rename.path)
        assertEquals("""{"name":"Burn v2"}""", rename.body.readUtf8())
        val detailRequest = server.takeRequest()
        assertEquals("GET", detailRequest.method)
        assertEquals("/api/v1/decks/3", detailRequest.path)
        val batchRequest = server.takeRequest()
        assertEquals("POST", batchRequest.method)
        assertEquals("/api/v1/cards/batch", batchRequest.path)
        val add = server.takeRequest()
        assertEquals("POST", add.method)
        assertEquals("/api/v1/decks/3/cards", add.path)
        assertEquals("""{"cardId":10,"quantity":2}""", add.body.readUtf8())
        val update = server.takeRequest()
        assertEquals("PATCH", update.method)
        assertEquals("/api/v1/decks/3/cards/10", update.path)
        assertEquals("""{"quantity":3}""", update.body.readUtf8())
        val removeCard = server.takeRequest()
        assertEquals("DELETE", removeCard.method)
        assertEquals("/api/v1/decks/3/cards/10", removeCard.path)
        val deleteDeck = server.takeRequest()
        assertEquals("DELETE", deleteDeck.method)
        assertEquals("/api/v1/decks/3", deleteDeck.path)
        assertEquals(8, server.requestCount)
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
        const val EMPTY_DECK = """{"id":3,"userId":7,"name":"Burn","cards":[]}"""
        const val EMPTY_DECK_RENAMED = """{"id":3,"userId":7,"name":"Burn v2","cards":[]}"""
        const val DECK_WITH_CARD = """{"id":3,"userId":7,"name":"Burn v2","cards":[
            {"cardId":11,"quantity":2,"section":"MAINBOARD"}
        ]}"""
    }
}
