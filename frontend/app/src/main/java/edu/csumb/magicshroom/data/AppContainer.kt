package edu.csumb.magicshroom.data

import edu.csumb.magicshroom.BuildConfig
import edu.csumb.magicshroom.data.api.StubAuthInterceptor
import edu.csumb.magicshroom.data.api.createMagicShroomApi
import edu.csumb.magicshroom.data.repository.CardRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Creates the app's shared objects once (manual dependency injection). ViewModel factories get
 * them through [edu.csumb.magicshroom.MagicShroomApplication.container]. Add new repositories here.
 */
class AppContainer {
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(StubAuthInterceptor(BuildConfig.STUB_USER_ID))
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
}
