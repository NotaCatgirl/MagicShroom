package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.error.NotFoundException
import edu.csumb.magicshroom.api.repository.DeckCardRepository
import edu.csumb.magicshroom.api.repository.DeckRepository
import edu.csumb.magicshroom.api.repository.UserRepository
import org.springframework.stereotype.Service

/** Admin management of user accounts. */
@Service
class UserService(
	private val userRepository: UserRepository,
	private val deckRepository: DeckRepository,
	private val deckCardRepository: DeckCardRepository,
	private val authAdminClient: SupabaseAuthAdminClient,
) {
	/** Deletes user [userId] from Supabase Auth, which cascades through their application data. Admin only. */
	fun delete(user: CurrentUser, userId: Long) {
		user.requireAdmin()
		userRepository.findById(userId) ?: throw NotFoundException("User $userId was not found.")
		val authUserId = userRepository.findAuthUserIdById(userId)
			?: throw NotFoundException("User $userId was not found.")
		authAdminClient.deleteUser(authUserId)
		// These no-ops in PostgreSQL after ON DELETE CASCADE; they keep the JSON mock routes representative.
		deckRepository.findByUserId(userId).forEach { deck ->
			deckCardRepository.deleteByDeckId(deck.id)
			deckRepository.deleteById(deck.id)
		}
		userRepository.deleteById(userId)
	}
}
