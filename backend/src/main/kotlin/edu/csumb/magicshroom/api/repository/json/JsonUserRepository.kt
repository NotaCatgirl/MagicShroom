package edu.csumb.magicshroom.api.repository.json

import edu.csumb.magicshroom.api.model.User
import edu.csumb.magicshroom.api.repository.UserRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
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

	/** Returns a stable stand-in identity for the mock-only deletion flow. */
	override fun findAuthUserIdById(id: Long): UUID? = id.takeIf(users::containsKey)?.let {
		UUID.nameUUIDFromBytes("mock-auth-user-$it".toByteArray())
	}

	override fun deleteById(id: Long): Boolean = users.remove(id) != null
}
