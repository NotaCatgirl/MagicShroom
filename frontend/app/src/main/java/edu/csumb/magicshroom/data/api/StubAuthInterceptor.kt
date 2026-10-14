package edu.csumb.magicshroom.data.api

import okhttp3.Interceptor
import okhttp3.Response

// TODO(auth): replace with an interceptor that sends "Authorization: Bearer <token>" after PKCE sign-in.
/**
 * TEMPORARY: tells the backend's stub authentication who the caller is via `X-Stub-User-Id`.
 * Replace with an interceptor that sends `Authorization: Bearer <token>` once PKCE sign-in exists.
 * A blank [userId] sends nothing.
 */
class StubAuthInterceptor(private val userId: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (userId.isBlank()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(HEADER, userId).build())
    }

    companion object {
        /** Header the backend's `StubCurrentUserResolver` reads. */
        const val HEADER = "X-Stub-User-Id"
    }
}
