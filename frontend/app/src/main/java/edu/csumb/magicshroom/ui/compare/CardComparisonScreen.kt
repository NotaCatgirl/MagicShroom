package edu.csumb.magicshroom.ui.compare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.MagicShroomApplication
import edu.csumb.magicshroom.data.api.CardDto
import edu.csumb.magicshroom.data.repository.ApiResult
import edu.csumb.magicshroom.ui.common.BackTopBar
import edu.csumb.magicshroom.ui.common.FanContentNotice
import edu.csumb.magicshroom.ui.search.SearchCardTile
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BrandPurple

/** Loads both cards (normally from the cache the similar list filled) and compares them. */
@Composable
fun CardComparisonScreen(baseId: Long, otherId: Long, onBack: () -> Unit, onCardClick: (Long) -> Unit = {}) {
    val app = LocalContext.current.applicationContext as MagicShroomApplication
    val repository = remember(app) { app.container.cardRepository }
    var retry by remember { mutableIntStateOf(0) }
    var result by remember(baseId, otherId) { mutableStateOf<ApiResult<List<CardDto>>?>(null) }
    LaunchedEffect(baseId, otherId, retry) {
        result = null
        result = repository.getCardsByIds(listOf(baseId, otherId))
    }
    when (val current = result) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is ApiResult.Success -> {
            val base = current.data.first { it.id == baseId }
            val other = current.data.firstOrNull { it.id == otherId } ?: base
            CardComparisonContent(base, other, onBack, onCardClick)
        }
        is ApiResult.Failure -> Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(current.userMessage)
            Button(onClick = { retry++ }) { Text("Retry") }
            Button(onClick = onBack) { Text("Back") }
        }
    }
}

/** Both cards, the tally, one row per stat, and the traits each card shares or has alone. */
@Composable
fun CardComparisonContent(base: CardDto, other: CardDto, onBack: () -> Unit, onCardClick: (Long) -> Unit = {}) {
    val comparison = remember(base, other) { compareCards(base, other) }
    Scaffold(
        modifier = Modifier.testTag(CompareTestTags.COMPARISON_SCREEN),
        containerColor = AppBackground,
        topBar = { BackTopBar(title = "Comparison", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SearchCardTile(base, onClick = { onCardClick(base.id) }) }
                Text("vs", style = MaterialTheme.typography.titleMedium)
                Box(Modifier.weight(1f)) { SearchCardTile(other, onClick = { onCardClick(other.id) }) }
            }
            Tally(comparison)
            StatTable(base.cardName, other.cardName, comparison.rows)
            if (comparison.shared.isNotEmpty()) TraitList("Shared", comparison.shared, VerdictStyle.EQUAL)
            TraitList("Unique to ${base.cardName}", comparison.uniqueToBase, VerdictStyle.UNIQUE)
            TraitList("Unique to ${other.cardName}", comparison.uniqueToOther, VerdictStyle.UNIQUE)
            FanContentNotice()
        }
    }
}

/** Icon, word, and color for each verdict, so color is never the only signal (WCAG 1.4.1). */
private enum class VerdictStyle(val symbol: String, val word: String, val color: Color, val badgeWord: String = word) {
    BETTER("▲", "Better", Color(0xFF1B5E20)),
    WORSE("▼", "Worse", Color(0xFFB71C1C)),
    EQUAL("=", "Equal", Color(0xFF616161)),
    UNIQUE("★", "Unique", BrandPurple),
    NOT_COMPARABLE("—", "Not comparable", Color(0xFF616161), badgeWord = "N/A"),
}

/** The style that shows this verdict. */
private fun Verdict.style(): VerdictStyle = when (this) {
    Verdict.BETTER -> VerdictStyle.BETTER
    Verdict.WORSE -> VerdictStyle.WORSE
    Verdict.EQUAL -> VerdictStyle.EQUAL
    Verdict.NOT_COMPARABLE -> VerdictStyle.NOT_COMPARABLE
}

/** "▲ 2 better · ▼ 2 worse · = 0 equal · ★ 7 unique", each part in its verdict color. */
@Composable
private fun Tally(comparison: CardComparison) {
    val parts = listOf(
        VerdictStyle.BETTER to comparison.betterCount,
        VerdictStyle.WORSE to comparison.worseCount,
        VerdictStyle.EQUAL to comparison.equalCount,
        VerdictStyle.UNIQUE to comparison.uniqueCount,
    )
    Text(
        text = buildAnnotatedString {
            parts.forEachIndexed { index, (style, count) ->
                if (index > 0) append("  ·  ")
                withStyle(SpanStyle(color = style.color, fontWeight = FontWeight.SemiBold)) {
                    append("${style.symbol} $count ${style.word.lowercase()}")
                }
            }
        },
        modifier = Modifier.testTag(CompareTestTags.TALLY).clearAndSetSemantics {
            contentDescription = parts.joinToString { (style, count) -> "$count ${style.word.lowercase()}" }
        },
        style = MaterialTheme.typography.titleSmall,
    )
}

/** Header plus one row per stat: label, base value, verdict badge, other value. */
@Composable
private fun StatTable(baseName: String, otherName: String, rows: List<StatRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Text("Stat", Modifier.weight(1.1f), fontWeight = FontWeight.SemiBold)
            Text(
                baseName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            Text("vs", Modifier.weight(1.3f), textAlign = TextAlign.Center)
            Text(
                otherName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider()
        rows.forEach { row ->
            val style = row.verdict.style()
            Row(
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {
                    contentDescription = "${row.label}: $baseName ${spoken(row.baseValue)}, $otherName ${spoken(row.otherValue)}, ${style.word.lowercase()}"
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(row.label, Modifier.weight(1.1f), style = MaterialTheme.typography.bodyMedium)
                Text(row.baseValue, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                VerdictBadge(style, Modifier.weight(1.3f))
                Text(row.otherValue, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** A tinted pill with the verdict's symbol and word. */
@Composable
private fun VerdictBadge(style: VerdictStyle, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "${style.symbol} ${style.badgeWord}",
            modifier = Modifier.background(style.color.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            color = style.color,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

/** How a table value is read aloud: "—" as none, "#65" as rank 65. */
private fun spoken(value: String): String = when {
    value == "—" -> "none"
    value.startsWith("#") -> "rank ${value.drop(1)}"
    else -> value
}

/** A titled line of traits, each marked with [style]'s symbol; "None" when empty. */
@Composable
private fun TraitList(title: String, traits: List<String>, style: VerdictStyle) {
    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = if (traits.isEmpty()) "$title: none" else "$title: ${traits.joinToString()}"
        },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(
            text = if (traits.isEmpty()) "None" else traits.joinToString("  ") { "${style.symbol} $it" },
            color = if (traits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else style.color,
        )
    }
}
