package edu.csumb.magicshroom.api.auth

import edu.csumb.magicshroom.api.error.ForbiddenException
import edu.csumb.magicshroom.api.model.Role

/**
 * The signed-in caller. Declare it as a controller parameter on any protected endpoint; it is
 * filled in by the registered resolver (today [StubCurrentUserResolver]). Its role comes from the
 * user store, so admin rights live in the database rather than in code.
 */
data class CurrentUser(val id: Long, val role: Role) {
	/** Throws [ForbiddenException] (403) unless this user is an admin. */
	fun requireAdmin() {
		if (role != Role.ADMIN) throw ForbiddenException("This operation requires the ADMIN role.")
	}
}
