package edu.csumb.magicshroom.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.csumb.magicshroom.data.api.Role
import edu.csumb.magicshroom.data.api.UserDto
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BorderLavender
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme

/** Stable identifiers used by Compose UI tests. */
object AdminTestTags {
    const val SCREEN = "admin_screen"
    fun roleSwitch(userId: Long) = "admin_role_switch_$userId"
    fun delete(userId: Long) = "admin_delete_$userId"
}

/** Admin-only user table: grant or revoke ADMIN, and delete users. */
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = viewModel(factory = AdminViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsState()
    AdminContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onSetAdmin = viewModel::setAdmin,
        onDelete = viewModel::delete,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

/** Stateless admin UI, so it can be previewed and tested without a server. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminContent(
    state: AdminUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onSetAdmin: (UserDto, Boolean) -> Unit,
    onDelete: (UserDto) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<UserDto?>(null) }

    Scaffold(
        modifier = modifier.testTag(AdminTestTags.SCREEN),
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = { Text("Admin", fontWeight = FontWeight.Medium) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { contentPadding ->
        val error = state.errorMessage
        when {
            error != null && state.users.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Retry") }
            }

            state.isLoading && state.users.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.actionMessage?.let { message ->
                    item { ActionMessage(message = message, onDismiss = onDismissMessage) }
                }
                item {
                    Text(
                        text = "${state.totalUsers} users",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(state.users, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        isSelf = user.id == state.currentUserId,
                        isBusy = user.id in state.busyUserIds,
                        onSetAdmin = { admin -> onSetAdmin(user, admin) },
                        onDelete = { pendingDelete = user },
                    )
                }
                if (state.hasMore) {
                    item {
                        OutlinedButton(onClick = onLoadMore, enabled = !state.isLoading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (state.isLoading) "Loading…" else "Load more")
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${user.displayName}?") },
            text = { Text("This deletes their account and all of their decks. It can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDelete(user); pendingDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun UserRow(
    user: UserDto,
    isSelf: Boolean,
    isBusy: Boolean,
    onSetAdmin: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(1.dp, BorderLavender, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isSelf) "${user.displayName} (you)" else user.displayName,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text("Admin", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
                Switch(
                    checked = user.role == Role.ADMIN,
                    onCheckedChange = onSetAdmin,
                    enabled = !isSelf,
                    modifier = Modifier.testTag(AdminTestTags.roleSwitch(user.id)),
                )
            }
        }
        if (!isSelf) {
            TextButton(
                onClick = onDelete,
                enabled = !isBusy,
                modifier = Modifier.align(Alignment.End).testTag(AdminTestTags.delete(user.id)),
            ) { Text("Delete user", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ActionMessage(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("Dismiss") }
    }
}

@Preview(showBackground = true)
@Composable
private fun AdminContentPreview() {
    MagicShroomTheme {
        AdminContent(
            state = AdminUiState(
                users = listOf(
                    UserDto(1, "bcalvario@csumb.edu", "Brandon Calvario", Role.ADMIN),
                    UserDto(2, "player.one@example.com", "Player One", Role.USER),
                ),
                totalUsers = 2,
                isLoading = false,
                currentUserId = 1,
            ),
            onBack = {}, onRetry = {}, onLoadMore = {}, onSetAdmin = { _, _ -> }, onDelete = {}, onDismissMessage = {},
        )
    }
}
