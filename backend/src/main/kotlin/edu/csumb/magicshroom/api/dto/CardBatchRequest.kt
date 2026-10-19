package edu.csumb.magicshroom.api.dto

import jakarta.validation.constraints.Size

/** Body of POST /api/v1/cards/batch; request a bounded set of full card records in one call. */
data class CardBatchRequest(@field:Size(min = 1, max = 100) val cardIds: List<Long>)
