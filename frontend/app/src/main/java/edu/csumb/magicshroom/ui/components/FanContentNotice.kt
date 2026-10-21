package edu.csumb.magicshroom.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** The unofficial-fan-content notice shown on every screen with card scans. */
@Composable
fun FanContentNotice(modifier: Modifier = Modifier) {
    Text(
        "MagicShroom is unofficial fan content. Card images and related materials are © Wizards of the Coast LLC; this app is not endorsed by Wizards.",
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
