import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Optional OpenRouteService key.
 *
 * Resolution order at runtime is: Settings screen (DataStore) -> BuildConfig.ORS_API_KEY.
 * The value below comes from local.properties (git-ignored) and defaults to "" (blank == not
 * provided), which makes the app fall back to the public OSRM server. No key is ever hardcoded.
 */
val orsApiKey: String = run {
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.exists()) {
        ""
    } else {
        val properties = Properties()
        localProperties.inputStream().use { properties.load(it) }
        properties.getProperty("ORS_API_KEY").orEmpty().trim()
    }
}

android {
    namespace = "com.example.indriveclone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.indriveclone"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "ORS_API_KEY",
            "\"${orsApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // Map (OpenStreetMap) — needs no API key
    implementation(libs.osmdroid.android)

    // HTTP + JSON
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
