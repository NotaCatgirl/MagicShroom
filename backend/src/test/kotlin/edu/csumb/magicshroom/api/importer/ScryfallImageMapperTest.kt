package edu.csumb.magicshroom.api.importer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files

/** Checks representative and multi-face Scryfall scans without network access. */
class ScryfallImageMapperTest {
    private val json = JsonMapper()

    /** Scryfall's uncompressed JSON Lines export is parsed one card object per line. */
    @Test
    fun `reader accepts JSON Lines and skips blank lines`() {
        val path = Files.createTempFile("scryfall-oracle-cards", ".jsonl")
        try {
            Files.writeString(path, """{"name":"First"}

                {"name":"Second"}
            """.trimIndent())

            val cards = mutableListOf<String>()
            ScryfallImageExportReader.forEachCard(path, json) { cards += it.path("name").asString() }

            assertEquals(listOf("First", "Second"), cards)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    /** The reader remains compatible with a JSON-array export, including leading whitespace. */
    @Test
    fun `reader accepts JSON array`() {
        val path = Files.createTempFile("scryfall-oracle-cards", ".json")
        try {
            Files.writeString(path, """
                [
                  {"name":"First"},
                  {"name":"Second"}
                ]
            """.trimIndent())

            val cards = mutableListOf<String>()
            ScryfallImageExportReader.forEachCard(path, json) { cards += it.path("name").asString() }

            assertEquals(listOf("First", "Second"), cards)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    /** A single card image appears as one display face. */
    @Test
    fun `single face has small and normal card scans`() {
        val image = ScryfallImageMapper.map(json.readTree("""{
          "oracle_id":"4457ed35-7c10-48c8-9776-456485fdf070",
          "id":"67f4c93b-080c-4196-b095-6a120a221988", "set":"m10", "name":"Lightning Bolt",
          "image_uris":{"small":"https://cards.scryfall.io/small/front/6/7/67f4c93b.jpg",
          "normal":"https://cards.scryfall.io/normal/front/6/7/67f4c93b.jpg"}
        }"""))
        assertEquals(1, image?.faces?.size)
        assertEquals("Lightning Bolt", image?.faces?.single()?.name)
        assertEquals("m10", image?.setCode)
    }

    /** Double-faced cards preserve both image faces in Scryfall order. */
    @Test
    fun `separate face scans preserve order`() {
        val image = ScryfallImageMapper.map(json.readTree("""{
          "oracle_id":"4457ed35-7c10-48c8-9776-456485fdf070",
          "id":"67f4c93b-080c-4196-b095-6a120a221988", "set":"mid",
          "card_faces":[
            {"name":"Front","image_uris":{"small":"https://cards.scryfall.io/small/front/a.jpg","normal":"https://cards.scryfall.io/normal/front/a.jpg"}},
            {"name":"Back","image_uris":{"small":"https://cards.scryfall.io/small/back/a.jpg","normal":"https://cards.scryfall.io/normal/back/a.jpg"}}
          ]
        }"""))
        assertEquals(listOf("Front", "Back"), image?.faces?.map { it.name })
    }

    /** Broken or unrelated URLs are never exposed as card images. */
    @Test
    fun `missing or non Scryfall scans are skipped`() {
        val image = ScryfallImageMapper.map(json.readTree("""{
          "oracle_id":"4457ed35-7c10-48c8-9776-456485fdf070",
          "id":"67f4c93b-080c-4196-b095-6a120a221988", "set":"m10", "name":"Lightning Bolt",
          "image_uris":{"small":"https://example.com/card.jpg","normal":"https://cards.scryfall.io/normal/front/a.jpg"}
        }"""))
        assertNull(image)
    }
}
