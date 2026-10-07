package edu.csumb.magicshroom.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme

/** Stable identifiers used by the search Compose UI test. */
object SearchTestTags {
    const val SCREEN = "search_screen"
    const val ACCOUNT = "search_account"
    const val FIELD = "search_field"
}

/** Displays the non-functional card search layout and account navigation action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
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
                value = "",
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SearchTestTags.FIELD),
                enabled = false,
                label = { Text("Search cards") },
                placeholder = { Text("Card name") },
                singleLine = true,
            )
            Text(
                text = "Search functionality will be added later.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SearchScreenPreview() {
    MagicShroomTheme {
        SearchScreen(onAccountClick = {})
    }
}
