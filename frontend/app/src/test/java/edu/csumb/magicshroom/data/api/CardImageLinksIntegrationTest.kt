package edu.csumb.magicshroom.data.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Integration check that the live card API returns links to card scans. */
class CardImageLinksIntegrationTest {
    @Test
    fun apiReturnsImageLinksForLightningBolt() = runBlocking {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .callTimeout(210, TimeUnit.SECONDS)
            .build()
        val api = createMagicShroomApi(testApiBaseUrl, client)

        val searchCard = api.listCards(name = "Lightning Bolt", page = 0, size = 10)
            .content.firstOrNull { it.imageFaces.isNotEmpty() }
            ?: error("The API returned no Lightning Bolt card with image faces")
        val card = api.getCard(searchCard.id)

        assertEquals(searchCard.id, card.id)
        assertFalse("The card API returned no image faces", card.imageFaces.isEmpty())
        assertTrue(
            "Every card face should include HTTP(S) small and normal image links",
            card.imageFaces.all { face ->
                face.smallUrl.isHttpUrl() && face.normalUrl.isHttpUrl()
            },
        )
    }

    private fun String.isHttpUrl(): Boolean = startsWith("https://") || startsWith("http://")

    private companion object {
        // Flip this to true when the API is running locally on port 8080.
        const val USE_LOCAL_API = false
        const val LOCAL_API_BASE_URL = "http://localhost:8080/"
        const val RENDER_API_BASE_URL = "https://magicshroom.onrender.com/"
        val testApiBaseUrl = if (USE_LOCAL_API) LOCAL_API_BASE_URL else RENDER_API_BASE_URL
    }
}
