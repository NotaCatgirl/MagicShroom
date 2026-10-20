package edu.csumb.magicshroom.ui.account

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csumb.magicshroom.data.auth.UserProfile
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BorderLavender
import edu.csumb.magicshroom.ui.theme.BrandPurple
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import kotlinx.coroutines.launch

/** Stable identifiers used by the account Compose UI test. */
object UserTestTags {
    const val SCREEN = "user_screen"
    const val BACK = "user_back"
    const val SIGN_OUT = "user_sign_out"
    const val ADMIN = "user_admin"
}

/**
 * Displays signed-in account information, sign-out, and admin navigation when the server confirms
 * that the user has the ADMIN role.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    user: UserProfile,
    onBack: () -> Unit,
    onSignOut: suspend () -> Unit,
    modifier: Modifier = Modifier,
    onAdminClick: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier.testTag(UserTestTags.SCREEN),
        containerColor = AppBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Account",
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Medium,
                    )
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(48.dp)
                            .semantics { contentDescription = "Back" }
                            .testTag(UserTestTags.BACK)
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        BackArrowIcon(
                            modifier = Modifier.size(27.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .background(BrandPurple, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initialsFor(user.displayName),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            Text(
                text = user.displayName,
                modifier = Modifier.padding(top = 24.dp),
                fontSize = 29.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "MagicShroom account",
                modifier = Modifier.padding(top = 5.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 34.dp)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .border(1.dp, BorderLavender, RoundedCornerShape(20.dp)),
            ) {
                UserInfoRow(label = "Display name", value = user.displayName)
                HorizontalDivider(color = BorderLavender)
                UserInfoRow(label = "Email", value = user.email)
                HorizontalDivider(color = BorderLavender)
                UserInfoRow(label = "Role", value = user.role.toDisplayRole())
            }

            Button(
                onClick = { scope.launch { onSignOut() } },
                modifier = Modifier.fillMaxWidth().padding(top = 28.dp).testTag(UserTestTags.SIGN_OUT),
            ) {
                Text("Sign out")
            }
            if (onAdminClick != null) {
                Button(
                    onClick = onAdminClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                        .height(52.dp)
                        .testTag(UserTestTags.ADMIN),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Admin tools", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun UserInfoRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
        )
        Text(
            text = value,
            modifier = Modifier.padding(top = 7.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 21.sp,
        )
    }
}

private fun initialsFor(displayName: String): String {
    val words = displayName.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    return words.take(2).mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("")
        .ifEmpty { "?" }
}

private fun String.toDisplayRole(): String = lowercase().replaceFirstChar { it.uppercaseChar() }

@Composable
private fun BackArrowIcon(modifier: Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height / 2f),
            end = Offset(size.width * 0.82f, size.height / 2f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height / 2f),
            end = Offset(size.width * 0.42f, size.height * 0.26f),
            strokeWidth = strokeWidth,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height / 2f),
            end = Offset(size.width * 0.42f, size.height * 0.74f),
            strokeWidth = strokeWidth,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun UserScreenPreview() {
    MagicShroomTheme {
        UserScreen(
            user = UserProfile(
                email = "testEmail1@gmail.com",
                displayName = "testUser1",
                role = "USER",
            ),
            onBack = {},
            onSignOut = {},
        )
    }
}
