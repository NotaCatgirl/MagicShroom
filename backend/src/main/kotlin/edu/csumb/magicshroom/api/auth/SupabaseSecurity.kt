package edu.csumb.magicshroom.api.auth

import edu.csumb.magicshroom.api.error.UnauthorizedException
import edu.csumb.magicshroom.api.repository.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.MethodParameter
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.context.SecurityContextHolder
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
class SupabaseSecurity(@Value("\${magicshroom.auth.issuer}") private val issuer: String) {
    /** Enforces bearer authentication for writes and all personal deck and user routes. */
    @Bean
    fun apiFilterChain(http: HttpSecurity): SecurityFilterChain = http.csrf { it.disable() }
        .authorizeHttpRequests {
            it.requestMatchers(HttpMethod.GET, "/api/v1/cards", "/api/v1/cards/*", "/actuator/health").permitAll()
                .anyRequest().authenticated()
        }
        .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
        .build()

    /** Validates signature, issuer, expiry, and the authenticated Supabase audience. */
    @Bean
    fun jwtDecoder(): JwtDecoder = NimbusJwtDecoder.withJwkSetUri("$issuer/.well-known/jwks.json")
        .jwsAlgorithms { algorithms -> algorithms.add(SignatureAlgorithm.RS256); algorithms.add(SignatureAlgorithm.ES256) }
        .build().apply {
            setJwtValidator { token ->
                val standard = JwtValidators.createDefaultWithIssuer(issuer).validate(token)
                if (standard.hasErrors()) standard else if ("authenticated" !in token.audience || token.getClaimAsString("role") != "authenticated") {
                    org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.failure(
                        org.springframework.security.oauth2.core.OAuth2Error("invalid_token", "Invalid audience", null))
                } else org.springframework.security.oauth2.core.OAuth2TokenValidatorResult.success()
            }
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
        val user = users.findOrCreate(subject, email, displayName)
        return CurrentUser(user.id, user.role)
    }
}
