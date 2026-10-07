package edu.csumb.magicshroom.api.controller

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.dto.CardInput
import edu.csumb.magicshroom.api.dto.CardPatch
import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.Color
import edu.csumb.magicshroom.api.service.CardService
import edu.csumb.magicshroom.api.service.CardSort
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.net.URI

/** `/api/v1/cards`: public browsing; creating, replacing, updating, and deleting need the ADMIN role. */
@RestController
@RequestMapping("/api/v1/cards")
class CardController(private val cardService: CardService) {

	/** Lists cards, filtered by name and/or color, one page at a time. Public. */
	@GetMapping
	fun list(
		@RequestParam(required = false) @Size(min = 1, message = "must not be empty") name: String?,
		@RequestParam(required = false) color: Color?,
		@RequestParam(defaultValue = "cardName,asc") sort: String,
		@RequestParam(defaultValue = "0") @Min(0) page: Int,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): PageResponse<Card> = cardService.list(name, color, CardSort.fromParam(sort), page, size)

	/** One card. Public. */
	@GetMapping("/{cardId}")
	fun get(@PathVariable @Min(1) cardId: Long): Card = cardService.get(cardId)

	/** Creates a card and returns it with a `Location` header. Admin only. */
	@PostMapping
	fun create(user: CurrentUser, @Valid @RequestBody input: CardInput): ResponseEntity<Card> {
		val card = cardService.create(user, input)
		return ResponseEntity.created(URI.create("/api/v1/cards/${card.id}")).body(card)
	}

	/** Replaces a card completely. Admin only. */
	@PutMapping("/{cardId}")
	fun replace(user: CurrentUser, @PathVariable @Min(1) cardId: Long, @Valid @RequestBody input: CardInput): Card =
		cardService.replace(user, cardId, input)

	/** Changes some fields of a card. Admin only. */
	@PatchMapping("/{cardId}")
	fun update(user: CurrentUser, @PathVariable @Min(1) cardId: Long, @Valid @RequestBody patch: CardPatch): Card =
		cardService.update(user, cardId, patch)

	/** Deletes a card that no deck uses. Admin only. */
	@DeleteMapping("/{cardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(user: CurrentUser, @PathVariable @Min(1) cardId: Long) = cardService.delete(user, cardId)
}
