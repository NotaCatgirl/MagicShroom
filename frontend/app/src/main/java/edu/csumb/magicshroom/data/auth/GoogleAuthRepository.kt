package edu.csumb.magicshroom.data.auth

import edu.csumb.magicshroom.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Uses the same Supabase Google OAuth flow as the earlier app, with session-driven UI. */
class GoogleAuthRepository(private val client: SupabaseClient) : AuthRepository {
    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage = _errorMessage.asStateFlow()

    override val authState = client.auth.sessionStatus.map { status ->
        when (status) {
            SessionStatus.Initializing -> AuthState.Loading
            is SessionStatus.Authenticated -> {
                val user = status.session.user ?: try {
                    client.auth.retrieveUserForCurrentSession()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (user == null) AuthState.SignedOut else AuthState.SignedIn(user.toProfile())
            }
            else -> AuthState.SignedOut
        }
    }

    override suspend fun signInWithGoogle() {
        clearError()
        if (BuildConfig.SUPABASE_URL == "https://example.supabase.co" ||
            BuildConfig.SUPABASE_PUBLISHABLE_KEY == "sb_publishable_replace_me"
        ) {
            _errorMessage.value = "Configure the Supabase URL and publishable key to use Google sign-in."
            return
        }
        try {
            // This opens Google's sign-in page; authState changes only after the callback succeeds.
            client.auth.signInWith(Google)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = "Google sign-in couldn't start. Please try again."
        }
    }

    override suspend fun signOut() {
        clearError()
        try {
            client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _errorMessage.value = "Couldn't sign out. Please try again."
        }
    }

    fun onCallbackError() {
        _errorMessage.value = "Google sign-in didn't finish. Please try again."
    }

    override fun clearError() {
        _errorMessage.value = null
    }
}

/** Google profile data is display-only; application roles must come from the backend. */
internal fun UserInfo.toProfile(): UserProfile {
    val userEmail = email.orEmpty()
    val name = listOf("full_name", "name")
        .firstNotNullOfOrNull { key ->
            (userMetadata?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank)
        }
        ?: userEmail.substringBefore('@').ifBlank { "Google user" }
    return UserProfile(email = userEmail, displayName = name)
}
