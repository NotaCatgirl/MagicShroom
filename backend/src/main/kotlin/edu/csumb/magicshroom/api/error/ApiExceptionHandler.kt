package edu.csumb.magicshroom.api.error

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterErrors
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import tools.jackson.core.JacksonException
import java.net.URI

/**
 * Turns every error into an RFC 9457 Problem Details body (`application/problem+json`), the one
 * error shape in docs/openapi.yaml. Spring's own errors (malformed JSON, failed validation, unknown
 * route) are handled by [ResponseEntityExceptionHandler]; ours are handled below.
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
	private val log = LoggerFactory.getLogger(javaClass)

	/** Maps an [ApiException] to its status, with the exception message as `detail`. */
	@ExceptionHandler(ApiException::class)
	fun handleApiException(ex: ApiException, request: HttpServletRequest): ProblemDetail {
		val status = when (ex) {
			is BadRequestException -> HttpStatus.BAD_REQUEST
			is UnauthorizedException -> HttpStatus.UNAUTHORIZED
			is ForbiddenException -> HttpStatus.FORBIDDEN
			is NotFoundException -> HttpStatus.NOT_FOUND
			is ConflictException -> HttpStatus.CONFLICT
		}
		return problem(status, ex.message, request)
	}

	/** Anything unexpected becomes a 500 without leaking internals; the stack trace goes to the log. */
	@ExceptionHandler(Exception::class)
	fun handleUnexpected(ex: Exception, request: HttpServletRequest): ProblemDetail {
		log.error("Unhandled error on {} {}", request.method, request.requestURI, ex)
		return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.", request)
	}

	/** Spring's validation and parsing errors say only "Invalid request content"; name the bad fields instead. */
	override fun handleExceptionInternal(
		ex: Exception,
		body: Any?,
		headers: HttpHeaders,
		statusCode: HttpStatusCode,
		request: WebRequest,
	): ResponseEntity<Any>? {
		val response = super.handleExceptionInternal(ex, body, headers, statusCode, request)
		val problem = response?.body as? ProblemDetail
		if (problem != null) validationDetail(ex)?.let { problem.detail = it }
		return response
	}

	private fun validationDetail(ex: Exception): String? = when (ex) {
		is MethodArgumentNotValidException -> ex.bindingResult.fieldErrors.describe()
		is HandlerMethodValidationException -> ex.parameterValidationResults.flatMap { result ->
			if (result is ParameterErrors) {
				result.fieldErrors.map { "${it.field} ${it.defaultMessage}" }
			} else {
				result.resolvableErrors.map { "${result.methodParameter.parameterName} ${it.defaultMessage}" }
			}
		}.joinToString("; ")
		is HttpMessageNotReadableException -> generateSequence<Throwable>(ex) { it.cause }
			.filterIsInstance<JacksonException>()
			.firstNotNullOfOrNull { cause -> cause.path.takeIf { it.isNotEmpty() } }
			?.joinToString(".") { it.propertyName ?: "[${it.index}]" }
			?.let { "Missing or invalid value for '$it'." }
		else -> null
	}

	private fun List<FieldError>.describe() = joinToString("; ") { "${it.field} ${it.defaultMessage}" }

	private fun problem(status: HttpStatus, detail: String, request: HttpServletRequest): ProblemDetail =
		ProblemDetail.forStatusAndDetail(status, detail).apply { instance = URI.create(request.requestURI) }
}
