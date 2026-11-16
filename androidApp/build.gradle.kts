plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes only from environment variables (CI secrets, or set by you
// locally). Nothing secret is stored in the repository.
val releaseKeystore: String? = providers.environmentVariable("POCKETADBLOCK_KEYSTORE_FILE").orNull

// CI: every build gets a higher versionCode; tag builds (v1.2.3) their version name.
val ciRunNumber: Int? = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
val ciVersion: String? = providers.environmentVariable("POCKETADBLOCK_VERSION").orNull?.removePrefix("v")

android {
    namespace = "io.github.joanrra2.pocketadblock"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.joanrra2.pocketadblock"
        minSdk = 26
        targetSdk = 36
        versionCode = ciRunNumber ?: 1
        versionName = ciVersion ?: "0.1.0-dev"
    }

    signingConfigs {
        if (!releaseKeystore.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("POCKETADBLOCK_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("POCKETADBLOCK_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("POCKETADBLOCK_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Pocket AdBlock (debug)"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            manifestPlaceholders["appLabel"] = "Pocket AdBlock"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    // MainActivity is a FragmentActivity (BiometricPrompt).
    implementation(libs.androidx.biometric)
}
