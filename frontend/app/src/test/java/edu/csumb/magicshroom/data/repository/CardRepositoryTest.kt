package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.Color
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

/**
 * Contract tests for the API client: the requests it sends and how it reads responses. The JSON
 * bodies below were captured from the real backend (`GET /api/v1/cards?name=bolt`, `/cards/999`).
 */
class CardRepositoryTest {
    private val server = MockWebServer()
    private lateinit var repository: CardRepository
    private var accessToken: String? = "signed-test-token"

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(BearerTokenInterceptor { accessToken }).build()
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
        assertEquals("Bearer signed-test-token", request.getHeader("Authorization"))
        assertEquals(null, request.getHeader("X-Stub-User-Id"))
        val page = (result as ApiResult.Success).data
        assertEquals(1L, page.page.totalElements)
        val card = page.content.single()
        assertEquals("Lightning Bolt", card.cardName)
        assertEquals(listOf(Color.R), card.colorIdentity)
        assertEquals("Relámpago", card.foreignData.single().name)
        assertEquals("Lightning Bolt", card.imageFaces.single().name)

        assertEquals("Lightning Bolt", (repository.getCard(1) as ApiResult.Success).data.cardName)
        assertEquals(1, server.requestCount)
    }

    /** Sends one deduplicated batch request and reuses its response from the shared cache. */
    @Test
    fun `getCardsByIds batches uncached ids in order and caches results`() = runTest {
        server.enqueue(json(200, CARD_BATCH))

        val result = repository.getCardsByIds(listOf(2, 1, 2)) as ApiResult.Success

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/cards/batch", request.path)
        assertEquals("""{"cardIds":[2,1]}""", request.body.readUtf8())
        assertEquals("Bearer signed-test-token", request.getHeader("Authorization"))
        assertEquals(listOf(2L, 1L), result.data.map { it.id })
        assertEquals(listOf("Counterspell", "Lightning Bolt"), result.data.map { it.cardName })

        val cachedResult = repository.getCardsByIds(listOf(1, 2)) as ApiResult.Success
        assertEquals(listOf(1L, 2L), cachedResult.data.map { it.id })
        assertEquals(1, server.requestCount)
    }

    /** Sends only IDs missing from earlier card-list results to the bulk endpoint. */
    @Test
    fun `getCardsByIds batches only cache misses`() = runTest {
        server.enqueue(json(200, CARD_PAGE))
        server.enqueue(json(200, "[$CARD_TWO]"))

        repository.listCards()
        val result = repository.getCardsByIds(listOf(1, 2)) as ApiResult.Success

        assertEquals(listOf(1L, 2L), result.data.map { it.id })
        assertEquals("/api/v1/cards?page=0&size=20", server.takeRequest().path)
        assertEquals("""{"cardIds":[2]}""", server.takeRequest().body.readUtf8())
    }

    /** Splits oversized card collections into API-sized batches. */
    @Test
    fun `getCardsByIds chunks more than one hundred uncached ids`() = runTest {
        val ids = (1..101).map(Int::toLong)
        server.enqueue(json(200, ids.take(100).joinToString(prefix = "[", postfix = "]", transform = ::cardJson)))
        server.enqueue(json(200, "[${cardJson(101)}]"))

        val batchResult = repository.getCardsByIds(ids)
        assertTrue("Expected all batches to succeed, got $batchResult", batchResult is ApiResult.Success)
        val result = batchResult as ApiResult.Success

        assertEquals(101, result.data.size)
        val firstRequest = server.takeRequest().body.readUtf8()
        val secondRequest = server.takeRequest().body.readUtf8()
        val expectedFirstRequest = """{"cardIds":[${(1..100).joinToString(",")}]}"""
        assertEquals(expectedFirstRequest, firstRequest)
        assertEquals("""{"cardIds":[101]}""", secondRequest)
    }

    /** Requests similar cards and caches them so the comparison screen needs no second request. */
    @Test
    fun `getSimilarCards sends the limit and caches the cards`() = runTest {
        server.enqueue(json(200, "[$CARD_TWO]"))

        val result = repository.getSimilarCards(1, limit = 5) as ApiResult.Success

        assertEquals("/api/v1/cards/1/similar?limit=5", server.takeRequest().path)
        assertEquals(listOf("Counterspell"), result.data.map { it.cardName })
        assertEquals("Counterspell", (repository.getCard(2) as ApiResult.Success).data.cardName)
        assertEquals(1, server.requestCount)
    }

    /** Reads stats when the server sends them and tolerates their absence from older servers. */
    @Test
    fun `card stats are parsed when present and optional otherwise`() = runTest {
        server.enqueue(json(200, """[{"id":4,"uuid":"u4","cardName":"Serra Angel","power":"4","toughness":"4","loyalty":null,"edhrecRank":4698},$CARD_TWO]"""))

        val cards = (repository.getSimilarCards(1) as ApiResult.Success).data

        assertEquals("/api/v1/cards/1/similar?limit=10", server.takeRequest().path)
        assertEquals("4", cards[0].power)
        assertEquals("4", cards[0].toughness)
        assertEquals(4698, cards[0].edhrecRank)
        assertEquals(null, cards[1].power)
        assertEquals(null, cards[1].edhrecRank)
    }

    /** A server older than the similar route answers 404, which the screen explains. */
    @Test
    fun `getSimilarCards reports a missing route as a 404`() = runTest {
        server.enqueue(json(404, """{"title":"Not Found","status":404,"detail":"No static resource api/v1/cards/1/similar."}"""))

        val result = repository.getSimilarCards(1)

        assertEquals(404, (result as ApiResult.HttpError).status)
    }

    @Test
    fun `uses the latest refreshed access token for every request`() = runTest {
        server.enqueue(json(200, CARD_PAGE))
        repository.listCards()
        assertEquals("Bearer signed-test-token", server.takeRequest().getHeader("Authorization"))

        accessToken = "refreshed-test-token"
        server.enqueue(json(200, CARD_PAGE))
        repository.listCards()
        assertEquals("Bearer refreshed-test-token", server.takeRequest().getHeader("Authorization"))
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

    /** Builds a minimal valid card response for an API ID. */
    private fun cardJson(id: Long) = """{"id":$id,"uuid":"uuid-$id","cardName":"Card $id"}"""

    private companion object {
        const val CARD_PAGE = """{"content":[{"id":1,"uuid":"0318d56b-647f-4b0f-9306-9adc3312619d","cardName":"Lightning Bolt","colorIdentity":["R"],"colors":["R"],"convertedManaCost":1.0,"manaCost":"{R}","scryfallOracleId":null,"text":"Lightning Bolt deals 3 damage to any target.","types":["Instant"],"subtypes":[],"supertypes":[],"printings":["LEA","M10","2XM"],"foreignData":[{"language":"Spanish","name":"Relámpago","text":null}],"imageFaces":[{"name":"Lightning Bolt","smallUrl":"https://cards.scryfall.io/small/front/6/7/example.jpg","normalUrl":"https://cards.scryfall.io/normal/front/6/7/example.jpg"}],"legalities":[{"format":"modern","status":"LEGAL"},{"format":"legacy","status":"LEGAL"},{"format":"vintage","status":"LEGAL"},{"format":"commander","status":"LEGAL"}],"purchaseUrls":[{"vendor":"example","url":"https://example.com/cards/lightning-bolt"}],"rulings":[]}],"page":{"size":20,"number":0,"totalElements":1,"totalPages":1}}"""
        const val NOT_FOUND_PROBLEM = """{"detail":"Card 999 was not found.","instance":"/api/v1/cards/999","status":404,"title":"Not Found"}"""
        const val CARD_BATCH = """[{"id":2,"uuid":"a318d56b-647f-4b0f-9306-9adc3312619d","cardName":"Counterspell"},{"id":1,"uuid":"0318d56b-647f-4b0f-9306-9adc3312619d","cardName":"Lightning Bolt"}]"""
        const val CARD_TWO = """{"id":2,"uuid":"a318d56b-647f-4b0f-9306-9adc3312619d","cardName":"Counterspell"}"""
    }
}
