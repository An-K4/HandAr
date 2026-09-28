plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.navigation.safeargs)
}

android {
    namespace = "com.example.handar"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.handar"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        // armeabi-v7a is kept on purpose, but MediaPipe tasks-vision >= 0.10.26 only ships
        // arm64-v8a native libs, so hand tracking is expected to fail on 32-bit-only devices.
        ndk {
            abiFilters.clear()
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }
    }

    // CI signs release builds with a dedicated keystore (GitHub Secrets).
    // Locally, release falls back to the debug keystore so it stays installable.
    val ciKeystorePath: String? = System.getenv("CI_KEYSTORE_PATH")
    signingConfigs {
        if (ciKeystorePath != null) {
            create("ci") {
                storeFile = file(ciKeystorePath)
                storePassword = System.getenv("CI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CI_KEY_ALIAS")
                keyPassword = System.getenv("CI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.getByName(if (ciKeystorePath != null) "ci" else "debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                "magic-hand-${variant.buildType}.apk"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    val cameraxVersion = "1.4.2"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    implementation("com.google.mediapipe:tasks-vision:0.10.26")
}