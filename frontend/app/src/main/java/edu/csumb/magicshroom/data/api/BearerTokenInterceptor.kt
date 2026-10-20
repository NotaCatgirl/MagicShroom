package edu.csumb.magicshroom.data.api

import okhttp3.Interceptor
import okhttp3.Response

/** Adds the current Supabase token to requests that require API authentication. */
class BearerTokenInterceptor(private val tokenProvider: () -> String?) : Interceptor {
    /** Sends each request with the latest nonblank token, omitting auth when no session exists. */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider()?.takeIf(String::isNotBlank) ?: return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
    }
}
