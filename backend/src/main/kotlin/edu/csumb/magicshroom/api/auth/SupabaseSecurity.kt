package edu.csumb.magicshroom.api.auth

import edu.csumb.magicshroom.api.error.UnauthorizedException
import edu.csumb.magicshroom.api.repository.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.MethodParameter
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.security.web.AuthenticationEntryPoint
import tools.jackson.databind.json.JsonMapper
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID
import java.sql.SQLException

/** Allows the isolated mock profile to exercise its stub caller in route tests. */
@Configuration
@Profile("mock")
class MockSecurity {
    /** Permits test requests; the stub resolver supplies the test caller. */
    @Bean
    fun mockFilterChain(http: HttpSecurity): SecurityFilterChain = http.csrf { it.disable() }
        .authorizeHttpRequests { it.anyRequest().permitAll() }.build()
}

/** Verifies Supabase access tokens locally against the project's asymmetric signing keys. */
@Configuration
@Profile("!mock")
class SupabaseSecurity(
    @Value("\${magicshroom.auth.issuer}") private val issuer: String,
    private val json: JsonMapper,
) {
    /** Enforces bearer authentication for writes and all personal deck and user routes. */
    @Bean
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain {
        val entryPoint = ProblemAuthenticationEntryPoint(json)
        return http.csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(
                    HttpMethod.GET,
                    "/api/v1/cards",
                    "/api/v1/cards/*",
                    "/actuator/health",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                ).permitAll()
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()).authenticationEntryPoint(entryPoint) }
            .exceptionHandling { it.authenticationEntryPoint(entryPoint) }
            .build()
    }

    /** Validates signature, issuer, expiry, and the authenticated Supabase audience. */
    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withJwkSetUri("$issuer/.well-known/jwks.json")
        .jwsAlgorithms { algorithms -> algorithms.add(SignatureAlgorithm.RS256); algorithms.add(SignatureAlgorithm.ES256) }
        .build().apply {
            setJwtValidator { token ->
                val standard = JwtValidators.createDefaultWithIssuer(issuer).validate(token)
                if (standard.hasErrors()) standard else if (token.audience?.contains("authenticated") != true || token.getClaimAsString("role") != "authenticated") {
                    org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid audience", null))
                } else org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
            }
        }
}

/**
 * Writes 401s raised by the security filters (missing, invalid, or expired bearer token) as RFC 9457
 * Problem Details, the same body every other API error uses. The `WWW-Authenticate` header is kept.
 */
class ProblemAuthenticationEntryPoint(private val json: JsonMapper) : AuthenticationEntryPoint {
    private val bearer = BearerTokenAuthenticationEntryPoint()

    /** Sets the 401 status and bearer challenge, then writes the problem body. */
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        bearer.commence(request, response, authException)
        val sentToken = request.getHeader(HttpHeaders.AUTHORIZATION)?.startsWith("Bearer ", ignoreCase = true) == true
        val problem = mapOf(
            "title" to "Unauthorized",
            "status" to HttpStatus.UNAUTHORIZED.value(),
            "detail" to if (sentToken) "The bearer token is invalid or expired." else "A bearer token is required.",
            "instance" to request.requestURI,
        )
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        json.writeValue(response.outputStream, problem)
    }
}

/** Resolves a verified Supabase Auth subject to the API's numeric user id. */
@Component
@Profile("!mock")
class SupabaseCurrentUserResolver(private val users: UserRepository) : HandlerMethodArgumentResolver {
    /** Selects controller parameters that require a signed-in user. */
    override fun supportsParameter(parameter: MethodParameter): Boolean = parameter.parameterType == CurrentUser::class.java

    /** Creates an application user on first valid sign-in and returns its role. */
    override fun resolveArgument(parameter: MethodParameter, mavContainer: ModelAndViewContainer?, webRequest: NativeWebRequest,
                                 binderFactory: WebDataBinderFactory?): CurrentUser {
        val jwt = SecurityContextHolder.getContext().authentication?.principal as? Jwt
            ?: throw UnauthorizedException("A valid Supabase access token is required.")
        val subject = runCatching { UUID.fromString(jwt.subject) }.getOrNull()
            ?: throw UnauthorizedException("Invalid token subject.")
        val email = jwt.getClaimAsString("email")?.takeIf(String::isNotBlank)
            ?: throw UnauthorizedException("Token has no email claim.")
        val displayName = (jwt.getClaimAsMap("user_metadata")?.get("full_name") as? String)
            ?.takeIf(String::isNotBlank) ?: email.substringBefore('@')
        val user = try {
            users.findOrCreate(subject, email, displayName)
        } catch (exception: DataIntegrityViolationException) {
            val isMissingAuthIdentity = generateSequence<Throwable>(exception) { it.cause }
                .filterIsInstance<SQLException>()
                .any { it.sqlState == "23503" }
            if (isMissingAuthIdentity) throw UnauthorizedException("This account no longer exists. Sign in again.")
            throw exception
        }
        return CurrentUser(user.id, user.role)
    }
}
