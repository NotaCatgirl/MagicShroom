package edu.csumb.magicshroom.api.service

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.dto.CardInput
import edu.csumb.magicshroom.api.dto.CardPatch
import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.error.BadRequestException
import edu.csumb.magicshroom.api.error.ConflictException
import edu.csumb.magicshroom.api.error.NotFoundException
import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.Color
import edu.csumb.magicshroom.api.repository.CardRepository
import edu.csumb.magicshroom.api.repository.DeckCardRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** Browsing the public card catalog, and admin-only changes to it. */
@Service
@Transactional
class CardService(
	private val cardRepository: CardRepository,
	private val deckCardRepository: DeckCardRepository,
) {
	/**
	 * One page of cards whose name contains [name] (ignoring case) and whose color identity includes
	 * [color], in [sort] order. Either filter may be null to skip it.
	 */
	fun list(name: String?, color: Color?, sort: CardSort, page: Int, size: Int): PageResponse<Card> {
		return cardRepository.search(name, color, sort, page, size)
	}

	/** The card with [cardId]; 404 if there is none. */
	fun get(cardId: Long): Card =
		cardRepository.findById(cardId) ?: throw NotFoundException("Card $cardId was not found.")

	/** Returns distinct requested cards in request order, with one 404 listing any missing IDs. */
	fun getBatch(cardIds: List<Long>): List<Card> {
		if (cardIds.any { it <= 0 }) throw BadRequestException("Card IDs must be positive.")
		val uniqueIds = cardIds.distinct()
		val found = cardRepository.findByIds(uniqueIds).associateBy(Card::id)
		val missing = uniqueIds.filterNot(found::containsKey)
		if (missing.isNotEmpty()) throw NotFoundException("Cards not found: ${missing.joinToString(", ")}.")
		return uniqueIds.map(found::getValue)
	}

	/** Adds a card to the catalog. Admin only; 409 if another card already has its uuid. */
	fun create(user: CurrentUser, input: CardInput): Card {
		user.requireAdmin()
		requireUuidFree(input.uuid, exceptCardId = null)
		return cardRepository.insert(input.toCard(id = 0))
	}

	/** Replaces every field of card [cardId]. Admin only; idempotent. */
	fun replace(user: CurrentUser, cardId: Long, input: CardInput): Card {
		user.requireAdmin()
		get(cardId)
		requireUuidFree(input.uuid, exceptCardId = cardId)
		return cardRepository.update(input.toCard(id = cardId))
	}

	/** Changes only the fields present in [patch]. Admin only; 400 if [patch] is empty. */
	fun update(user: CurrentUser, cardId: Long, patch: CardPatch): Card {
		user.requireAdmin()
		if (patch.isEmpty()) throw BadRequestException("Send at least one field to change.")
		val card = get(cardId)
		patch.uuid?.let { requireUuidFree(it, exceptCardId = cardId) }
		return cardRepository.update(patch.applyTo(card))
	}

	/** Deletes card [cardId]. Admin only; 409 while any deck still contains it. */
	fun delete(user: CurrentUser, cardId: Long) {
		user.requireAdmin()
		get(cardId)
		if (deckCardRepository.existsByCardId(cardId)) {
			throw ConflictException("Card $cardId is still used by a deck.")
		}
		cardRepository.deleteById(cardId)
	}

	private fun requireUuidFree(uuid: UUID, exceptCardId: Long?) {
		val owner = cardRepository.findByUuid(uuid)
		if (owner != null && owner.id != exceptCardId) {
			throw ConflictException("Card ${owner.id} already has uuid $uuid.")
		}
	}
}
