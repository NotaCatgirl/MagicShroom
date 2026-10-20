package edu.csumb.magicshroom.data

import edu.csumb.magicshroom.BuildConfig
import edu.csumb.magicshroom.data.api.createMagicShroomApi
import edu.csumb.magicshroom.data.auth.GoogleAuthRepository
import edu.csumb.magicshroom.data.repository.CardRepository
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Creates the app's shared objects once (manual dependency injection). ViewModel factories get
 * them through [edu.csumb.magicshroom.MagicShroomApplication.container]. Add new repositories here.
 */
class AppContainer {
    val supabase = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
        install(Auth) { scheme = "magicshroom"; host = "auth" }
    }

    val authRepository = GoogleAuthRepository(supabase)

    private val okHttpClient = OkHttpClient.Builder()
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
