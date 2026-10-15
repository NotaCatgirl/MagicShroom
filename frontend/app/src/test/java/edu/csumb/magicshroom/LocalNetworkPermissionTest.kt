package edu.csumb.magicshroom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the debug-only emulator permission gate. */
class LocalNetworkPermissionTest {
    /** Requests local access for the Android 17 emulator alias. */
    @Test fun emulatorBackendOnApi37NeedsPermission() {
        assertTrue(requiresEmulatorNetworkPermission("http://10.0.2.2:8080", 37, true))
    }

    /** Leaves older Android versions and release builds unaffected. */
    @Test fun olderDevicesAndReleaseBuildsDoNotPrompt() {
        assertFalse(requiresEmulatorNetworkPermission("http://10.0.2.2:8080", 36, true))
        assertFalse(requiresEmulatorNetworkPermission("http://10.0.2.2:8080", 37, false))
    }

    /** Never requests nearby-device access for a remote HTTPS backend. */
    @Test fun remoteBackendDoesNotPrompt() {
        assertFalse(requiresEmulatorNetworkPermission("https://magicshroom.onrender.com", 37, true))
    }
}
