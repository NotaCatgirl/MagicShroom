package edu.csumb.magicshroom.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.api.Color
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme

/** Stable identifiers used by the search Compose UI test. */
object SearchTestTags {
    const val SCREEN = "search_screen"
    const val ACCOUNT = "search_account"
    const val FIELD = "search_field"
    const val RESULTS = "search_results"
}

/** Card search backed by the live API, plus the account navigation action. */
@Composable
fun SearchScreen(
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    SearchContent(
        query = viewModel.query,
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRetry = viewModel::retry,
        onAccountClick = onAccountClick,
        modifier = modifier,
    )
}

/** Stateless search UI, so it can be previewed and tested without a server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(SearchTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("Search cards") },
                actions = {
                    TextButton(
                        onClick = onAccountClick,
                        modifier = Modifier.testTag(SearchTestTags.ACCOUNT),
                    ) {
                        Text("Account")
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SearchTestTags.FIELD),
                label = { Text("Search cards") },
                placeholder = { Text("Card name") },
                singleLine = true,
            )

            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            val error = state.errorMessage
            when {
                error != null -> SearchError(message = error, onRetry = onRetry)
                !state.isLoading -> SearchResults(query = query, state = state)
            }
        }
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SearchResults(query: String, state: SearchUiState) {
    Text(
        text = if (state.cards.isEmpty()) {
            "No cards match \"${query.trim()}\"."
        } else {
            "Showing ${state.cards.size} of ${state.totalCards} cards"
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.labelLarge,
    )
    // TODO(ui): tapping a card should open the Detail screen (GET /api/v1/cards/{cardId}).
    LazyColumn(modifier = Modifier.testTag(SearchTestTags.RESULTS)) {
        items(state.cards, key = { it.id }) { card ->
            ListItem(
                headlineContent = { Text(card.cardName) },
                supportingContent = { Text((card.supertypes + card.types).joinToString(" ")) },
                trailingContent = card.manaCost?.let { cost -> { Text(cost) } },
            )
            HorizontalDivider()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    val bolt = CardDto(
        id = 1,
        uuid = "0318d56b-647f-4b0f-9306-9adc3312619d",
        cardName = "Lightning Bolt",
        colorIdentity = listOf(Color.R),
        colors = listOf(Color.R),
        manaCost = "{R}",
        types = listOf("Instant"),
    )
    MagicShroomTheme {
        SearchContent(
            query = "bolt",
            state = SearchUiState(cards = listOf(bolt), totalCards = 1, isLoading = false),
            onQueryChange = {},
            onRetry = {},
            onAccountClick = {},
        )
    }
}
