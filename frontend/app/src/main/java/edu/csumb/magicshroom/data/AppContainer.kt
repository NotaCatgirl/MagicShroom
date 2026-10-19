package edu.csumb.magicshroom.data

import edu.csumb.magicshroom.BuildConfig
import edu.csumb.magicshroom.data.api.StubAuthInterceptor
import edu.csumb.magicshroom.data.api.createMagicShroomApi
import edu.csumb.magicshroom.data.repository.ApiUserRepository
import edu.csumb.magicshroom.data.repository.CardRepository
import edu.csumb.magicshroom.data.repository.UserRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Creates the app's shared objects once (manual dependency injection). ViewModel factories get
 * them through [edu.csumb.magicshroom.MagicShroomApplication.container]. Add new repositories here.
 */
class AppContainer {
    private val okHttpClient = OkHttpClient.Builder()
        .apply {
            // Debug-only: lets the app act as a mock-profile user (e.g. -PstubUserId=1 for the admin)
            // against `./gradlew bootTestRun --args='--spring.profiles.active=mock'`. The real backend
            // ignores this header. TODO(auth): replace with the Supabase bearer-token interceptor.
            if (BuildConfig.STUB_USER_ID.isNotBlank()) addInterceptor(StubAuthInterceptor(BuildConfig.STUB_USER_ID))
        }
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                // Logs "GET /api/v1/cards -> 200" lines to Logcat in debug builds.
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            },
        )
        .build()

    private val api = createMagicShroomApi(BuildConfig.API_BASE_URL, okHttpClient)

    /** Card catalog calls. */
    val cardRepository = CardRepository(api)

    /** The signed-in user's account and admin user management. */
    val userRepository: UserRepository = ApiUserRepository(api)
}
