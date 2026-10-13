package edu.csumb.magicshroom.api.importer

import edu.csumb.magicshroom.api.model.Color
import edu.csumb.magicshroom.api.model.LegalityStatus
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Checks archive-to-API mapping before any PostgreSQL import. */
class ArchiveCardMapperTest {
    private val json = JsonMapper()

    /** Maps arrays and structured details while ignoring archive-only foreign-language keys in the API view. */
    @Test
    fun `maps compact card fields and structured details`() {
        val node = json.readTree("""{
            "uuid":"8f9a35d5-a3a2-556b-88da-6686da3aaa34",
            "name":"Sample Card", "colorIdentity":["R","G"], "colors":["R"],
            "convertedManaCost":2.5, "manaCost":"{1}{R}", "types":["Creature"],
            "subtypes":["Goblin"], "supertypes":[], "printings":["ABC","DEF"],
            "foreignData":[{"language":"German","name":"Beispiel","text":"Text","type":"Kreatur","flavorText":"Extra"}],
            "legalities":{"modern":"Legal","commander":"Banned"},
            "purchaseUrls":{"cardmarket":"https://example.test/card"},
            "rulings":[{"date":"2019-01-01","text":"A ruling."}]
        }""")

        val card = ArchiveCardMapper.map(node)

        assertEquals("Sample Card", card.cardName)
        assertEquals(listOf(Color.R, Color.G), card.colorIdentity)
        assertEquals(listOf("ABC", "DEF"), card.printings)
        assertEquals("Beispiel", card.foreignData.single().name)
        assertEquals("Text", card.foreignData.single().text)
        assertEquals(LegalityStatus.BANNED, card.legalities.first { it.format == "commander" }.status)
        assertEquals("https://example.test/card", card.purchaseUrls.single().url)
        assertEquals(LocalDate.of(2019, 1, 1), card.rulings.single().date)
    }

    /** Treats missing optional fields as empty lists or null values. */
    @Test
    fun `maps sparse card without inventing optional values`() {
        val node = json.readTree("""{"uuid":"8f9a35d5-a3a2-556b-88da-6686da3aaa34","name":"Sparse"}""")
        val card = ArchiveCardMapper.map(node)
        assertEquals(emptyList(), card.colors)
        assertEquals(emptyList(), card.legalities)
        assertNull(card.convertedManaCost)
    }

    /** Rejects an unfamiliar legality instead of silently corrupting the archive mapping. */
    @Test
    fun `rejects unknown legality status`() {
        val node = json.readTree("""{"uuid":"8f9a35d5-a3a2-556b-88da-6686da3aaa34","name":"Bad","legalities":{"modern":"Maybe"}}""")
        assertFailsWith<IllegalStateException> { ArchiveCardMapper.map(node) }
    }
}
