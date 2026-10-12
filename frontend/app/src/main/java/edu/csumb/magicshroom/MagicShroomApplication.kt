package edu.csumb.magicshroom

import android.app.Application
import edu.csumb.magicshroom.data.AppContainer

/** Process-wide entry point (registered in AndroidManifest.xml); owns the [AppContainer]. */
class MagicShroomApplication : Application() {
    /** Shared repositories and clients for the whole app. */
    val container: AppContainer by lazy { AppContainer() }
}
