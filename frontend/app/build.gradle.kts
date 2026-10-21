import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Read the public Auth settings from the ignored root .env; database credentials stay server-side.
val rootEnv = Properties().apply {
    load(providers.fileContents(rootProject.layout.projectDirectory.file("../.env"))
        .asText.orElse("").get().reader())
}

android {
    val backendUrl = providers.gradleProperty("backendUrl").orElse("http://10.0.2.2:8080").get()
    namespace = "edu.csumb.magicshroom"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "edu.csumb.magicshroom"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val supabaseUrl = providers.gradleProperty("supabaseUrl").orElse(
            rootEnv.getProperty("SUPABASE_URL") ?: rootEnv.getProperty("supabaseUrl") ?: "https://example.supabase.co",
        ).get().trim().removeSurrounding("\"").removeSurrounding("'")
        val supabaseKey = providers.gradleProperty("supabasePublishableKey").orElse(
            rootEnv.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: rootEnv.getProperty("supabasePublishableKey")
                ?: "sb_publishable_replace_me",
        ).get().trim().removeSurrounding("\"").removeSurrounding("'")
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", "\"${backendUrl.trimEnd('/')}/\"")
            // Optional -PstubUserId=1 (admin) / 2 (user): acts as that mock-profile user. Blank sends nothing.
            val stubUserId = providers.gradleProperty("stubUserId").orElse("").get()
            buildConfigField("String", "STUB_USER_ID", "\"$stubUserId\"")
        }
        release {
            optimization {
                enable = false
            }
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"${backendUrl.trimEnd('/')}/\"")
            buildConfigField("String", "STUB_USER_ID", "\"\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Supabase Auth supplies bearer tokens; card and deck data still go through our backend API.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
}
