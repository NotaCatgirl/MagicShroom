import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
}

// Read only the public Auth settings from backend/.env; database credentials stay server-side.
val backendEnv = Properties().apply {
    load(providers.fileContents(rootProject.layout.projectDirectory.file("../backend/.env"))
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
            backendEnv.getProperty("SUPABASE_URL") ?: backendEnv.getProperty("supabaseUrl") ?: "https://example.supabase.co",
        ).get().trim().removeSurrounding("\"").removeSurrounding("'")
        val supabaseKey = providers.gradleProperty("supabasePublishableKey").orElse(
            backendEnv.getProperty("SUPABASE_PUBLISHABLE_KEY") ?: backendEnv.getProperty("supabasePublishableKey")
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

// Any finding not recorded in the baseline fails the build. Regenerate the baseline only when
// deliberately accepting existing findings: ./gradlew :app:detektBaseline
detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    baseline = file("$rootDir/config/detekt/baseline.xml")
    ignoreFailures = false
}

// Unit tests that call the deployed Render API are skipped unless run with -PliveApiTests=true.
tasks.withType<Test>().configureEach {
    systemProperty("magicshroom.liveApiTests", providers.gradleProperty("liveApiTests").orElse("false").get())
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
