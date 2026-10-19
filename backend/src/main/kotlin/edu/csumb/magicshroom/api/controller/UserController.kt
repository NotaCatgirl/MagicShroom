package edu.csumb.magicshroom.api.controller

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.dto.UserPatch
import edu.csumb.magicshroom.api.model.Role
import edu.csumb.magicshroom.api.model.User
import edu.csumb.magicshroom.api.service.UserService
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** `/api/v1/users`: the caller's own account (`/me`), and admin-only account management. */
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

	/** Lists users one page at a time, optionally only one role. Admin only. */
	@GetMapping
	fun list(
		user: CurrentUser,
		@RequestParam(required = false) role: Role?,
		@RequestParam(defaultValue = "0") @Min(0) page: Int,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): PageResponse<User> = userService.list(user, role, page, size)

	/** The signed-in user's own account, including their role. */
	@GetMapping("/me")
	fun me(user: CurrentUser): User = userService.me(user)

	/** Deletes the signed-in user's account and all of their data. */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun deleteMe(user: CurrentUser) = userService.deleteSelf(user)

	/** One user. Admin only. */
	@GetMapping("/{userId}")
	fun get(user: CurrentUser, @PathVariable @Min(1) userId: Long): User = userService.get(user, userId)

	/** Grants or revokes the ADMIN role. Admin only. */
	@PatchMapping("/{userId}")
	fun updateRole(user: CurrentUser, @PathVariable @Min(1) userId: Long, @Valid @RequestBody patch: UserPatch): User =
		userService.updateRole(user, userId, patch)

	/** Deletes a user and all of their data. Admin only. */
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(user: CurrentUser, @PathVariable @Min(1) userId: Long) = userService.delete(user, userId)
}
