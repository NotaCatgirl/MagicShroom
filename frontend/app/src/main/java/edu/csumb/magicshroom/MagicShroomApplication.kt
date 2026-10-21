package edu.csumb.magicshroom

import android.app.Application
import android.content.Context
import edu.csumb.magicshroom.data.AppContainer
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

/** Process-wide entry point (registered in AndroidManifest.xml); owns the [AppContainer]. */
class MagicShroomApplication : Application(), SingletonImageLoader.Factory {
    /** Shared repositories and clients for the whole app. */
    val container: AppContainer by lazy { AppContainer() }

    /** Configures Coil's shared loader to fetch card images over OkHttp. */
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
}
