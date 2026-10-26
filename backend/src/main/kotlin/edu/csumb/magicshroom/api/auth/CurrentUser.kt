package edu.csumb.magicshroom.api.auth

import edu.csumb.magicshroom.api.error.ForbiddenException
import edu.csumb.magicshroom.api.model.Role
import io.swagger.v3.oas.annotations.Hidden

/**
 * The signed-in caller. Declare it as a controller parameter on any protected endpoint; it is
 * filled in by the active profile's registered resolver. Its role comes from the
 * user store, so admin rights live in the database rather than in code. Hidden from the generated
 * API docs: it comes from the bearer token, not from a request parameter.
 */
@Hidden
data class CurrentUser(val id: Long, val role: Role) {
	/** Throws [ForbiddenException] (403) unless this user is an admin. */
	fun requireAdmin() {
		if (role != Role.ADMIN) throw ForbiddenException("This operation requires the ADMIN role.")
	}
}
