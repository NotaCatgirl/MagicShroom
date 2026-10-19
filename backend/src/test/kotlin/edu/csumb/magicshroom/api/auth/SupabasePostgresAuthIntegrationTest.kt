package edu.csumb.magicshroom.api.auth

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import io.restassured.RestAssured.given
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.Date
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

/** Exercises production bearer validation, JDBC user creation, and Auth deletion against PostgreSQL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("postgres")
class SupabasePostgresAuthIntegrationTest {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @LocalServerPort
    private var port: Int = 0

    /** Connects the mock Auth Admin endpoint to PostgreSQL's `auth.users` fixture table. */
    @BeforeEach
    fun connectMockAuthDeletionToPostgres() {
        deleteAuthIdentity.set { userId -> jdbc.update("delete from auth.users where id = ?", userId) }
        authRequests.clear()
    }

    /** Accepts a signed, unexpired Supabase-shaped JWT and persists its identity in PostgreSQL. */
    @Test
    fun `valid bearer token creates one postgres user and is reusable`() {
        val subject = UUID.randomUUID()
        jdbc.update("insert into auth.users (id) values (?)", subject)
        val bearer = token(subject)

        given().port(port).auth().oauth2(bearer).get("/api/v1/decks").then().statusCode(200)
        given().port(port).auth().oauth2(bearer).get("/api/v1/decks").then().statusCode(200)

        assertEquals(1, jdbc.queryForObject("select count(*) from app_users where auth_user_id = ?", Int::class.java, subject))
    }

    /** Exercises PostgreSQL card, deck, and deck-card reads and writes through the authenticated HTTP API. */
    @Test
    fun `real postgres routes search cards and manage decks with card entries`() {
        val adminAuthId = UUID.randomUUID()
        jdbc.update("insert into auth.users (id) values (?)", adminAuthId)
        jdbc.update(
            "insert into app_users (auth_user_id, email, display_name, role) values (?, ?, ?, 'ADMIN')",
            adminAuthId,
            "catalog-admin@example.test",
            "Catalog Admin",
        )
        val bearer = token(adminAuthId, "catalog-admin@example.test")
        val cardUuid = UUID.randomUUID()

        val createdCard = given().port(port).auth().oauth2(bearer).contentType("application/json")
            .body(cardBody(cardUuid, "Supabase Integration Card"))
            .post("/api/v1/cards")
        assertEquals(201, createdCard.statusCode())
        val cardId = createdCard.jsonPath().getLong("id")

        val cardBatch = given().port(port).contentType("application/json")
            .body("""{"cardIds":[$cardId]}""")
            .post("/api/v1/cards/batch")
        assertEquals(200, cardBatch.statusCode())
        assertEquals(cardId, cardBatch.jsonPath().getLong("[0].id"))
        assertEquals("Supabase Integration Card", cardBatch.jsonPath().getString("[0].cardName"))

        val cardSearch = given().port(port).queryParam("name", "integration").queryParam("color", "R")
            .get("/api/v1/cards")
        assertEquals(200, cardSearch.statusCode())
        assertEquals("Supabase Integration Card", cardSearch.jsonPath().getString("content[0].cardName"))
        val cardDetails = given().port(port).get("/api/v1/cards/$cardId")
        assertEquals("Spanish", cardDetails.jsonPath().getString("foreignData[0].language"))
        assertEquals("Anillo de integración", cardDetails.jsonPath().getString("foreignData[0].name"))
        assertEquals("LEGAL", cardDetails.jsonPath().getString("legalities[0].status"))

        val replacement = given().port(port).auth().oauth2(bearer).contentType("application/json")
            .body(cardBody(cardUuid, "Replaced Integration Card"))
            .put("/api/v1/cards/$cardId")
        assertEquals(200, replacement.statusCode())
        assertEquals("Replaced Integration Card", replacement.jsonPath().getString("cardName"))
        val patch = given().port(port).auth().oauth2(bearer).contentType("application/json")
            .body("""{"cardName":"Patched Integration Card","manaCost":"{3}"}""")
            .patch("/api/v1/cards/$cardId")
        assertEquals("Patched Integration Card", patch.jsonPath().getString("cardName"))

        val createdDeck = given().port(port).auth().oauth2(bearer).contentType("application/json")
            .body("""{"name":"Postgres test deck"}""")
            .post("/api/v1/decks")
        assertEquals(201, createdDeck.statusCode())
        val deckId = createdDeck.jsonPath().getLong("id")
        val addedCard = given().port(port).auth().oauth2(bearer).contentType("application/json")
            .body("""{"cardId":$cardId,"quantity":2,"section":"MAINBOARD"}""")
            .post("/api/v1/decks/$deckId/cards")
        assertEquals(201, addedCard.statusCode())
        assertEquals(2, addedCard.jsonPath().getInt("quantity"))

        val deckDetails = given().port(port).auth().oauth2(bearer).get("/api/v1/decks/$deckId")
        assertEquals(200, deckDetails.statusCode())
        assertEquals(cardId, deckDetails.jsonPath().getLong("cards[0].cardId"))
        val renamedDeck = given().port(port).auth().oauth2(bearer).contentType("application/json")
            .body("""{"name":"Renamed test deck"}""")
            .patch("/api/v1/decks/$deckId")
        assertEquals("Renamed test deck", renamedDeck.jsonPath().getString("name"))
        given().port(port).auth().oauth2(bearer).contentType("application/json")
            .body("""{"quantity":3,"section":"SIDEBOARD"}""")
            .patch("/api/v1/decks/$deckId/cards/$cardId").then().statusCode(200)
        given().port(port).auth().oauth2(bearer).delete("/api/v1/cards/$cardId").then().statusCode(409)
        given().port(port).auth().oauth2(bearer).delete("/api/v1/decks/$deckId/cards/$cardId").then().statusCode(204)
        given().port(port).auth().oauth2(bearer).delete("/api/v1/decks/$deckId").then().statusCode(204)
        given().port(port).auth().oauth2(bearer).delete("/api/v1/cards/$cardId").then().statusCode(204)
    }

    /** Rejects missing, wrongly signed, expired, and wrongly-audienced tokens at the real filter chain. */
    @Test
    fun `real resource server rejects missing invalid expired and wrong audience tokens`() {
        val subject = UUID.randomUUID()
        jdbc.update("insert into auth.users (id) values (?)", subject)
        val otherKey = RSAKeyGenerator(2048).keyID(signingKey.keyID).generate()

        given().port(port).get("/v3/api-docs").then().statusCode(200)
        given().port(port).get("/swagger-ui/index.html").then().statusCode(200)
        given().port(port).get("/api/v1/decks").then().statusCode(401)
        given().port(port).auth().oauth2(token(subject, signer = otherKey)).get("/api/v1/decks").then().statusCode(401)
        given().port(port).auth().oauth2(token(subject, expiresAt = Date(System.currentTimeMillis() - 60_000)))
            .get("/api/v1/decks").then().statusCode(401)
        given().port(port).auth().oauth2(token(subject, audience = "public"))
            .get("/api/v1/decks").then().statusCode(401)

        assertEquals(0, jdbc.queryForObject("select count(*) from app_users where auth_user_id = ?", Int::class.java, subject))
    }

    /** Deletes the Auth identity through the admin client, verifies FK cascades, and rejects its stale JWT. */
    @Test
    fun `admin deletion cascades postgres data and stale token cannot recreate the user`() {
        val adminAuthId = UUID.randomUUID()
        val targetAuthId = UUID.randomUUID()
        jdbc.update("insert into auth.users (id) values (?), (?)", adminAuthId, targetAuthId)
        jdbc.update(
            "insert into app_users (auth_user_id, email, display_name, role) values (?, ?, ?, 'ADMIN')",
            adminAuthId,
            "admin@example.test",
            "Admin",
        )
        val targetUserId = jdbc.queryForObject(
            "insert into app_users (auth_user_id, email, display_name) values (?, ?, ?) returning id",
            Long::class.java,
            targetAuthId,
            "target@example.test",
            "Target",
        )!!
        jdbc.update("insert into decks (user_id, name) values (?, ?)", targetUserId, "Cascade test")

        given().port(port).auth().oauth2(token(adminAuthId, "admin@example.test"))
            .delete("/api/v1/users/$targetUserId").then().statusCode(204)

        assertEquals(0, jdbc.queryForObject("select count(*) from auth.users where id = ?", Int::class.java, targetAuthId))
        assertEquals(0, jdbc.queryForObject("select count(*) from app_users where auth_user_id = ?", Int::class.java, targetAuthId))
        assertEquals(0, jdbc.queryForObject("select count(*) from decks where user_id = ?", Int::class.java, targetUserId))
        assertEquals(401, given().port(port).auth().oauth2(token(targetAuthId, "target@example.test"))
            .get("/api/v1/decks").statusCode())
        assertEquals(0, jdbc.queryForObject("select count(*) from app_users where auth_user_id = ?", Int::class.java, targetAuthId))

        val deletionRequest = authRequests.lastOrNull { it.method == "DELETE" }
        assertNotNull(deletionRequest)
        assertEquals("test-admin-key", deletionRequest?.getHeader("apikey"))
        assertEquals("Bearer test-admin-key", deletionRequest?.getHeader("Authorization"))
    }

    /** Builds an RS256 JWT containing the Supabase claims required by the production decoder. */
    private fun token(
        subject: UUID,
        email: String = "player@example.test",
        audience: String = "authenticated",
        expiresAt: Date = Date(System.currentTimeMillis() + 300_000),
        signer: com.nimbusds.jose.jwk.RSAKey = signingKey,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuerUrl())
            .subject(subject.toString())
            .audience(audience)
            .issueTime(Date(System.currentTimeMillis() - 1_000))
            .expirationTime(expiresAt)
            .claim("role", "authenticated")
            .claim("email", email)
            .build()
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.keyID).build(), claims)
            .apply { sign(RSASSASigner(signer)) }
            .serialize()
    }

    /** Returns a representative card payload covering PostgreSQL arrays and JSONB fields. */
    private fun cardBody(uuid: UUID, name: String): String = """{
        "uuid":"$uuid","cardName":"$name","colorIdentity":["R"],"colors":["R"],
        "convertedManaCost":3,"manaCost":"{2}{R}","scryfallOracleId":"92b5bdb4-a1a2-4dc8-b72f-420bbf5c7873",
        "text":"Test card text","types":["Artifact"],"subtypes":["Equipment"],"supertypes":["Legendary"],
        "printings":["TST"],"foreignData":[{"language":"Spanish","name":"Anillo de integración","text":"Texto"}],
        "legalities":[{"format":"commander","status":"LEGAL"}],
        "purchaseUrls":[{"vendor":"Example","url":"https://example.test/card"}],
        "rulings":[{"date":"2024-01-01","text":"A test ruling."}]
    }""".trimIndent()

    private companion object {
        private val signingKey = RSAKeyGenerator(2048).keyID("magicshroom-integration-test").generate()
        private val authRequests = CopyOnWriteArrayList<RecordedRequest>()
        private val deleteAuthIdentity = AtomicReference<(UUID) -> Unit>({})
        private val authServer = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    authRequests += request
                    return when {
                        request.path == "/auth/v1/.well-known/jwks.json" -> MockResponse()
                            .setHeader("Content-Type", "application/json")
                            .setBody("{\"keys\":[${signingKey.toPublicJWK().toJSONString()}]}")
                        request.method == "DELETE" && request.path?.startsWith("/auth/v1/admin/users/") == true -> {
                            deleteAuthIdentity.get().invoke(UUID.fromString(request.path!!.substringAfterLast('/')))
                            MockResponse().setResponseCode(200).setBody("{}")
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        }.also(MockWebServer::start)

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer<Nothing>("postgres:16-alpine")
            .withInitScript("measurement-bootstrap.sql")

        /** Configures the container data source and isolated local Supabase-compatible endpoints. */
        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.flyway.enabled") { true }
            registry.add("spring.datasource.url") { postgres.getJdbcUrl() }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.datasource.username") { postgres.getUsername() }
            registry.add("spring.datasource.password") { postgres.getPassword() }
            registry.add("magicshroom.auth.issuer", ::issuerUrl)
            registry.add("magicshroom.auth.admin-key") { "test-admin-key" }
        }

        /** Returns the mock Supabase Auth issuer shared by the test JWTs and API decoder. */
        private fun issuerUrl(): String = authServer.url("/auth/v1").toString().trimEnd('/')
    }
}
