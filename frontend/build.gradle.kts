import java.io.File
import java.security.MessageDigest

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
}

// Desktop may be synced by iCloud, which can introduce numbered copies of generated classes.
// Keep this checkout's build output local, with a separate directory for each checkout/module.
val desktopDirectory = File(System.getProperty("user.home"), "Desktop").toPath()
if (System.getProperty("os.name").startsWith("Mac") && rootDir.toPath().startsWith(desktopDirectory)) {
    val checkoutId = MessageDigest.getInstance("SHA-256")
        .digest(rootDir.absolutePath.toByteArray(Charsets.UTF_8))
        .take(8).joinToString("") { "%02x".format(it) }
    val localBuildRoot = gradle.gradleUserHomeDir.resolve("magicshroom-builds/$checkoutId")
    allprojects {
        val moduleDirectory = if (path == ":") "root" else path.removePrefix(":").replace(':', '/')
        layout.buildDirectory.set(localBuildRoot.resolve(moduleDirectory))
    }
}
