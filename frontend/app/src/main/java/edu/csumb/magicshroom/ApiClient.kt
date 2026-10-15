package edu.csumb.magicshroom

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** The API's minimal card listing model. */
data class CardSummary(val id: Long, val name: String, val text: String?, val manaCost: String?)

/** The API's minimal deck model. */
data class DeckSummary(val id: Long, val name: String, val cards: List<DeckEntry>)

/** A card count and section in a deck. */
data class DeckEntry(val cardId: Long, val quantity: Int, val section: String)

/** HTTP client for public cards and authenticated personal decks. */
class ApiClient(private val baseUrl: String = BuildConfig.BACKEND_URL) {
    /** Searches the public catalog with server-side paging. */
    suspend fun cards(name: String, page: Int = 0): List<CardSummary> {
        val query = if (name.isBlank()) "" else "&name=${Uri.encode(name.trim())}"
        val content = request("GET", "/api/v1/cards?page=$page&size=20$query", null, null)
            .getJSONArray("content")
        return (0 until content.length()).map { content.getJSONObject(it).toCard() }
    }

    /** Lists decks for the current Supabase access token. */
    suspend fun decks(token: String): List<DeckSummary> {
        val array = requestArray("/api/v1/decks", token)
        return (0 until array.length()).map { array.getJSONObject(it).toDeck() }
    }

    /** Creates an empty personal deck. */
    suspend fun createDeck(token: String, name: String): DeckSummary =
        request("POST", "/api/v1/decks", token, JSONObject().put("name", name.trim())).toDeck()

    /** Adds a card to a personal deck. */
    suspend fun addCard(token: String, deckId: Long, cardId: Long) {
        request("POST", "/api/v1/decks/$deckId/cards", token, JSONObject().put("cardId", cardId))
    }

    /** Removes one card from a personal deck. */
    suspend fun removeCard(token: String, deckId: Long, cardId: Long) {
        request("DELETE", "/api/v1/decks/$deckId/cards/$cardId", token, null)
    }

    /** Decodes an API card without changing its server response shape. */
    private fun JSONObject.toCard() = CardSummary(getLong("id"), getString("cardName"), optString("text").takeIf(String::isNotBlank), optString("manaCost").takeIf(String::isNotBlank))

    /** Decodes one deck and its entries. */
    private fun JSONObject.toDeck(): DeckSummary {
        val entries = getJSONArray("cards")
        return DeckSummary(getLong("id"), getString("name"), (0 until entries.length()).map {
            val item = entries.getJSONObject(it)
            DeckEntry(item.getLong("cardId"), item.getInt("quantity"), item.getString("section"))
        })
    }

    /** Executes a request with the session access token in the Authorization header. */
    private suspend fun request(method: String, path: String, token: String?, body: JSONObject?): JSONObject =
        JSONObject(execute(method, path, token, body).ifBlank { "{}" })

    /** Executes a request whose response is a JSON array. */
    private suspend fun requestArray(path: String, token: String): JSONArray =
        JSONArray(execute("GET", path, token, null))

    /** Performs blocking network IO off the UI thread and reports API errors. */
    private suspend fun execute(method: String, path: String, token: String?, body: JSONObject?): String = withContext(Dispatchers.IO) {
        val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) {
                val detail = runCatching { JSONObject(response).optString("detail") }.getOrNull().orEmpty()
                error(detail.ifBlank { "Request failed (${connection.responseCode})" })
            }
            response
        } finally {
            connection.disconnect()
        }
    }
}
