plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.streaming.platform.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.streaming.platform.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        // 10.0.2.2 is the Android emulator's alias for the host machine's localhost, reaching the
        // same api-gateway container the web/admin apps were verified against.
        buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:8080/v1\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":shared:common"))
    implementation(project(":shared:core"))
    implementation(project(":shared:security"))
    implementation(project(":shared:authentication"))
    implementation(project(":shared:profile"))
    implementation(project(":shared:content"))
    implementation(project(":shared:search"))
    implementation(project(":shared:playback"))
    implementation(project(":shared:watchlist"))
    implementation(project(":shared:network"))

    implementation(libs.coroutines.core)
    implementation(libs.datetime)
    implementation(libs.serialization.json)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.content)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)

    implementation(libs.coil.compose)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.coroutines.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
