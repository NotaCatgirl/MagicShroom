package edu.csumb.magicshroom.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import edu.csumb.magicshroom.data.auth.FakeAuthRepository
import edu.csumb.magicshroom.ui.theme.MagicShroomTheme

/** Stable identifiers used by the login Compose UI test. */
object LoginTestTags {
    const val EMAIL = "login_email"
    const val PASSWORD = "login_password"
    const val SUBMIT = "login_submit"
}

/**
 * Displays the temporary email/password login form.
 *
 * [onLogin] returns true when the supplied mock credentials are accepted.
 */
@Composable
fun LoginScreen(
    onLogin: (email: String, password: String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = modifier) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "MagicShroom",
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = "Sign in to manage your decks",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    showError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginTestTags.EMAIL),
                label = { Text("Email") },
                singleLine = true,
                isError = showError,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    showError = false
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginTestTags.PASSWORD),
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                isError = showError,
            )

            if (showError) {
                Text(
                    text = "Email or password is incorrect.",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { showError = !onLogin(email, password) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(LoginTestTags.SUBMIT),
            ) {
                Text("Sign in")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Mock account",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = FakeAuthRepository.TEST_EMAIL + "\n" + FakeAuthRepository.TEST_PASSWORD,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    MagicShroomTheme {
        LoginScreen(onLogin = { _, _ -> false })
    }
}
