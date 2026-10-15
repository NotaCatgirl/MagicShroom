package edu.csumb.magicshroom

import android.os.Build
import java.net.URI

/** Requires a local-network grant only for the Android 17+ debug emulator backend. */
internal fun requiresEmulatorNetworkPermission(backendUrl: String, sdkInt: Int, debugBuild: Boolean): Boolean =
    debugBuild && sdkInt >= Build.VERSION_CODES.CINNAMON_BUN &&
        runCatching { URI(backendUrl).host == "10.0.2.2" }.getOrDefault(false)
