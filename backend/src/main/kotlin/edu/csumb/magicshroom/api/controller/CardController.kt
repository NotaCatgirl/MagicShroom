package edu.csumb.magicshroom.api.controller

import edu.csumb.magicshroom.api.auth.CurrentUser
import edu.csumb.magicshroom.api.config.BAD_REQUEST
import edu.csumb.magicshroom.api.config.BEARER_AUTH
import edu.csumb.magicshroom.api.config.CONFLICT
import edu.csumb.magicshroom.api.config.FORBIDDEN
import edu.csumb.magicshroom.api.config.NOT_FOUND
import edu.csumb.magicshroom.api.config.UNAUTHORIZED
import edu.csumb.magicshroom.api.dto.CardBatchRequest
import edu.csumb.magicshroom.api.dto.CardInput
import edu.csumb.magicshroom.api.dto.CardPatch
import edu.csumb.magicshroom.api.dto.PageResponse
import edu.csumb.magicshroom.api.model.Card
import edu.csumb.magicshroom.api.model.Color
import edu.csumb.magicshroom.api.service.CardService
import edu.csumb.magicshroom.api.service.CardSort
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(name = "Cards", description = "The public card catalog; changing it requires the ADMIN role.")
@RestController
@RequestMapping("/api/v1/cards")
class CardController(private val cardService: CardService) {

	/** Lists cards, filtered by name and/or color, one page at a time. Public. */
	@Operation(
		summary = "List and search cards",
		description = "Filters by name (case-insensitive substring) and color identity, sorted and paged. Public.",
	)
	@ApiResponse(responseCode = "200", description = "A page of cards with paging metadata")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@GetMapping
	fun list(
		@RequestParam(required = false) @Size(min = 1, message = "must not be empty") name: String?,
		@RequestParam(required = false) color: Color?,
		@RequestParam(defaultValue = "cardName,asc") sort: String,
		@RequestParam(defaultValue = "0") @Min(0) page: Int,
		@RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
	): PageResponse<Card> = cardService.list(name, color, CardSort.fromParam(sort), page, size)

	/** One card. Public. */
	@Operation(summary = "Get a card", description = "Full card details, including legalities, rulings, and image URLs. Public.")
	@ApiResponse(responseCode = "200", description = "The card")
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@GetMapping("/{cardId}")
	fun get(@PathVariable @Min(1) cardId: Long): Card = cardService.get(cardId)

	/** Fetches up to one hundred cards in one public request. */
	@Operation(
		summary = "Get several cards by ID",
		description = "Returns the cards in request order with duplicates removed; at most 100 IDs. Public.",
	)
	@ApiResponse(responseCode = "200", description = "The requested cards")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@PostMapping("/batch")
	fun batch(@Valid @RequestBody request: CardBatchRequest): List<Card> = cardService.getBatch(request.cardIds)

	/** Up to [limit] cards most like this one, best first. Public. */
	@Operation(
		summary = "Find similar cards",
		description = "Cards sharing a main type, ranked by colors, mana value, shared subtypes, and popularity. Public.",
	)
	@ApiResponse(responseCode = "200", description = "Similar cards, best match first")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@GetMapping("/{cardId}/similar")
	fun similar(
		@PathVariable @Min(1) cardId: Long,
		@RequestParam(defaultValue = "10") @Min(1) @Max(30) limit: Int,
	): List<Card> = cardService.similar(cardId, limit)

	/** Creates a card and returns it with a `Location` header. Admin only. */
	@Operation(summary = "Create a card", description = "Requires a bearer token for a user with the ADMIN role.")
	@SecurityRequirement(name = BEARER_AUTH)
	@ApiResponse(responseCode = "201", description = "The created card, with its URL in the Location header")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@ApiResponse(responseCode = "409", ref = CONFLICT)
	@PostMapping
	fun create(user: CurrentUser, @Valid @RequestBody input: CardInput): ResponseEntity<Card> {
		val card = cardService.create(user, input)
		return ResponseEntity.created(URI.create("/api/v1/cards/${card.id}")).body(card)
	}

	/** Replaces a card completely. Admin only. */
	@Operation(summary = "Replace a card", description = "Requires a bearer token for a user with the ADMIN role.")
	@SecurityRequirement(name = BEARER_AUTH)
	@ApiResponse(responseCode = "200", description = "The replaced card")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@ApiResponse(responseCode = "409", ref = CONFLICT)
	@PutMapping("/{cardId}")
	fun replace(user: CurrentUser, @PathVariable @Min(1) cardId: Long, @Valid @RequestBody input: CardInput): Card =
		cardService.replace(user, cardId, input)

	/** Changes some fields of a card. Admin only. */
	@Operation(summary = "Update some card fields", description = "Requires a bearer token for a user with the ADMIN role.")
	@SecurityRequirement(name = BEARER_AUTH)
	@ApiResponse(responseCode = "200", description = "The updated card")
	@ApiResponse(responseCode = "400", ref = BAD_REQUEST)
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@ApiResponse(responseCode = "409", ref = CONFLICT)
	@PatchMapping("/{cardId}")
	fun update(user: CurrentUser, @PathVariable @Min(1) cardId: Long, @Valid @RequestBody patch: CardPatch): Card =
		cardService.update(user, cardId, patch)

	/** Deletes a card that no deck uses. Admin only. */
	@Operation(summary = "Delete a card", description = "Requires the ADMIN role. A card that any deck still uses cannot be deleted (409).")
	@SecurityRequirement(name = BEARER_AUTH)
	@ApiResponse(responseCode = "204", description = "Card deleted")
	@ApiResponse(responseCode = "401", ref = UNAUTHORIZED)
	@ApiResponse(responseCode = "403", ref = FORBIDDEN)
	@ApiResponse(responseCode = "404", ref = NOT_FOUND)
	@ApiResponse(responseCode = "409", ref = CONFLICT)
	@DeleteMapping("/{cardId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun delete(user: CurrentUser, @PathVariable @Min(1) cardId: Long) = cardService.delete(user, cardId)
}
