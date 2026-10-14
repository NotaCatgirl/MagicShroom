plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
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
    }

    buildTypes {
        debug {
            // The emulator reaches your computer's localhost at 10.0.2.2 (backend: ./gradlew bootRun).
            buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/\"")
            // Sent as X-Stub-User-Id until OAuth2 sign-in exists: 2 = regular user, 1 = admin.
            buildConfigField("String", "STUB_USER_ID", "\"2\"")
        }
        release {
            optimization {
                enable = false
            }
            isMinifyEnabled = false
            // TODO(deploy): replace with the deployed HTTPS API URL (Sprint 3).
            buildConfigField("String", "API_BASE_URL", "\"https://your-api.example.com/\"")
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
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // TODO(db): unused so far. The app should reach data through our API (Android -> Spring -> DB),
    //  not Supabase directly, which would also ship database keys inside the APK. Remove if agreed.
    implementation(platform("io.github.jan-tennert.supabase:bom:3.5.0"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.ktor:ktor-client-android:3.0.3")
}
