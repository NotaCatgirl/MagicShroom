package edu.csumb.magicshroom.api.auth

import edu.csumb.magicshroom.api.error.UnauthorizedException
import edu.csumb.magicshroom.api.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * TEMPORARY stand-in for OAuth2 so protected routes can be exercised before sign-in exists.
 *
 * The caller is the user id in the `X-Stub-User-Id` header, or `magicshroom.stub-auth.default-user-id`
 * when the header is absent. An id that is not a known user is treated like a missing token (401).
 * There is no security here at all: replace this class with a resolver that reads the validated JWT
 * (Spring Security Resource Server) and looks the user up by email. Controllers and services keep
 * their [CurrentUser] parameters unchanged.
 */
@Component
class StubCurrentUserResolver(
	private val userRepository: UserRepository,
	@Value("\${magicshroom.stub-auth.default-user-id}") private val defaultUserId: Long,
) : HandlerMethodArgumentResolver {

	init {
		LoggerFactory.getLogger(javaClass).warn(
			"Stub authentication is active: callers are chosen by the {} header (default user {}). Do not deploy.",
			HEADER, defaultUserId,
		)
	}

	override fun supportsParameter(parameter: MethodParameter): Boolean =
		parameter.parameterType == CurrentUser::class.java

	override fun resolveArgument(
		parameter: MethodParameter,
		mavContainer: ModelAndViewContainer?,
		webRequest: NativeWebRequest,
		binderFactory: WebDataBinderFactory?,
	): CurrentUser {
		val header = webRequest.getHeader(HEADER)
		val userId = if (header == null) defaultUserId else header.trim().toLongOrNull()
		val user = userId?.let(userRepository::findById)
			?: throw UnauthorizedException("No signed-in user. Send a valid $HEADER header.")
		return CurrentUser(user.id, user.role)
	}

	companion object {
		/** Request header that picks the stub caller, e.g. `X-Stub-User-Id: 1` for the admin. */
		const val HEADER = "X-Stub-User-Id"
	}
}
