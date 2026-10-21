package edu.csumb.magicshroom.api.auth

import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.error.UnauthorizedException
import edu.csumb.magicshroom.api.model.Role
import edu.csumb.magicshroom.api.model.User
import edu.csumb.magicshroom.api.repository.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.context.request.ServletWebRequest
import java.util.UUID
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Verifies that only a validated JWT principal can select an application user. */
class SupabaseCurrentUserResolverTest {
    private var savedSubject: UUID? = null
    private val repository = object : UserRepository {
        /** Unused in this resolver test. */
        override fun findById(id: Long): User? = null

        /** Unused in this resolver test. */
        override fun findPage(role: Role?, page: Int, size: Int): PageResponse<User> = error("unused")

        /** Unused in this resolver test. */
        override fun countByRole(role: Role): Long = error("unused")

        /** Unused in this resolver test. */
        override fun updateRole(id: Long, role: Role): User? = error("unused")
        override fun findAuthUserIdById(id: Long): UUID? = null

        /** Records the Auth subject handed to application account creation. */
        override fun findOrCreate(authUserId: UUID, email: String, displayName: String): User {
            savedSubject = authUserId
            return User(42, email, displayName, Role.USER)
        }

        /** Unused in this resolver test. */
        override fun deleteById(id: Long): Boolean = false
    }
    private val resolver = SupabaseCurrentUserResolver(repository)
    private val parameter = MethodParameter(Fixture::class.java.getDeclaredMethod("endpoint", CurrentUser::class.java), 0)

    /** Clears the static security context between tests. */
    @AfterEach
    fun clearContext() { SecurityContextHolder.clearContext() }

    /** Hands the verified Supabase subject to the account repository. */
    @Test
    fun `valid JWT subject creates linked application user`() {
        val subject = UUID.randomUUID()
        val jwt = Jwt.withTokenValue("token").header("alg", "RS256")
            .subject(subject.toString()).claim("email", "player@example.com").build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        val user = resolver.resolveArgument(parameter, null, ServletWebRequest(MockHttpServletRequest()), null)

        assertEquals(subject, savedSubject)
        assertEquals(42, user.id)
        assertEquals(Role.USER, user.role)
    }

    /** Rejects a request with no verified JWT principal. */
    @Test
    fun `missing token is unauthorized`() {
        assertFailsWith<UnauthorizedException> {
            resolver.resolveArgument(parameter, null, ServletWebRequest(MockHttpServletRequest()), null)
        }
    }

    /** Rejects a token without a usable email claim. */
    @Test
    fun `token without email is unauthorized`() {
        val jwt = Jwt.withTokenValue("token").header("alg", "RS256")
            .subject(UUID.randomUUID().toString()).build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)
        assertFailsWith<UnauthorizedException> {
            resolver.resolveArgument(parameter, null, ServletWebRequest(MockHttpServletRequest()), null)
        }
    }

    /** A removed Supabase Auth identity must never be recreated by its still-unexpired JWT. */
    @Test
    fun `deleted auth identity cannot recreate application user`() {
        val missingIdentityRepository = object : UserRepository by repository {
            override fun findOrCreate(authUserId: UUID, email: String, displayName: String): User {
                val sqlException = java.sql.SQLException("auth user missing", "23503")
                throw DataIntegrityViolationException("missing auth user", sqlException)
            }
        }
        val missingIdentityResolver = SupabaseCurrentUserResolver(missingIdentityRepository)
        val jwt = Jwt.withTokenValue("token").header("alg", "RS256")
            .subject(UUID.randomUUID().toString()).claim("email", "deleted@example.com").build()
        SecurityContextHolder.getContext().authentication = JwtAuthenticationToken(jwt)

        assertFailsWith<UnauthorizedException> {
            missingIdentityResolver.resolveArgument(parameter, null, ServletWebRequest(MockHttpServletRequest()), null)
        }
    }

    private class Fixture {
        /** Provides a reflective controller parameter for the resolver. */
        fun endpoint(user: CurrentUser) = user
    }
}
