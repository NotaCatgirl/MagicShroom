package edu.csumb.magicshroom.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csumb.magicshroom.ui.components.MagicShroomLogo
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BrandPurple
import edu.csumb.magicshroom.ui.theme.LavenderSurface
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import kotlinx.coroutines.launch

object LoginTestTags {
    const val SUBMIT = "login_submit"
    const val ERROR = "login_error"
}

/** The only sign-in option is Google through Supabase Auth. */
@Composable
fun LoginScreen(
    onGoogleLogin: suspend () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
    onBackToSearch: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var isLaunching by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize().background(AppBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            MagicShroomLogo()
            Text(
                "Sign in to build and manage your decks",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
            )

            Spacer(modifier = Modifier.height(42.dp))
            Button(
                onClick = {
                    if (!isLaunching) scope.launch {
                        isLaunching = true
                        try {
                            onGoogleLogin()
                        } finally {
                            isLaunching = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(58.dp).testTag(LoginTestTags.SUBMIT),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                enabled = !isLaunching,
            ) {
                if (isLaunching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Continue with Google", fontSize = 17.sp)
                }
            }

            if (errorMessage != null) {
                Text(
                    errorMessage,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag(LoginTestTags.ERROR),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(34.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LavenderSurface,
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    "Google is the only sign-in option for now. Your account opens after Google confirms your sign-in.",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                )
            }
            if (onBackToSearch != null) {
                TextButton(onClick = onBackToSearch, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Back to search")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MagicShroomTheme { LoginScreen(onGoogleLogin = {}) }
}
