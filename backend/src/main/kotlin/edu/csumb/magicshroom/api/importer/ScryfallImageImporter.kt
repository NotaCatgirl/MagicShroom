package edu.csumb.magicshroom.api.importer

import edu.csumb.magicshroom.api.model.CardImageFace
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.sql.PreparedStatement
import java.util.UUID

/** One Scryfall printing chosen for a card's Oracle identity. */
internal data class ScryfallImage(
    val oracleId: UUID,
    val cardId: UUID,
    val setCode: String,
    val faces: List<CardImageFace>,
)

/** Extracts actual full-card image URLs from Scryfall's Oracle Cards bulk export. */
internal object ScryfallImageMapper {
    /** Returns null when an entry has no usable scan or Oracle identity. */
    fun map(node: JsonNode): ScryfallImage? {
        val oracleId = node.path("oracle_id").asString().let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val cardId = node.path("id").asString().let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val setCode = node.path("set").asString().takeIf(String::isNotBlank) ?: return null
        val topLevel = face(node.path("name").asString(), node.path("image_uris"))
        val faces = if (topLevel != null) listOf(topLevel) else node.path("card_faces").toList().mapNotNull {
            face(it.path("name").asString(), it.path("image_uris"))
        }
        if (faces.isEmpty()) return null
        return ScryfallImage(oracleId, cardId, setCode, faces)
    }

    /** Returns a named display face only when both Scryfall HTTPS image URLs are usable. */
    private fun face(name: String, uris: JsonNode): CardImageFace? {
        val small = uris.path("small").asString()
        val normal = uris.path("normal").asString()
        if (name.isBlank() || !validImageUrl(small) || !validImageUrl(normal)) return null
        return CardImageFace(name, small, normal)
    }

    /** Checks a network URL before exposing it from our public API. */
    private fun validImageUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme == "https" && uri.host == "cards.scryfall.io" && uri.path.isNotBlank()
    }.getOrDefault(false)
}

/** Reads either the traditional JSON-array export or Scryfall's newline-delimited JSON export. */
internal object ScryfallImageExportReader {
    /** Streams each card object from a JSON-array or newline-delimited Scryfall export. */
    fun forEachCard(path: Path, json: JsonMapper, consume: (JsonNode) -> Unit) {
        Files.newBufferedReader(path).use { reader ->
            reader.mark(1)
            var firstCharacter = reader.read()
            while (firstCharacter >= 0 && firstCharacter.toChar().isWhitespace()) {
                reader.mark(1)
                firstCharacter = reader.read()
            }
            reader.reset()
            if (firstCharacter.toChar() == '[') {
                val root = json.readTree(reader)
                require(root.isArray) { "Scryfall Oracle Cards export must be a JSON array or JSON Lines" }
                root.forEach(consume)
            } else {
                reader.lineSequence().filter(String::isNotBlank).forEach { consume(json.readTree(it)) }
            }
        }
    }
}

/** Manually imports a Scryfall Oracle Cards JSON or JSON Lines file; normal startup never downloads images. */
@Component
@Profile("!mock")
class ScryfallImageImporter(
    private val jdbc: JdbcTemplate,
    private val json: JsonMapper,
    private val transactions: TransactionTemplate,
    @Value("\${magicshroom.images.import.path:}") private val importPath: String,
) : ApplicationRunner {
    /** Upserts image metadata for cards already present in this database. */
    override fun run(args: ApplicationArguments) {
        if (importPath.isBlank()) return
        val oracleIds = jdbc.queryForList("select distinct scryfall_oracle_id from cards where scryfall_oracle_id is not null", UUID::class.java).toSet()
        val images = mutableListOf<ScryfallImage>()
        ScryfallImageExportReader.forEachCard(Path.of(importPath), json) { node ->
            ScryfallImageMapper.map(node)?.takeIf { it.oracleId in oracleIds }?.let(images::add)
        }
        require(images.map(ScryfallImage::oracleId).distinct().size == images.size) { "Duplicate Scryfall Oracle IDs in image export" }
        require(images.isNotEmpty()) { "No image entries match catalog Oracle IDs" }
        transactions.executeWithoutResult {
            images.chunked(500).forEach { batch ->
                jdbc.batchUpdate(
                    """insert into card_images (oracle_id, scryfall_card_id, set_code, faces, synced_at)
                        values (?, ?, ?, ?::jsonb, now())
                        on conflict (oracle_id) do update set scryfall_card_id = excluded.scryfall_card_id,
                        set_code = excluded.set_code, faces = excluded.faces, synced_at = now()""",
                    object : BatchPreparedStatementSetter {
                        /** Binds one image relation in the current batch. */
                        override fun setValues(ps: PreparedStatement, i: Int) {
                            val image = batch[i]
                            ps.setObject(1, image.oracleId)
                            ps.setObject(2, image.cardId)
                            ps.setString(3, image.setCode)
                            ps.setString(4, json.writeValueAsString(image.faces))
                        }

                        /** The number of image relations in the current batch. */
                        override fun getBatchSize(): Int = batch.size
                    },
                )
            }
        }
        println("Scryfall image import: ${images.size} of ${oracleIds.size} Oracle IDs matched; ${oracleIds.size - images.size} unmatched")
    }

}
