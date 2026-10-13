package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.dto.PageMeta
import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.model.*
import edu.csumb.magicshroom.api.service.CardSort
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.UUID

/** PostgreSQL card catalog with native arrays and JSONB columns. */
@Repository
@Profile("!mock")
class PostgresCardRepository(private val jdbc: JdbcTemplate, private val json: JsonMapper) : CardRepository {
    private val columns = "id, uuid, card_name, color_identity, colors, converted_mana_cost, mana_cost, scryfall_oracle_id, card_text, types, subtypes, supertypes, printings, foreign_data, legalities, purchase_urls, rulings"
    private val fields = "uuid, card_name, color_identity, colors, converted_mana_cost, mana_cost, scryfall_oracle_id, card_text, types, subtypes, supertypes, printings, foreign_data, legalities, purchase_urls, rulings"
    private val values = "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb"
    private val mapper = RowMapper { rs: ResultSet, _: Int ->
        Card(
            id = rs.getLong("id"), uuid = rs.getObject("uuid", UUID::class.java),
            cardName = rs.getString("card_name"),
            colorIdentity = strings(rs, "color_identity").map(Color::valueOf),
            colors = strings(rs, "colors").map(Color::valueOf),
            convertedManaCost = rs.getDouble("converted_mana_cost").takeUnless { rs.wasNull() },
            manaCost = rs.getString("mana_cost"),
            scryfallOracleId = rs.getObject("scryfall_oracle_id", UUID::class.java),
            text = rs.getString("card_text"), types = strings(rs, "types"),
            subtypes = strings(rs, "subtypes"), supertypes = strings(rs, "supertypes"),
            printings = strings(rs, "printings"),
            foreignData = foreignData(rs.getString("foreign_data")),
            legalities = json.readValue(rs.getString("legalities")),
            purchaseUrls = json.readValue(rs.getString("purchase_urls")),
            rulings = json.readValue(rs.getString("rulings")),
        )
    }

    /** Reads a PostgreSQL text array into an immutable Kotlin list. */
    private fun strings(rs: ResultSet, name: String): List<String> =
        (rs.getArray(name).array as Array<*>).map { it.toString() }

    /** Projects complete archived foreign data into the stable API response fields. */
    private fun foreignData(raw: String): List<ForeignCardData> = json.readTree(raw).toList().map { item ->
        ForeignCardData(item.path("language").asText(), item.path("name").asText(),
            item.path("text").takeUnless { it.isMissingNode || it.isNull }?.asText())
    }

    /** Reads the entire catalog for maintenance tasks. */
    override fun findAll(): List<Card> = jdbc.query("select $columns from cards order by id", mapper)

    /** Searches and paginates in PostgreSQL so normal browsing never loads the whole archive. */
    override fun search(name: String?, color: Color?, sort: CardSort, page: Int, size: Int): PageResponse<Card> {
        val terms = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (name != null) { terms += "card_name ilike ? escape '\\'"; args += "%${name.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%" }
        if (color != null) {
            terms += if (color == Color.C) "cardinality(color_identity) = 0 or ? = any(color_identity)" else "? = any(color_identity)"
            args += color.name
        }
        val where = if (terms.isEmpty()) "" else " where " + terms.joinToString(" and ") { "($it)" }
        val total = jdbc.queryForObject("select count(*) from cards$where", Long::class.java, *args.toTypedArray()) ?: 0L
        val order = when (sort) {
            CardSort.NAME_ASC -> "lower(card_name) asc, id asc"
            CardSort.NAME_DESC -> "lower(card_name) desc, id asc"
            CardSort.MANA_VALUE_ASC -> "converted_mana_cost asc nulls last, id asc"
            CardSort.MANA_VALUE_DESC -> "converted_mana_cost desc nulls first, id asc"
        }
        val rows = jdbc.query("select $columns from cards$where order by $order limit ? offset ?", mapper,
            *(args + listOf(size, page.toLong() * size)).toTypedArray())
        return PageResponse(rows, PageMeta(size, page, total, ((total + size - 1) / size).toInt()))
    }

    /** Finds a card by numeric API id. */
    override fun findById(id: Long): Card? = jdbc.query("select $columns from cards where id = ?", mapper, id).firstOrNull()

    /** Finds a card by its stable archive UUID. */
    override fun findByUuid(uuid: UUID): Card? = jdbc.query("select $columns from cards where uuid = ?", mapper, uuid).firstOrNull()

    /** Inserts a card and returns its database id. */
    override fun insert(card: Card): Card {
        val keys = GeneratedKeyHolder()
        jdbc.update({ con -> con.prepareStatement("insert into cards ($fields) values ($values)", arrayOf("id")).also { bind(it, card) } }, keys)
        return card.copy(id = requireNotNull(keys.key).toLong())
    }

    /** Updates all scalar, array, and JSONB fields of a card. */
    override fun update(card: Card): Card {
        val assignments = fields.split(", ").mapIndexed { index, field -> "$field = ${values.split(", ")[index]}" }.joinToString(", ")
        jdbc.update({ con -> con.prepareStatement("update cards set $assignments where id = ?").also { bind(it, card); it.setLong(17, card.id) } })
        return card
    }

    /** Deletes a card unused by decks. */
    override fun deleteById(id: Long): Boolean = jdbc.update("delete from cards where id = ?", id) > 0

    /** Binds a card using PostgreSQL text arrays and JSONB casts. */
    private fun bind(ps: PreparedStatement, card: Card) {
        ps.setObject(1, card.uuid)
        ps.setString(2, card.cardName)
        ps.setArray(3, ps.connection.createArrayOf("text", card.colorIdentity.map(Color::name).toTypedArray()))
        ps.setArray(4, ps.connection.createArrayOf("text", card.colors.map(Color::name).toTypedArray()))
        val manaValue = card.convertedManaCost
        if (manaValue == null) ps.setNull(5, java.sql.Types.DOUBLE) else ps.setDouble(5, manaValue)
        ps.setString(6, card.manaCost)
        ps.setObject(7, card.scryfallOracleId)
        ps.setString(8, card.text)
        ps.setArray(9, ps.connection.createArrayOf("text", card.types.toTypedArray()))
        ps.setArray(10, ps.connection.createArrayOf("text", card.subtypes.toTypedArray()))
        ps.setArray(11, ps.connection.createArrayOf("text", card.supertypes.toTypedArray()))
        ps.setArray(12, ps.connection.createArrayOf("text", card.printings.toTypedArray()))
        ps.setString(13, json.writeValueAsString(card.foreignData))
        ps.setString(14, json.writeValueAsString(card.legalities))
        ps.setString(15, json.writeValueAsString(card.purchaseUrls))
        ps.setString(16, json.writeValueAsString(card.rulings))
    }
}

/** PostgreSQL storage for user accounts linked to Supabase Auth subjects. */
@Repository
@Profile("!mock")
class PostgresUserRepository(private val jdbc: JdbcTemplate) : UserRepository {
    private val mapper = RowMapper { rs: ResultSet, _: Int -> User(rs.getLong("id"), rs.getString("email"), rs.getString("display_name"), Role.valueOf(rs.getString("role"))) }

    /** Finds a user by the API's numeric id. */
    override fun findById(id: Long): User? = jdbc.query("select id, email, display_name, role from app_users where id = ?", mapper, id).firstOrNull()

    /** Creates an application account for an Auth subject on first valid request. */
    override fun findOrCreate(authUserId: UUID, email: String, displayName: String): User =
        jdbc.query("""insert into app_users (auth_user_id, email, display_name) values (?, ?, ?)
            on conflict (auth_user_id) do update set email = excluded.email
            returning id, email, display_name, role""", mapper, authUserId, email, displayName).single()

    /** Removes an application account and its decks without deleting auth.users. */
    override fun deleteById(id: Long): Boolean = jdbc.update("delete from app_users where id = ?", id) > 0
}

/** PostgreSQL deck storage. */
@Repository
@Profile("!mock")
class PostgresDeckRepository(private val jdbc: JdbcTemplate) : DeckRepository {
    private val mapper = RowMapper { rs: ResultSet, _: Int -> Deck(rs.getLong("id"), rs.getLong("user_id"), rs.getString("name")) }

    /** Lists a user's decks in stable id order. */
    override fun findByUserId(userId: Long): List<Deck> = jdbc.query("select id, user_id, name from decks where user_id = ? order by id", mapper, userId)

    /** Finds a deck by id. */
    override fun findById(id: Long): Deck? = jdbc.query("select id, user_id, name from decks where id = ?", mapper, id).firstOrNull()

    /** Inserts a deck and returns its id. */
    override fun insert(deck: Deck): Deck = jdbc.query("insert into decks (user_id, name) values (?, ?) returning id, user_id, name", mapper, deck.userId, deck.name).single()

    /** Renames a deck. */
    override fun update(deck: Deck): Deck = jdbc.query("update decks set name = ? where id = ? returning id, user_id, name", mapper, deck.name, deck.id).single()

    /** Deletes a deck and its entries through the foreign key. */
    override fun deleteById(id: Long): Boolean = jdbc.update("delete from decks where id = ?", id) > 0
}

/** PostgreSQL deck card storage. */
@Repository
@Profile("!mock")
class PostgresDeckCardRepository(private val jdbc: JdbcTemplate) : DeckCardRepository {
    private val mapper = RowMapper { rs: ResultSet, _: Int -> DeckCard(rs.getLong("deck_id"), rs.getLong("card_id"), rs.getInt("quantity"), DeckSection.valueOf(rs.getString("section"))) }

    /** Lists entries in a deck. */
    override fun findByDeckId(deckId: Long): List<DeckCard> = jdbc.query("select * from deck_cards where deck_id = ? order by card_id", mapper, deckId)

    /** Finds an entry in a deck. */
    override fun find(deckId: Long, cardId: Long): DeckCard? = jdbc.query("select * from deck_cards where deck_id = ? and card_id = ?", mapper, deckId, cardId).firstOrNull()

    /** Checks whether a card is referenced by any deck. */
    override fun existsByCardId(cardId: Long): Boolean = jdbc.queryForObject("select exists(select 1 from deck_cards where card_id = ?)", Boolean::class.java, cardId) == true

    /** Inserts or changes one deck entry. */
    override fun save(deckCard: DeckCard): DeckCard {
        jdbc.update("""insert into deck_cards (deck_id, card_id, quantity, section) values (?, ?, ?, ?)
            on conflict (deck_id, card_id) do update set quantity = excluded.quantity, section = excluded.section""",
            deckCard.deckId, deckCard.cardId, deckCard.quantity, deckCard.section.name)
        return deckCard
    }

    /** Deletes one deck entry. */
    override fun delete(deckId: Long, cardId: Long): Boolean = jdbc.update("delete from deck_cards where deck_id = ? and card_id = ?", deckId, cardId) > 0

    /** Clears one deck. */
    override fun deleteByDeckId(deckId: Long) { jdbc.update("delete from deck_cards where deck_id = ?", deckId) }
}
