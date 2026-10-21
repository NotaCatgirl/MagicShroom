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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/** Legacy card details component retained for the existing card-display test and later search work. */
@Composable
internal fun CardDetails(card: CardSummary) {
    Text("${card.name} ${card.manaCost.orEmpty()}")
    card.text?.let { Text(it) }
}

/** Legacy card list retained for the existing card-display test and later search work. */
@Composable
internal fun CardList(cards: List<CardSummary>, onSelectCard: (CardSummary) -> Unit) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(cards, key = { it.id }) { card ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelectCard(card) }) {
                Text(card.name, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
