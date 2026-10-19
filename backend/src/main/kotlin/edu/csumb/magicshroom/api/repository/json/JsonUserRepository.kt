package edu.csumb.magicshroom.api.repository.json

import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.model.Role
import edu.csumb.magicshroom.api.model.User
import edu.csumb.magicshroom.api.repository.UserRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.concurrent.ConcurrentHashMap

/** [UserRepository] backed by `mock-data/users.json`. Changes live in memory until restart. */
@Repository
@Profile("mock")
class JsonUserRepository(jsonMapper: JsonMapper) : UserRepository {
	private val users = ConcurrentHashMap<Long, User>()

	init {
		jsonMapper.readMockData<User>("users.json").forEach { users[it.id] = it }
	}

	override fun findById(id: Long): User? = users[id]

	override fun findPage(role: Role?, page: Int, size: Int): PageResponse<User> =
		PageResponse.of(users.values.filter { role == null || it.role == role }.sortedBy { it.id }, page, size)

	override fun countByRole(role: Role): Long = users.values.count { it.role == role }.toLong()

	override fun updateRole(id: Long, role: Role): User? = users.computeIfPresent(id) { _, user -> user.copy(role = role) }

	override fun deleteById(id: Long): Boolean = users.remove(id) != null
}
