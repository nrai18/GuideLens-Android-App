@file:Suppress("DEPRECATION")

import java.util.Properties

plugins {
    id("com.android.application") version "8.13.2"
    id("org.jetbrains.kotlin.android") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}

android {
    namespace = "com.example.guidelensapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.guidelensapp"
        minSdk = 29  // Android 10+
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // OpenAI API Key
        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
             localPropertiesFile.inputStream().use { localProperties.load(it) }
        }
        val openAiApiKey = localProperties.getProperty("OPENAI_API_KEY") ?: ""
        buildConfigField("String", "OPENAI_API_KEY", "\"$openAiApiKey\"")

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val versionName = defaultConfig.versionName
            val buildType = buildType.name
            output.outputFileName = "GuideLens-v${versionName}-${buildType}.apk"
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
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

composeCompiler {
    enableStrongSkippingMode = true
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM & Libraries
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ONNX Runtime
    implementation(libs.onnx.runtime.android)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Accompanist for permissions
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // ML Kit Text Recognition (Unbundled / Play Services version to avoid 16KB alignment issues)
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")

    // Retrofit & Gson (Network & JSON)
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Play Services Coroutines (for await())
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    // Google Gemini AI SDK
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0")
}
