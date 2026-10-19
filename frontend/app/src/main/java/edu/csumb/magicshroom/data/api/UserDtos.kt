package edu.csumb.magicshroom.data.api

import kotlinx.serialization.Serializable

/** What a user may do (`Role` in docs/openapi.yaml). Stored in the database, never decided by the app. */
@Serializable
enum class Role { USER, ADMIN }

/** A user account as the API returns it (`User`). */
@Serializable
data class UserDto(val id: Long, val email: String, val displayName: String, val role: Role)

/** One page of users (`UserPage`). */
@Serializable
data class UserPageDto(val content: List<UserDto>, val page: PageMetaDto)

/** Body of PATCH /api/v1/users/{userId} (`UserPatch`). */
@Serializable
data class UserRolePatchDto(val role: Role)
