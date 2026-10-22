package edu.csumb.magicshroom.ui.search

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.ui.components.FanContentNotice
import edu.csumb.magicshroom.ui.components.MagicShroomLogo
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BorderLavender
import edu.csumb.magicshroom.ui.theme.BrandPurple
import edu.csumb.magicshroom.ui.theme.LavenderSurface
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme

/** Stable identifiers used by the search Compose UI test. */
object SearchTestTags {
    const val SCREEN = "search_screen"
    const val ACCOUNT = "search_account"
    const val LOGIN = "search_login"
    const val FIELD = "search_field"
    const val RESULTS = "search_results"
    const val LOAD_MORE = "search_load_more"
    const val RETRY = "search_retry"
    const val CARD = "search_card_"
}

/** Searches the public card catalog and displays a gallery of card images. */
@Composable
fun SearchScreen(
    onAccountClick: () -> Unit,
    onCardClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    canAccessBackend: Boolean = true,
    onRequestLocalNetworkPermission: () -> Unit = {},
    isSignedIn: Boolean = false,
    onLoginClick: () -> Unit = {},
) {
    if (canAccessBackend) {
        val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
        val state by viewModel.uiState.collectAsState()
        SearchContent(
            query = viewModel.query,
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onSearchNow = viewModel::retry,
            onRetry = viewModel::retry,
            onLoadMore = viewModel::loadMore,
            onAccountClick = onAccountClick,
            isSignedIn = isSignedIn,
            onLoginClick = onLoginClick,
            onCardClick = onCardClick,
            modifier = modifier,
        )
    } else {
        SearchContent(
            query = "",
            state = SearchUiState(isLoading = false),
            onQueryChange = {},
            onSearchNow = {},
            onRetry = {},
            onLoadMore = {},
            onAccountClick = onAccountClick,
            isSignedIn = isSignedIn,
            onLoginClick = onLoginClick,
            onCardClick = onCardClick,
            modifier = modifier,
            needsLocalNetworkPermission = true,
            onRequestLocalNetworkPermission = onRequestLocalNetworkPermission,
        )
    }
}

/** Stateless search content, kept separate from the network request for previews and UI tests. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearchNow: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onAccountClick: () -> Unit,
    onCardClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    needsLocalNetworkPermission: Boolean = false,
    onRequestLocalNetworkPermission: () -> Unit = {},
    isSignedIn: Boolean = false,
    onLoginClick: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.testTag(SearchTestTags.SCREEN),
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground),
                actions = {
                    if (isSignedIn) {
                        Box(
                            modifier = Modifier
                                .padding(end = 18.dp)
                                .size(48.dp)
                                .semantics { contentDescription = "Account" }
                                .testTag(SearchTestTags.ACCOUNT)
                                .clickable(role = Role.Button, onClick = onAccountClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            AccountOutlineIcon(
                                modifier = Modifier.size(27.dp),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        TextButton(
                            onClick = onLoginClick,
                            modifier = Modifier.padding(end = 16.dp).testTag(SearchTestTags.LOGIN),
                        ) {
                            LoginOutlineIcon(modifier = Modifier.size(24.dp), color = BrandPurple)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Log in")
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp),
        ) {
            MagicShroomLogo(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(28.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .testTag(SearchTestTags.FIELD),
                enabled = !needsLocalNetworkPermission,
                singleLine = true,
                shape = RoundedCornerShape(32.dp),
                placeholder = { Text("Search cards") },
                leadingIcon = {
                    SearchOutlineIcon(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Text("×", modifier = Modifier.semantics { contentDescription = "Clear search" }, fontSize = 24.sp)
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchNow() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = LavenderSurface,
                    unfocusedContainerColor = LavenderSurface,
                    disabledContainerColor = LavenderSurface,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                ),
            )

            LazyVerticalGrid(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SearchTestTags.RESULTS),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(top = 26.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when {
                    needsLocalNetworkPermission -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(
                                "Allow local network access to search cards on the emulator. " +
                                    "If you denied it before, enable it in MagicShroom's app permissions.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(onClick = onRequestLocalNetworkPermission) {
                                Text("Allow local connection")
                            }
                        }
                    }
                    state.isLoading -> items(2) { SearchResultPlaceholder() }
                    state.errorMessage != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchError(message = state.errorMessage, onRetry = onRetry)
                    }
                    state.cards.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = if (query.isBlank()) "No cards are available yet."
                            else "No cards match \"${query.trim()}\".",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Showing ${state.cards.size} of ${state.totalCards} cards",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        items(state.cards, key = { it.id }) { card ->
                            SearchCardTile(card, onClick = { onCardClick(card.id) })
                        }
                        if (state.isLoadingMore) item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = BrandPurple)
                            }
                        }
                        if (state.loadMoreErrorMessage != null) item(span = { GridItemSpan(maxLineSpan) }) {
                            SearchError(message = state.loadMoreErrorMessage, onRetry = onLoadMore)
                        }
                        if (state.hasMore && !state.isLoadingMore && state.loadMoreErrorMessage == null) item(span = { GridItemSpan(maxLineSpan) }) {
                            OutlinedButton(
                                onClick = onLoadMore,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(SearchTestTags.LOAD_MORE),
                            ) {
                                Text("Load more")
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FanContentNotice()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchError(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onRetry, modifier = Modifier.testTag(SearchTestTags.RETRY)) {
            Text("Retry")
        }
    }
}

/** Renders one card scan with a text fallback when the image is missing or unavailable. Shared by card lists. */
@Composable
internal fun SearchCardTile(card: CardDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(17.dp))
            .border(1.dp, BorderLavender, RoundedCornerShape(17.dp))
            .testTag(SearchTestTags.CARD + card.id)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val imageUrl = card.imageFaces.firstOrNull()?.smallUrl
        var imageFailed by remember(imageUrl) { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.714f)
                .background(
                    Brush.verticalGradient(listOf(BrandPurple, BrandPurple.copy(alpha = 0.45f))),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null && !imageFailed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "${card.cardName} card",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                    onError = {
                        android.util.Log.e(
                            "MagicShroomImage",
                            "Failed to load $imageUrl",
                            it.result.throwable,
                        )
                        imageFailed = true
                    },
                )
            } else {
                Text(card.cardName, color = Color.White, modifier = Modifier.padding(10.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                text = card.cardName,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            card.manaCost?.takeIf(String::isNotBlank)?.let { manaCost ->
                Text("Mana cost", style = MaterialTheme.typography.labelSmall)
                Text(manaCost, color = BrandPurple, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun SearchResultPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .background(Color.White, RoundedCornerShape(17.dp))
            .border(1.dp, BorderLavender, RoundedCornerShape(17.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 72.dp)
                .background(LavenderSurface, RoundedCornerShape(10.dp)),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(LavenderSurface, RoundedCornerShape(8.dp)),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(12.dp)
                    .background(LavenderSurface, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
private fun SearchOutlineIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2.dp.toPx())
        drawCircle(
            color = color,
            radius = size.minDimension * 0.32f,
            center = Offset(size.width * 0.43f, size.height * 0.43f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.66f, size.height * 0.66f),
            end = Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = 2.dp.toPx(),
        )
    }
}

@Composable
private fun LoginOutlineIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        // Door frame and an arrow entering from the left.
        drawLine(color, Offset(size.width * 0.55f, size.height * 0.15f), Offset(size.width * 0.85f, size.height * 0.15f), strokeWidth)
        drawLine(color, Offset(size.width * 0.85f, size.height * 0.15f), Offset(size.width * 0.85f, size.height * 0.85f), strokeWidth)
        drawLine(color, Offset(size.width * 0.55f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.85f), strokeWidth)
        drawLine(color, Offset(size.width * 0.1f, size.height * 0.5f), Offset(size.width * 0.62f, size.height * 0.5f), strokeWidth)
        drawLine(color, Offset(size.width * 0.4f, size.height * 0.28f), Offset(size.width * 0.62f, size.height * 0.5f), strokeWidth)
        drawLine(color, Offset(size.width * 0.4f, size.height * 0.72f), Offset(size.width * 0.62f, size.height * 0.5f), strokeWidth)
    }
}

@Composable
private fun AccountOutlineIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        drawCircle(
            color = color,
            radius = size.minDimension * 0.42f,
            style = Stroke(strokeWidth),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.13f,
            center = Offset(size.width / 2f, size.height * 0.38f),
            style = Stroke(strokeWidth),
        )
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.27f, size.height * 0.48f),
            size = Size(size.width * 0.46f, size.height * 0.34f),
            style = Stroke(strokeWidth),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    MagicShroomTheme {
        SearchContent(
            query = "bolt",
            state = SearchUiState(
                cards = listOf(
                    CardDto(
                        id = 1,
                        uuid = "0318d56b-647f-4b0f-9306-9adc3312619d",
                        cardName = "Lightning Bolt",
                        manaCost = "{R}",
                        types = listOf("Instant"),
                    ),
                ),
                totalCards = 1,
                isLoading = false,
            ),
            onQueryChange = {},
            onSearchNow = {},
            onRetry = {},
            onLoadMore = {},
            onAccountClick = {},
            onCardClick = {},
        )
    }
}
