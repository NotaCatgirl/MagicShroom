plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        val supabaseUrl = providers.gradleProperty("supabaseUrl").orElse("https://example.supabase.co").get()
        val supabaseKey = providers.gradleProperty("supabasePublishableKey").orElse("sb_publishable_replace_me").get()
        buildConfigField("String", "BACKEND_URL", "\"$backendUrl\"")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"$supabaseKey\"")
    }

    buildTypes {
        debug {
            // Keep the optional Retrofit prototype pointed at the same API as the active screen.
            buildConfigField("String", "API_BASE_URL", "\"${backendUrl.trimEnd('/')}/\"")
            // The prototype uses this only when run against the isolated mock backend profile.
            buildConfigField("String", "STUB_USER_ID", "\"2\"")
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
