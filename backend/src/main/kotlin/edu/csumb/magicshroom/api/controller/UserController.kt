package edu.csumb.magicshroom.api.controller

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.service.UserService
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** `/api/v1/users`: admin-only account management. */
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

	/** Deletes a user and all of their data. Admin only. */
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(user: CurrentUser, @PathVariable @Min(1) userId: Long) = userService.delete(user, userId)
}
