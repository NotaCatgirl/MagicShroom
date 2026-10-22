package edu.csumb.magicshroom

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import edu.csumb.magicshroom.data.AppContainer
import okhttp3.OkHttpClient

/** Process-wide entry point (registered in AndroidManifest.xml); owns the [AppContainer]. */
class MagicShroomApplication : Application(), SingletonImageLoader.Factory {
    /** Creates Coil's network client with headers accepted by Scryfall's image CDN. */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val imageClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "MagicShroom/1.0")
                    .header("Accept", "image/jpeg,image/*;q=0.9")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(imageClient)) }
            .build()
    }

    /** Shared repositories and clients for the whole app. */
    val container: AppContainer by lazy { AppContainer() }
}
