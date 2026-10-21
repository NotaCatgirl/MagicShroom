package edu.csumb.magicshroom.ui.deck

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.DeckSection
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.data.repository.ResolvedDeck
import edu.csumb.magicshroom.data.repository.ResolvedDeckCard
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BrandPurple
import kotlinx.coroutines.delay

object DeckTestTags {
    const val LIST = "deck_list_screen"
    const val CREATE = "deck_create"
    const val DETAIL = "deck_detail_screen"
    const val SEARCH_CARDS = "deck_search_cards"
    const val CARD_GRID = "deck_card_grid"
    const val CARD = "deck_card_"
}

/** Displays the signed-in user's decks and provides create and delete actions. */
@Composable
fun DeckListScreen(onBack: () -> Unit, onDeckClick: (Long) -> Unit) {
    val vm: DeckListViewModel = viewModel(factory = DeckListViewModel.Factory)
    val state by vm.uiState.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ResolvedDeck?>(null) }
    DeckScaffold(title = "My decks", onBack = onBack, modifier = Modifier.testTag(DeckTestTags.LIST)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Button(
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth().testTag(DeckTestTags.CREATE),
                enabled = !state.isWorking,
            ) {
                Text("Create deck")
            }
            Spacer(Modifier.height(12.dp))
            if (state.isLoading && state.decks.isEmpty()) {
                CenterProgress()
            } else if (state.errorMessage != null && state.decks.isEmpty()) {
                ErrorWithRetry(state.errorMessage.orEmpty(), vm::refresh)
            } else if (state.decks.isEmpty()) {
                Text("No decks yet. Create one to get started.", modifier = Modifier.padding(12.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.decks, key = ResolvedDeck::id) { deck ->
                        DeckListItem(deck, onClick = { onDeckClick(deck.id) }, onDelete = { deleteTarget = deck })
                    }
                }
            }
            state.errorMessage?.takeIf { state.decks.isNotEmpty() }?.let { ErrorText(it) }
        }
    }
    if (creating) {
        NameDialog(title = "Create deck", initial = "", confirmText = "Create", onDismiss = { creating = false }) { name ->
            vm.create(name)
            creating = false
        }
    }
    deleteTarget?.let { deck ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${deck.name}?") },
            text = { Text("This permanently deletes the deck and its card list.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(deck.id); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

/** Renders a deck row with its name, card count, and delete action. */
@Composable
private fun DeckListItem(deck: ResolvedDeck, onClick: () -> Unit, onDelete: () -> Unit) {
    androidx.compose.material3.Card(shape = RoundedCornerShape(14.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(deck.name, style = MaterialTheme.typography.titleMedium)
                Text("${deck.cards.size} card entries", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

/** Displays a deck, resolves its catalog cards, and exposes deck-card operations. */
@Composable
fun DeckDetailScreen(deckId: Long, onBack: () -> Unit, onCardClick: (Long) -> Unit) {
    val vm: DeckDetailViewModel = viewModel(factory = DeckDetailViewModel.factory(deckId))
    val state by vm.uiState.collectAsState()
    val app = LocalContext.current.applicationContext as MagicShroomApplication
    val cardRepository = remember(app) { app.container.cardRepository }
    var renaming by remember { mutableStateOf(false) }
    var catalogQuery by remember { mutableStateOf("") }
    var matchingCards by remember { mutableStateOf<List<CardDto>>(emptyList()) }
    var isSearchingCards by remember { mutableStateOf(false) }
    var cardSearchError by remember { mutableStateOf<String?>(null) }
    var selectedSection by remember { mutableStateOf(DeckSection.MAINBOARD) }
    var sectionMenu by remember { mutableStateOf(false) }

    LaunchedEffect(catalogQuery) {
        val query = catalogQuery.trim()
        if (query.length < 2) {
            matchingCards = emptyList()
            cardSearchError = null
            isSearchingCards = false
        } else {
            delay(300)
            isSearchingCards = true
            when (val result = cardRepository.listCards(name = query, size = 8)) {
                is ApiResult.Success -> {
                    matchingCards = result.data.content
                    cardSearchError = null
                }
                is ApiResult.Failure -> {
                    matchingCards = emptyList()
                    cardSearchError = result.userMessage
                }
            }
            isSearchingCards = false
        }
    }

    DeckScaffold(
        title = state.deck?.name ?: "Deck",
        onBack = onBack,
        modifier = Modifier.testTag(DeckTestTags.DETAIL),
        actions = { TextButton(onClick = { renaming = true }, enabled = state.deck != null) { Text("Rename") } },
    ) {
        when {
            state.isLoading && state.deck == null -> CenterProgress()
            state.deck == null -> ErrorWithRetry(state.errorMessage ?: "Deck unavailable", vm::refresh)
            else -> {
                val deck = requireNotNull(state.deck)
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Text("Search the catalog to add a card", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = catalogQuery,
                            onValueChange = { catalogQuery = it },
                            modifier = Modifier.weight(1f).testTag(DeckTestTags.SEARCH_CARDS),
                            label = { Text("Card name") },
                            singleLine = true,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BoxButton("Section: ${selectedSection.name}", onClick = { sectionMenu = true })
                        DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                            DeckSection.entries.forEach { section ->
                                DropdownMenuItem(text = { Text(section.name) }, onClick = {
                                    selectedSection = section
                                    sectionMenu = false
                                })
                            }
                        }
                    }
                    if (isSearchingCards) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    cardSearchError?.let { ErrorText(it) }
                    matchingCards.forEach { card ->
                        val isAlreadyInDeck = deck.cards.any { it.card.id == card.id }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(card.cardName, modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    vm.addCard(card.id, selectedSection)
                                    catalogQuery = ""
                                },
                                enabled = !state.isWorking && !isAlreadyInDeck,
                            ) { Text(if (isAlreadyInDeck) "Added" else "Add") }
                        }
                    }
                    if (catalogQuery.trim().length >= 2 && !isSearchingCards && matchingCards.isEmpty() && cardSearchError == null) {
                        Text("No matching cards found.", style = MaterialTheme.typography.bodySmall)
                    }
                    state.errorMessage?.let { ErrorText(it) }
                    Spacer(Modifier.height(8.dp))
                    if (deck.cards.isEmpty()) {
                        Text("This deck is empty.", modifier = Modifier.padding(vertical = 12.dp))
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.weight(1f).fillMaxWidth().testTag(DeckTestTags.CARD_GRID),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(deck.cards, key = { it.card.id }) { entry ->
                                DeckCardTile(
                                    entry = entry,
                                    enabled = !state.isWorking,
                                    onClick = { onCardClick(entry.card.id) },
                                    onRemove = { vm.removeCard(entry.card.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (renaming && state.deck != null) {
        NameDialog(
            title = "Rename deck",
            initial = state.deck!!.name,
            confirmText = "Save",
            onDismiss = { renaming = false },
        ) { name ->
            vm.rename(name)
            renaming = false
        }
    }
}

/** Displays one deck card's image, name, section, and remove action. */
@Composable
private fun DeckCardTile(entry: ResolvedDeckCard, enabled: Boolean, onClick: () -> Unit, onRemove: () -> Unit) {
    val card = entry.card
    val imageUrl = card.imageFaces.firstOrNull()?.smallUrl
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().testTag(DeckTestTags.CARD + card.id),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.714f)
                    .background(Brush.verticalGradient(listOf(BrandPurple, BrandPurple.copy(alpha = 0.45f))))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null && !imageFailed) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "${card.cardName} card",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onError = { imageFailed = true },
                    )
                } else {
                    Text(card.cardName, color = Color.White, modifier = Modifier.padding(8.dp))
                }
            }
            Text(
                text = card.cardName,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
            )
            Text(entry.section.name, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRemove, enabled = enabled) { Text("Remove") }
        }
    }
}

/** Provides a consistent app bar and scaffold for deck destinations. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeckScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineMedium) } },
                actions = actions,
            )
        },
    ) { padding -> Column(Modifier.fillMaxSize().padding(padding)) { content() } }
}

/** Centers a progress indicator while a deck request is in flight. */
@Composable
private fun CenterProgress() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
    }
}

/** Shows an API error with an action to retry loading. */
@Composable
private fun ErrorWithRetry(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ErrorText(message)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

/** Renders an error message using the theme's error color. */
@Composable
private fun ErrorText(message: String) { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }

/** Renders a compact outlined action button. */
@Composable
private fun BoxButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text(label) }
}

/** Prompts for a deck name and returns the trimmed value on confirmation. */
@Composable
private fun NameDialog(title: String, initial: String, confirmText: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Deck name") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
