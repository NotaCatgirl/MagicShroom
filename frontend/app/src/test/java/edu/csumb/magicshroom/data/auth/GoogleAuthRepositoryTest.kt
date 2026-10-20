package edu.csumb.magicshroom.data.auth

import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlin.time.ExperimentalTime::class)
class GoogleAuthRepositoryTest {
    @Test
    fun profileUsesGoogleDisplayNameAndEmail() {
        val user = UserInfo(
            aud = "authenticated",
            id = "user-1",
            email = "tony@example.com",
            userMetadata = buildJsonObject { put("full_name", JsonPrimitive("Tony Tester")) },
        )

        assertEquals(UserProfile("tony@example.com", "Tony Tester"), user.toProfile())
    }

    @Test
    fun profileFallsBackToEmailNameWhenMetadataIsMissing() {
        val user = UserInfo(aud = "authenticated", id = "user-2", email = "tony@example.com")

        assertEquals(UserProfile("tony@example.com", "tony"), user.toProfile())
    }
}
