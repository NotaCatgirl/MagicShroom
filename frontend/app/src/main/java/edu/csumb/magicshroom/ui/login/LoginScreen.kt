package edu.csumb.magicshroom.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import edu.csumb.magicshroom.data.auth.DemoAuthRepository
import edu.csumb.magicshroom.ui.theme.AppBackground
import edu.csumb.magicshroom.ui.theme.BrandPurple
import edu.csumb.magicshroom.ui.theme.LavenderSurface
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme
import kotlinx.coroutines.launch

/** Stable identifiers used by the login Compose UI test. */
object LoginTestTags {
    const val EMAIL = "login_email"
    const val PASSWORD = "login_password"
    const val SUBMIT = "login_submit"
    const val ERROR = "login_error"
}

/** Displays the email/password login design for the temporary demo account. */
@Composable
fun LoginScreen(
    onLogin: suspend (email: String, password: String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf(DemoAuthRepository.TEST_EMAIL) }
    var password by rememberSaveable { mutableStateOf(DemoAuthRepository.TEST_PASSWORD) }
    var showError by rememberSaveable { mutableStateOf(false) }
    var isLoading by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier.size(74.dp),
                color = BrandPurple,
                shape = RoundedCornerShape(22.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "♠",
                        color = Color.White,
                        fontSize = 48.sp,
                        lineHeight = 48.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = "MagicShroom",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 35.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Sign in to build and manage your decks",
                modifier = Modifier.padding(top = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
            )

            Spacer(modifier = Modifier.height(34.dp))

            LoginFieldLabel("Email")
            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    showError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginTestTags.EMAIL),
                singleLine = true,
                isError = showError,
                shape = RoundedCornerShape(15.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                colors = loginFieldColors(),
            )

            Spacer(modifier = Modifier.height(18.dp))

            LoginFieldLabel("Password")
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    showError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginTestTags.PASSWORD),
                singleLine = true,
                isError = showError,
                visualTransformation = PasswordVisualTransformation(),
                shape = RoundedCornerShape(15.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                colors = loginFieldColors(),
            )

            if (showError) {
                Text(
                    text = "Email or password is incorrect.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .testTag(LoginTestTags.ERROR),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(if (showError) 20.dp else 30.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        showError = !onLogin(email, password)
                        isLoading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .testTag(LoginTestTags.SUBMIT),
                shape = RoundedCornerShape(30.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandPurple),
                enabled = !isLoading,
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = "Sign in", fontSize = 17.sp)
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LavenderSurface,
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Demo account",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                    )
                    Text(
                        text = DemoAuthRepository.TEST_EMAIL,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = DemoAuthRepository.TEST_PASSWORD,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginFieldLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 17.sp,
    )
}

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    errorContainerColor = Color.White,
    focusedBorderColor = BrandPurple,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MagicShroomTheme {
        LoginScreen(onLogin = { _, _ -> false })
    }
}
