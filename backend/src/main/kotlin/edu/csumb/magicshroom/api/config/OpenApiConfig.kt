package edu.csumb.magicshroom.api.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.IntegerSchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Name of the bearer-token security scheme; protected endpoints declare `@SecurityRequirement(name = BEARER_AUTH)`. */
const val BEARER_AUTH = "bearerAuth"

/** Shared Problem Details responses, referenced from `@ApiResponse(ref = ...)` on each endpoint. */
const val BAD_REQUEST = "#/components/responses/BadRequest"

/** 401: missing, expired, or invalid bearer token. */
const val UNAUTHORIZED = "#/components/responses/Unauthorized"

/** 403: signed in without the ADMIN role. */
const val FORBIDDEN = "#/components/responses/Forbidden"

/** 404: not found, or owned by another user. */
const val NOT_FOUND = "#/components/responses/NotFound"

/** 409: conflicts with existing data. */
const val CONFLICT = "#/components/responses/Conflict"

/** 503: a required external service is unavailable. */
const val SERVICE_UNAVAILABLE = "#/components/responses/ServiceUnavailable"

private const val INTERNAL_ERROR = "#/components/responses/InternalError"
private const val PROBLEM_JSON = "application/problem+json"
private const val EXAMPLE_STATUS = 404

/**
 * Metadata for the generated docs at `/swagger-ui.html` and `/v3/api-docs`: the API description, the
 * Supabase bearer-token scheme (Swagger UI's Authorize button), and the RFC 9457 Problem Details error
 * responses that endpoints reference. The hand-written contract remains `docs/openapi.yaml`.
 */
@Configuration
class OpenApiConfig {

	/** The base document springdoc fills in with the endpoints it finds in the controllers. */
	@Bean
	fun magicShroomOpenApi(): OpenAPI = OpenAPI()
		.info(
			Info()
				.title("MagicShroom API")
				.version("1.0.0")
				.description(
					"Browse Magic: The Gathering cards and manage personal decks. Card reads are public; " +
						"decks need a Supabase access token (Authorize → paste the token); card writes and user " +
						"management also need the ADMIN role. Errors use RFC 9457 Problem Details.",
				)
				.license(License().name("MIT").url("https://opensource.org/licenses/MIT")),
		)
		.components(
			Components()
				.addSecuritySchemes(
					BEARER_AUTH,
					SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("A Supabase Auth access token from Google sign-in."),
				)
				.addSchemas("Problem", problemSchema())
				.addResponses("BadRequest", problemResponse("Malformed JSON, invalid parameters, or validation failure"))
				.addResponses("Unauthorized", problemResponse("Missing, expired, or invalid bearer token"))
				.addResponses("Forbidden", problemResponse("Valid token, but the user lacks the ADMIN role"))
				.addResponses("NotFound", problemResponse("Resource not found or not owned by the signed-in user"))
				.addResponses("Conflict", problemResponse("Request conflicts with existing data or relationships"))
				.addResponses("ServiceUnavailable", problemResponse("A required external service is not configured or unavailable"))
				.addResponses("InternalError", problemResponse("Unexpected server error")),
		)

	/** Every endpoint can fail unexpectedly, so each one lists the shared 500 response. */
	@Bean
	fun internalErrorResponse(): OperationCustomizer = OperationCustomizer { operation, _ ->
		operation.responses?.addApiResponse("500", ApiResponse().`$ref`(INTERNAL_ERROR))
		operation
	}

	private fun problemSchema(): Schema<*> = ObjectSchema()
		.description("RFC 9457 Problem Details, the body of every error response.")
		.addProperty("type", StringSchema().example("about:blank"))
		.addProperty("title", StringSchema().example("Not Found"))
		.addProperty("status", IntegerSchema().example(EXAMPLE_STATUS))
		.addProperty("detail", StringSchema().example("Card 1001 was not found."))
		.addProperty("instance", StringSchema().example("/api/v1/cards/1001"))

	private fun problemResponse(description: String): ApiResponse = ApiResponse()
		.description(description)
		.content(Content().addMediaType(PROBLEM_JSON, MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/Problem"))))
}
