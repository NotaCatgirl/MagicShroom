package edu.csumb.magicshroom.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.ui.common.BackTopBar
import edu.csumb.magicshroom.ui.search.SearchCardTile
import edu.csumb.magicshroom.ui.theme.AppBackground

/** Stable identifiers for comparison UI checks. */
object CompareTestTags {
    const val SIMILAR_SCREEN = "similar_cards_screen"
    const val COMPARISON_SCREEN = "card_comparison_screen"
    const val TALLY = "card_comparison_tally"
}

/** Lists the cards most like [cardId] so the player can pick one to compare with it. */
@Composable
fun SimilarCardsScreen(cardId: Long, onBack: () -> Unit, onCardClick: (Long) -> Unit) {
    val app = LocalContext.current.applicationContext as MagicShroomApplication
    val repository = remember(app) { app.container.cardRepository }
    var retry by remember { mutableIntStateOf(0) }
    var baseName by remember(cardId) { mutableStateOf("") }
    var result by remember(cardId) { mutableStateOf<ApiResult<List<CardDto>>?>(null) }
    LaunchedEffect(cardId, retry) {
        result = null
        (repository.getCard(cardId) as? ApiResult.Success)?.let { baseName = it.data.cardName }
        result = repository.getSimilarCards(cardId)
    }
    Scaffold(
        modifier = Modifier.testTag(CompareTestTags.SIMILAR_SCREEN),
        containerColor = AppBackground,
        topBar = { BackTopBar(title = if (baseName.isEmpty()) "Compare with…" else "Compare $baseName with…", onBack = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp)) {
            when (val current = result) {
                null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ApiResult.Failure -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(similarCardsErrorMessage(current))
                    Button(onClick = { retry++ }) { Text("Retry") }
                }
                is ApiResult.Success -> if (current.data.isEmpty()) {
                    Text(
                        "No similar cards found.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(current.data, key = { it.id }) { card ->
                            SearchCardTile(card, onClick = { onCardClick(card.id) })
                        }
                    }
                }
            }
        }
    }
}

/** Text for a failed similar-cards load; a 404 means the server predates the similar route. */
internal fun similarCardsErrorMessage(failure: ApiResult.Failure): String =
    if (failure is ApiResult.HttpError && failure.status == 404) "Comparisons need the latest server version."
    else failure.userMessage
