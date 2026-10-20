package edu.csumb.magicshroom

import android.app.Application
import edu.csumb.magicshroom.data.AppContainer
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

class MagicShroomApplication : Application(), SingletonImageLoader.Factory {
    /** Shared repositories and clients for the whole app. */
    val container: AppContainer by lazy { AppContainer() }

    /** Scryfall rejects OkHttp's default User-Agent, so card images are requested with the app's own. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = {
                OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        chain.proceed(chain.request().newBuilder()
                            .header("User-Agent", "MagicShroom/${BuildConfig.VERSION_NAME} (Android)")
                            .build())
                    }
                    .build()
            }))
        }
        .build()
}
