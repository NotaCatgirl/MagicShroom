package edu.csumb.magicshroom.data.repository

import edu.csumb.magicshroom.data.api.ProblemDto
import edu.csumb.magicshroom.data.api.apiJson
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** Outcome of an API call. Repositories return this instead of throwing. */
sealed interface ApiResult<out T> {
    /** The call worked. */
    data class Success<T>(val data: T) : ApiResult<T>

    /** The call failed; [userMessage] is safe to show on screen. */
    sealed interface Failure : ApiResult<Nothing> {
        val userMessage: String
    }

    /** The server answered with an error [status]; [detail] is its Problem Details `detail`, if any. */
    data class HttpError(val status: Int, val detail: String?) : Failure {
        override val userMessage: String
            get() = when (status) {
                // TODO(auth): on 401, also clear the stored token and send the user back to Login.
                401 -> "Please sign in again."
                403 -> "You don't have permission to do that."
                404 -> detail ?: "That doesn't exist anymore."
                in 500..599 -> "The server ran into a problem. Please try again."
                else -> detail ?: "Request failed ($status)."
            }
    }

    /** No usable response: the server is unreachable, timed out, or sent something unreadable. */
    data class NetworkError(val cause: Throwable) : Failure {
        override val userMessage: String
            get() = "Can't reach the server. Check your connection and that the API is running."
    }
}

/** Runs [call] and wraps its result, or its HTTP/network failure, in an [ApiResult]. */
suspend fun <T> apiCall(call: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(call())
} catch (e: HttpException) {
    ApiResult.HttpError(e.code(), e.response()?.errorBody()?.string()?.let(::problemDetail))
} catch (e: IOException) {
    ApiResult.NetworkError(e)
} catch (e: SerializationException) {
    ApiResult.NetworkError(e)
}

private fun problemDetail(body: String): String? =
    runCatching { apiJson.decodeFromString<ProblemDto>(body).detail }.getOrNull()
