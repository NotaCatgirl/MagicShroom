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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.launch

/** Main card browser and personal deck screen. */
class MainActivity : ComponentActivity() {
    private var localNetworkGranted by mutableStateOf(false)
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        localNetworkGranted = it
    }

    private val supabase by lazy {
        createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth) { scheme = "magicshroom"; host = "auth" }
        }
    }

    /** Starts the OAuth deep link handler and the Compose app. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        supabase.handleDeeplinks(intent)
        localNetworkGranted = hasLocalNetworkAccess()
        setContent {
            MagicShroomTheme {
                if (localNetworkGranted) MagicShroomScreen(ApiClient(), supabase.auth)
                else LocalNetworkPermissionScreen {
                    localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
            }
        }
        if (!localNetworkGranted) localNetworkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }

    /** Refreshes the local-network grant after returning from Android settings. */
    override fun onResume() {
        super.onResume()
        localNetworkGranted = hasLocalNetworkAccess()
    }

    /** Checks the runtime permission only when this debug build uses the emulator host. */
    private fun hasLocalNetworkAccess(): Boolean =
        !requiresEmulatorNetworkPermission(BuildConfig.BACKEND_URL, Build.VERSION.SDK_INT, BuildConfig.DEBUG) ||
            checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    /** Delivers a returning Google OAuth callback to Supabase Auth. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        supabase.handleDeeplinks(intent)
    }
}

/** Explains why local browsing is paused until Android allows emulator-host traffic. */
@Composable
private fun LocalNetworkPermissionScreen(onRequestPermission: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Allow Nearby devices access to connect to the local MagicShroom API.")
        Text("If you denied it before, you can also allow it in Settings > Apps > MagicShroom > Permissions.")
        Button(onClick = onRequestPermission) { Text("Allow local connection") }
    }
}

/** Browses the public catalog and edits decks for a signed-in user. */
@Composable
private fun MagicShroomScreen(api: ApiClient, auth: Auth) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var deckName by remember { mutableStateOf("") }
    var cards by remember { mutableStateOf<List<CardSummary>>(emptyList()) }
    var decks by remember { mutableStateOf<List<DeckSummary>>(emptyList()) }
    var selectedDeck by remember { mutableStateOf<Long?>(null) }
    var selectedCard by remember { mutableStateOf<CardSummary?>(null) }
    var page by remember { mutableStateOf(0) }
    var token by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    /** Reloads the public page and reports a network error in the screen. */
    fun loadCards() { scope.launch { runCatching { api.cards(query, page) }
        .onSuccess { cards = it; message = null }
        .onFailure { message = it.message } } }

    /** Reloads the current user's decks after a write or sign-in. */
    fun loadDecks(accessToken: String) { scope.launch { runCatching { api.decks(accessToken) }
        .onSuccess { decks = it; message = null }
        .onFailure { message = it.message } } }

    LaunchedEffect(Unit) { loadCards() }
    LaunchedEffect(auth) {
        auth.sessionStatus.collect {
            token = auth.currentSessionOrNull()?.accessToken
            if (token == null) { decks = emptyList(); selectedDeck = null }
            else loadDecks(requireNotNull(token))
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MagicShroom")
            if (token == null) {
                Button(onClick = {
                    if (BuildConfig.SUPABASE_PUBLISHABLE_KEY == "sb_publishable_replace_me") {
                        message = "Configure the Supabase URL and publishable key first."
                    } else scope.launch { runCatching { auth.signInWith(Google) }.onFailure { message = it.message } }
                }) { Text("Sign in with Google") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Signed in")
                    Button(onClick = { scope.launch { runCatching { auth.signOut() }.onFailure { message = it.message } } }) { Text("Sign out") }
                }
            }
            message?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text("Search cards") }, modifier = Modifier.weight(1f))
                Button(onClick = { page = 0; loadCards() }) { Text("Search") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = page > 0, onClick = { page--; loadCards() }) { Text("Previous") }
                Text("Page ${page + 1}")
                Button(enabled = cards.size == 20, onClick = { page++; loadCards() }) { Text("Next") }
            }
            selectedCard?.let { card ->
                Text("${card.name} ${card.manaCost.orEmpty()}")
                card.text?.let { Text(it) }
                val accessToken = token
                if (accessToken != null && selectedDeck != null) {
                    Button(onClick = { scope.launch {
                        runCatching { api.addCard(accessToken, requireNotNull(selectedDeck), card.id) }
                            .onSuccess { loadDecks(accessToken); message = "Added ${card.name}" }
                            .onFailure { message = it.message }
                    } }) { Text("Add to selected deck") }
                }
            }
            if (token != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(deckName, { deckName = it }, label = { Text("New deck") }, modifier = Modifier.weight(1f))
                    Button(enabled = deckName.isNotBlank(), onClick = {
                        val accessToken = requireNotNull(token)
                        scope.launch { runCatching { api.createDeck(accessToken, deckName) }
                            .onSuccess { selectedDeck = it.id; deckName = ""; loadDecks(accessToken) }
                            .onFailure { message = it.message } }
                    }) { Text("Create") }
                }
                decks.forEach { deck ->
                    Button(onClick = { selectedDeck = deck.id }) { Text("${if (selectedDeck == deck.id) "✓ " else ""}${deck.name} (${deck.cards.size})") }
                }
                decks.firstOrNull { it.id == selectedDeck }?.cards?.forEach { entry ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Card ${entry.cardId} ×${entry.quantity} ${entry.section}")
                        Button(onClick = {
                            val accessToken = requireNotNull(token)
                            scope.launch { runCatching { api.removeCard(accessToken, requireNotNull(selectedDeck), entry.cardId) }
                                .onSuccess { loadDecks(accessToken) }.onFailure { message = it.message } }
                        }) { Text("Remove") }
                    }
                }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(cards, key = { it.id }) { card ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedCard = card }) {
                        Text(card.name, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}
