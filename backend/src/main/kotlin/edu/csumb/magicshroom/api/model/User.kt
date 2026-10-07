package edu.csumb.magicshroom.api.model

/** What a user is allowed to do. The role is stored with the user, never hardcoded. */
enum class Role { USER, ADMIN }

/** An account. Created on first sign-in once OAuth2 is wired up; no passwords are stored. */
data class User(
	val id: Long,
	val email: String,
	val displayName: String,
	val role: Role,
)
