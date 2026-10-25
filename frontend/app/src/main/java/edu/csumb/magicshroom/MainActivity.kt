package edu.csumb.magicshroom

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import edu.csumb.magicshroom.ui.nav.AppNavHost
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import io.github.jan.supabase.auth.handleDeeplinks

/** Hosts the app's login, search, and account navigation flow. */
class MainActivity : ComponentActivity() {
    private var localNetworkGranted by mutableStateOf(false)
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        localNetworkGranted = it
    }

    private val container get() = (application as MagicShroomApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleAuthCallback(intent)
        localNetworkGranted = hasLocalNetworkAccess()
        setContent {
            MagicShroomTheme {
                AppNavHost(
                    authRepository = container.authRepository,
                    canAccessBackend = localNetworkGranted,
                    onRequestLocalNetworkPermission = {
                        localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        localNetworkGranted = hasLocalNetworkAccess()
    }

    private fun hasLocalNetworkAccess(): Boolean =
        !requiresEmulatorNetworkPermission(BuildConfig.BACKEND_URL, Build.VERSION.SDK_INT, BuildConfig.DEBUG) ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    /** Delivers OAuth callbacks to the process-shared Supabase client. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthCallback(intent)
    }

    private fun handleAuthCallback(intent: Intent) {
        container.supabase.handleDeeplinks(intent, onError = {
            container.authRepository.onCallbackError()
        })
    }
}
