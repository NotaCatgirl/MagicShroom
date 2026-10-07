package edu.csumb.magicshroom.api.error

/**
 * An error the API reports to the client as RFC 9457 Problem Details. Services throw these;
 * [ApiExceptionHandler] picks the HTTP status from the subclass and uses [message] as the `detail`.
 */
sealed class ApiException(override val message: String) : RuntimeException(message)

/** 400: the request is well-formed JSON but asks for something invalid. */
class BadRequestException(message: String) : ApiException(message)

/** 401: no valid signed-in user. */
class UnauthorizedException(message: String) : ApiException(message)

/** 403: signed in, but not allowed (for example, not an admin). */
class ForbiddenException(message: String) : ApiException(message)

/** 404: does not exist, or belongs to another user. */
class NotFoundException(message: String) : ApiException(message)

/** 409: conflicts with existing data, such as a duplicate or a card still used by a deck. */
class ConflictException(message: String) : ApiException(message)
