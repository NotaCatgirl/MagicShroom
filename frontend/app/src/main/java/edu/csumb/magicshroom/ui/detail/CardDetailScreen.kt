package edu.csumb.magicshroom.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.ui.components.BackTopBar
import edu.csumb.magicshroom.ui.components.FanContentNotice
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BorderLavender
import edu.csumb.magicshroom.ui.theme.BrandPurple

/** Stable identifiers for card detail UI checks. */
object CardDetailTestTags {
    const val SCREEN = "card_detail_screen"
    const val IMAGE = "card_detail_image"
    const val NEXT_FACE = "card_detail_next_face"
    const val COMPARE = "card_detail_compare"
}

/** Loads full details for a selected card from the public API; [onCompareClick] shows "Compare with…". */
@Composable
fun CardDetailScreen(cardId: Long, onBack: () -> Unit, onCompareClick: (() -> Unit)? = null) {
    val app = LocalContext.current.applicationContext as MagicShroomApplication
    val repository = remember(app) { app.container.cardRepository }
    var retry by remember { mutableIntStateOf(0) }
    var result by remember(cardId) { mutableStateOf<ApiResult<CardDto>?>(null) }
    LaunchedEffect(cardId, retry) {
        result = null
        result = repository.getCard(cardId)
    }
    when (val current = result) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ApiResult.Success -> CardDetailContent(current.data, onBack, onCompareClick)
        is ApiResult.Failure -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(current.userMessage)
            Button(onClick = { retry++ }) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

/** Shows the selected printing's full scan and the card's catalog information. */
@Composable
fun CardDetailContent(card: CardDto, onBack: () -> Unit, onCompareClick: (() -> Unit)? = null) {
    var faceIndex by remember(card.id) { mutableIntStateOf(0) }
    val face = card.imageFaces.getOrNull(faceIndex)
    val imageUrl = face?.normalUrl
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.testTag(CardDetailTestTags.SCREEN),
        containerColor = AppBackground,
        topBar = { BackTopBar(title = card.cardName, onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(0.714f)
                    .background(Color.White, RoundedCornerShape(16.dp))
                    .border(1.dp, BorderLavender, RoundedCornerShape(16.dp))
                    .testTag(CardDetailTestTags.IMAGE),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl != null && !imageFailed) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "${face.name} card face",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                        onError = { imageFailed = true },
                    )
                } else {
                    Text(card.cardName, modifier = Modifier.padding(24.dp), style = MaterialTheme.typography.headlineSmall)
                }
            }
            if (card.imageFaces.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { faceIndex = (faceIndex + 1) % card.imageFaces.size },
                        modifier = Modifier.testTag(CardDetailTestTags.NEXT_FACE),
                    ) { Text("Next face") }
                    Text("${faceIndex + 1} of ${card.imageFaces.size}: ${face?.name.orEmpty()}")
                }
            }
            Text(card.cardName, style = MaterialTheme.typography.headlineSmall)
            card.manaCost?.let { Text(it, color = BrandPurple) }
            val typeLine = (card.supertypes + card.types).joinToString(" ") +
                card.subtypes.takeIf { it.isNotEmpty() }?.joinToString(" — ", prefix = " — ").orEmpty()
            Text(typeLine.trim(), style = MaterialTheme.typography.titleMedium)
            card.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            if (onCompareClick != null) {
                OutlinedButton(
                    onClick = onCompareClick,
                    modifier = Modifier.fillMaxWidth().testTag(CardDetailTestTags.COMPARE),
                ) { Text("Compare with…") }
            }
            FanContentNotice()
        }
    }
}
