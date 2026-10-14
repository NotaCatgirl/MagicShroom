package edu.csumb.magicshroom.data.api

import kotlinx.serialization.Serializable

/** The RFC 9457 error body every API error uses (`Problem`). */
@Serializable
data class ProblemDto(
    val type: String? = null,
    val title: String? = null,
    val status: Int? = null,
    val detail: String? = null,
    val instance: String? = null,
)
