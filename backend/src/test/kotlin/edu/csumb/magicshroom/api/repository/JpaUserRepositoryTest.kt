package edu.csumb.magicshroom.api.repository

import edu.csumb.magicshroom.api.model.UserEntity
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.dao.DataIntegrityViolationException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@DataJpaTest
class JpaUserRepositoryTest(
	@Autowired private val repository: JpaUserRepository,
	@Autowired private val entityManager: EntityManager,
) {
	@Test
	fun `saves and retrieves a user`() {
		val saved = repository.saveAndFlush(
			UserEntity(email = "kyle@example.com", name = "Kyle Parker", displayName = "Kyle"),
		)
		entityManager.clear()

		val found = repository.findById(requireNotNull(saved.id)).orElseThrow()
		assertEquals(saved.id, found.id)
		assertEquals("kyle@example.com", found.email)
		assertEquals("Kyle Parker", found.name)
		assertEquals("Kyle", found.displayName)
	}

	@Test
	fun `rejects a duplicate email`() {
		repository.saveAndFlush(UserEntity(email = "same@example.com", name = "First"))

		assertFailsWith<DataIntegrityViolationException> {
			repository.saveAndFlush(UserEntity(email = "same@example.com", name = "Second"))
		}
	}
}
