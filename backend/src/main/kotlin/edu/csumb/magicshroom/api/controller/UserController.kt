package edu.csumb.magicshroom.api.controller

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.config.BAD_REQUEST
import edu.csumb.magicshroom.api.config.BEARER_AUTH
import edu.csumb.magicshroom.api.config.CONFLICT
import edu.csumb.magicshroom.api.config.FORBIDDEN
import edu.csumb.magicshroom.api.config.NOT_FOUND
import edu.csumb.magicshroom.api.config.SERVICE_UNAVAILABLE
import edu.csumb.magicshroom.api.config.UNAUTHORIZED
import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.dto.UserPatch
import edu.csumb.magicshroom.api.model.Role
import edu.csumb.magicshroom.api.model.User
import edu.csumb.magicshroom.api.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Users", description = "The signed-in user's own account, and admin-only account management.")
@SecurityRequirement(name = BEARER_AUTH)
@RestController
@RequestMapping("/api/v1/users")
class UserController(private val userService: UserService) {

	/** Lists users one page at a time, optionally only one role. Admin only. */
	@Operation(summary = "List users", description = "Requires a bearer token for a user with the ADMIN role.")
	@ApiResponse(responseCode = "200", description = "A page of users")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@GetMapping
	fun list(
		user: CurrentUser,
		@RequestParam(required = false) role: Role?,
		@RequestParam(defaultValue = "0") @Min(0) page: Int,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): PageResponse<User> = userService.list(user, role, page, size)

	/** The signed-in user's own account, including their role. */
	@Operation(summary = "Get my account", description = "The account is created on first sign-in; role is USER or ADMIN.")
	@ApiResponse(responseCode = "200", description = "The signed-in user's account")
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@GetMapping("/me")
	fun me(user: CurrentUser): User = userService.me(user)

	/** Deletes the signed-in user's account and all of their data. */
	@Operation(
		summary = "Delete my account",
		description = "Deletes the account, its decks, and its Supabase login (503 if the server lacks the admin key). The last admin gets 409.",
	)
	@ApiResponse(responseCode = "204", description = "Account deleted")
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "409", ref = CONFLICT)
	@ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun deleteMe(user: CurrentUser) = userService.deleteSelf(user)

	/** One user. Admin only. */
	@Operation(summary = "Get a user", description = "Requires a bearer token for a user with the ADMIN role.")
	@ApiResponse(responseCode = "200", description = "The user")
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@GetMapping("/{userId}")
	fun get(user: CurrentUser, @PathVariable @Min(1) userId: Long): User = userService.get(user, userId)

	/** Grants or revokes the ADMIN role. Admin only. */
	@Operation(summary = "Change a user's role", description = "Requires the ADMIN role. Demoting the last admin is a conflict (409).")
	@ApiResponse(responseCode = "200", description = "The updated user")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@ApiResponse(responseCode = "409", ref = CONFLICT)
	@PatchMapping("/{userId}")
	fun updateRole(user: CurrentUser, @PathVariable @Min(1) userId: Long, @Valid @RequestBody patch: UserPatch): User =
		userService.updateRole(user, userId, patch)

	/** Deletes a user and all of their data. Admin only. */
	@Operation(
		summary = "Delete a user",
		description = "Requires the ADMIN role. Also deletes the user's Supabase login when the server has the admin key (503 if not).",
	)
	@ApiResponse(responseCode = "204", description = "User deleted")
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@ApiResponse(responseCode = "409", ref = CONFLICT)
	@ApiResponse(responseCode = "503", ref = SERVICE_UNAVAILABLE)
	@DeleteMapping("/{userId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(user: CurrentUser, @PathVariable @Min(1) userId: Long) = userService.delete(user, userId)
}
