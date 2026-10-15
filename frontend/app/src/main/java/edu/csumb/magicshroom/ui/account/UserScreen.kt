package edu.csumb.magicshroom.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.data.auth.MockUser
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme

/** Stable identifiers used by the account Compose UI test. */
object UserTestTags {
    const val SCREEN = "user_screen"
}

/** Displays basic read-only information for the signed-in mock user. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    user: MockUser,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.testTag(UserTestTags.SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
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
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            UserInfoRow(label = "Display name", value = user.displayName)
            HorizontalDivider()
            UserInfoRow(label = "Email", value = user.email)
            HorizontalDivider()
            UserInfoRow(label = "Role", value = user.role)
        }
    }
}

@Composable
private fun UserInfoRow(
    label: String,
    value: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserScreenPreview() {
    MagicShroomTheme {
        UserScreen(
            user = MockUser(
                email = "testEmail1@gmail.com",
                displayName = "testUser1",
                role = "USER",
            ),
            onBack = {},
        )
    }
}
