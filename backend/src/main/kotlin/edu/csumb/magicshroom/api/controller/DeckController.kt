package edu.csumb.magicshroom.api.controller

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.dto.DeckCardCreate
import edu.csumb.magicshroom.api.dto.DeckCardPatch
import edu.csumb.magicshroom.api.dto.DeckCardResponse
import edu.csumb.magicshroom.api.dto.DeckCreate
import edu.csumb.magicshroom.api.dto.DeckPatch
import edu.csumb.magicshroom.api.dto.DeckResponse
import edu.csumb.magicshroom.api.service.DeckService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** `/api/v1/decks`: the signed-in user's own decks and the cards in them. */
@RestController
@RequestMapping("/api/v1/decks")
class DeckController(private val deckService: DeckService) {

	/** Lists my decks, optionally filtered by name. */
	@GetMapping
	fun list(
		user: CurrentUser,
		@RequestParam(required = false) @Size(min = 1, message = "must not be empty") name: String?,
	): List<DeckResponse> =
		deckService.list(user, name)

	/** Creates an empty deck and returns it with a `Location` header. */
	@PostMapping
	fun create(user: CurrentUser, @Valid @RequestBody request: DeckCreate): ResponseEntity<DeckResponse> {
		val deck = deckService.create(user, request)
		return ResponseEntity.created(URI.create("/api/v1/decks/${deck.id}")).body(deck)
	}

	/** One of my decks with its cards. */
	@GetMapping("/{deckId}")
	fun get(user: CurrentUser, @PathVariable @Min(1) deckId: Long): DeckResponse = deckService.get(user, deckId)

	/** Renames one of my decks. */
	@PatchMapping("/{deckId}")
	fun rename(user: CurrentUser, @PathVariable @Min(1) deckId: Long, @Valid @RequestBody patch: DeckPatch): DeckResponse =
		deckService.rename(user, deckId, patch)

	/** Deletes one of my decks and its cards. */
	@DeleteMapping("/{deckId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(user: CurrentUser, @PathVariable @Min(1) deckId: Long) = deckService.delete(user, deckId)

	/** Adds a card to one of my decks. */
	@PostMapping("/{deckId}/cards")
	@ResponseStatus(HttpStatus.CREATED)
	fun addCard(
		user: CurrentUser,
		@PathVariable @Min(1) deckId: Long,
		@Valid @RequestBody request: DeckCardCreate,
	): DeckCardResponse = deckService.addCard(user, deckId, request)

	/** Changes a card's quantity or section in one of my decks. */
	@PatchMapping("/{deckId}/cards/{cardId}")
	fun updateCard(
		user: CurrentUser,
		@PathVariable @Min(1) deckId: Long,
		@PathVariable @Min(1) cardId: Long,
		@Valid @RequestBody patch: DeckCardPatch,
	): DeckCardResponse = deckService.updateCard(user, deckId, cardId, patch)

	/** Removes a card from one of my decks. */
	@DeleteMapping("/{deckId}/cards/{cardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun removeCard(user: CurrentUser, @PathVariable @Min(1) deckId: Long, @PathVariable @Min(1) cardId: Long) =
		deckService.removeCard(user, deckId, cardId)
}
