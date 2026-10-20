package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.auth.AuthAccountDeleter
import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.dto.UserPatch
import edu.csumb.magicshroom.api.error.ConflictException
import edu.csumb.magicshroom.api.error.NotFoundException
import edu.csumb.magicshroom.api.model.Role
import edu.csumb.magicshroom.api.model.User
import edu.csumb.magicshroom.api.repository.DeckCardRepository
import edu.csumb.magicshroom.api.repository.DeckRepository
import edu.csumb.magicshroom.api.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** User accounts: the caller's own account, and admin-only management of everyone's. */
@Service
class UserService(
	private val userRepository: UserRepository,
	private val deckRepository: DeckRepository,
	private val deckCardRepository: DeckCardRepository,
	private val authAccounts: AuthAccountDeleter,
) {
	/** One page of users in id order, optionally only those with [role]. Admin only. */
	@Transactional(readOnly = true)
	fun list(user: CurrentUser, role: Role?, page: Int, size: Int): PageResponse<User> {
		user.requireAdmin()
		return userRepository.findPage(role, page, size)
	}

	/** The signed-in user's own account, including their role. */
	@Transactional(readOnly = true)
	fun me(user: CurrentUser): User = find(user.id)

	/** One user. Admin only; 404 if there is none. */
	@Transactional(readOnly = true)
	fun get(user: CurrentUser, userId: Long): User {
		user.requireAdmin()
		return find(userId)
	}

	/** Grants or revokes the ADMIN role. Admin only; 409 if it would leave no admin. */
	fun updateRole(user: CurrentUser, userId: Long, patch: UserPatch): User {
		user.requireAdmin()
		val target = find(userId)
		if (target.role == Role.ADMIN && patch.role != Role.ADMIN) requireAnotherAdmin()
		return userRepository.updateRole(userId, patch.role) ?: throw NotFoundException("User $userId was not found.")
	}

	/** Deletes user [userId] and everything they own. Admin only; 409 for the last admin. */
	fun delete(user: CurrentUser, userId: Long) {
		user.requireAdmin()
		purge(find(userId))
	}

	/** Deletes the signed-in user's own account and data; 409 if they are the last admin. */
	fun deleteSelf(user: CurrentUser) = purge(find(user.id))

	private fun purge(target: User) {
		if (target.role == Role.ADMIN) requireAnotherAdmin()
		// Delete the Supabase Auth user before writing anything here: its foreign key cascades to
		// app_users, decks and deck_cards, and this transaction must not hold locks on those rows yet.
		userRepository.findAuthUserId(target.id)?.let(authAccounts::delete)
		deckRepository.findByUserId(target.id).forEach { deck ->
			deckCardRepository.deleteByDeckId(deck.id)
			deckRepository.deleteById(deck.id)
		}
		userRepository.deleteById(target.id)
	}

	private fun requireAnotherAdmin() {
		if (userRepository.countByRole(Role.ADMIN) <= 1) throw ConflictException("At least one admin must remain.")
	}

	private fun find(userId: Long): User =
		userRepository.findById(userId) ?: throw NotFoundException("User $userId was not found.")
}
