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
) {
	/** Deletes user [userId] and everything they own: their decks and the cards in them. Admin only. */
	fun delete(user: CurrentUser, userId: Long) {
		user.requireAdmin()
		userRepository.findById(userId) ?: throw NotFoundException("User $userId was not found.")
		deckRepository.findByUserId(userId).forEach { deck ->
			deckCardRepository.deleteByDeckId(deck.id)
			deckRepository.deleteById(deck.id)
		}
		userRepository.deleteById(userId)
	}
}
