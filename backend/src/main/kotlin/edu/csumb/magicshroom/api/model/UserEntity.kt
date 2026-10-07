package edu.csumb.magicshroom.api.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "users")
open class UserEntity(
	@field:Column(nullable = false, unique = true)
	open var email: String = "",
	open var name: String = "",
	@field:Column(name = "display_name")
	open var displayName: String = "",
) {
	@field:Id
	@field:GeneratedValue(strategy = GenerationType.IDENTITY)
	open var id: Long? = null
}
