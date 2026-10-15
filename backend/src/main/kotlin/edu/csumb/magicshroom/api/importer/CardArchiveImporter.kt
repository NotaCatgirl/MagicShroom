package edu.csumb.magicshroom.api.importer

import edu.csumb.magicshroom.api.repository.CardRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

/** Explicit, one-time archive import with a PostgreSQL database-size gate. */
@Component
@Profile("!mock")
class CardArchiveImporter(
    private val cards: CardRepository,
    private val jdbc: JdbcTemplate,
    private val json: JsonMapper,
    private val transactions: TransactionTemplate,
    @Value("\${magicshroom.import.path:}") private val archivePath: String,
    @Value("\${magicshroom.import.mode:measure}") private val mode: String,
    @Value("\${magicshroom.import.measured-total-bytes:0}") private val measuredTotalBytes: Long,
) : ApplicationRunner {
    /** Imports only when a path was explicitly supplied; measure mode always rolls back. */
    override fun run(args: ApplicationArguments) {
        if (archivePath.isBlank()) return
        require(mode == "measure" || mode == "commit") { "Import mode must be measure or commit" }
        if (mode == "commit") require(measuredTotalBytes in 1..MAX_DATABASE_BYTES) {
            "Measure the complete temporary PostgreSQL database first; it must be at most $MAX_DATABASE_BYTES bytes"
        }
        val root = Files.newInputStream(Path.of(archivePath)).use { json.readTree(it) }
        require(root.isObject) { "AllCards.json must be an object keyed by card name" }
        val count = root.properties().size
        val finalSize = transactions.execute {
            require(jdbc.queryForObject("select count(*) from cards", Long::class.java) == 0L) {
                "The card table must be empty before archive import"
            }
            root.properties().forEach { (_, value) ->
                val saved = cards.insert(ArchiveCardMapper.map(value))
                preserveArchiveFields(saved.id, value)
            }
            val bytes = jdbc.queryForObject("select pg_database_size(current_database())", Long::class.java) ?: 0L
            val tableBytes = jdbc.queryForObject("select pg_total_relation_size('cards')", Long::class.java) ?: 0L
            println("Imported $count cards; database=$bytes bytes, cards table and indexes=$tableBytes bytes")
            require(bytes <= MAX_DATABASE_BYTES) { "Database exceeds the 400 MB import threshold" }
            if (mode == "measure") it.setRollbackOnly()
            bytes
        }
        println("Archive ${if (mode == "measure") "measurement rolled back" else "committed"}; measured database size=$finalSize bytes")
    }

    /** Stores archive-only scalar fields and the complete foreign-language payload. */
    private fun preserveArchiveFields(id: Long, node: JsonNode) {
        val sql = """update cards set foreign_data = ?::jsonb, color_indicator = ?, edhrec_rank = ?,
            face_converted_mana_cost = ?, hand = ?, has_no_deck_limit = ?, is_reserved = ?, layout = ?,
            leadership_skills = ?::jsonb, life = ?, loyalty = ?, mtg_arena_id = ?, mtgo_foil_id = ?,
            mtgo_id = ?, names = ?, power = ?, side = ?, toughness = ?, card_type = ? where id = ?"""
        jdbc.update { connection -> connection.prepareStatement(sql).also { ps ->
            ps.setString(1, node.path("foreignData").toString())
            ps.setArray(2, connection.createArrayOf("text", node.path("colorIndicator").toList().map { it.asString() }.toTypedArray()))
            ps.setObject(3, node.path("edhrecRank").intOrNull())
            ps.setObject(4, node.path("faceConvertedManaCost").doubleOrNull())
            ps.setString(5, node.path("hand").textOrNull())
            ps.setObject(6, node.path("hasNoDeckLimit").booleanOrNull())
            ps.setObject(7, node.path("isReserved").booleanOrNull())
            ps.setString(8, node.path("layout").textOrNull())
            ps.setString(9, node.path("leadershipSkills").takeUnless { it.isMissingNode || it.isNull }?.toString())
            ps.setString(10, node.path("life").textOrNull())
            ps.setString(11, node.path("loyalty").textOrNull())
            ps.setObject(12, node.path("mtgArenaId").intOrNull())
            ps.setObject(13, node.path("mtgoFoilId").intOrNull())
            ps.setObject(14, node.path("mtgoId").intOrNull())
            ps.setArray(15, connection.createArrayOf("text", node.path("names").toList().map { it.asString() }.toTypedArray()))
            ps.setString(16, node.path("power").textOrNull())
            ps.setString(17, node.path("side").textOrNull())
            ps.setString(18, node.path("toughness").textOrNull())
            ps.setString(19, node.path("type").textOrNull())
            ps.setLong(20, id)
        } }
    }

    /** Reads an optional archive integer. */
    private fun JsonNode.intOrNull(): Int? = takeUnless { it.isMissingNode || it.isNull }?.asInt()

    /** Reads an optional archive floating point value. */
    private fun JsonNode.doubleOrNull(): Double? = takeUnless { it.isMissingNode || it.isNull }?.asDouble()

    /** Reads an optional archive boolean. */
    private fun JsonNode.booleanOrNull(): Boolean? = takeUnless { it.isMissingNode || it.isNull }?.asBoolean()

    /** Returns null for absent or JSON null archive fields. */
    private fun JsonNode.textOrNull(): String? = takeUnless { it.isMissingNode || it.isNull }?.asString()

    companion object {
        private const val MAX_DATABASE_BYTES = 400_000_000L
    }
}
