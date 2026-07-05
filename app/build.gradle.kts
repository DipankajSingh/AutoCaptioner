plugins {
    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.room)
}



android {
    namespace = "com.dipdev.aiautocaptioner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dipdev.aiautocaptioner"
        minSdk = 24
        targetSdk = 37
        versionCode = 9
        versionName = "1.9"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
            }
        }
        
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            configure<com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension> {
                nativeSymbolUploadEnabled = true
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Allow FFmpeg Kit packaging
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {


    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.foundation.layout)
    // -------------------------------------------------------
    // Core Android
    // -------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // -------------------------------------------------------
    // Compose
    // -------------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // -------------------------------------------------------
    // Navigation
    // -------------------------------------------------------
    implementation(libs.androidx.navigation.compose)

    // -------------------------------------------------------
    // CameraX
    // -------------------------------------------------------
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)

    // -------------------------------------------------------
    // Lifecycle + ViewModel
    // -------------------------------------------------------
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // -------------------------------------------------------
    // Hilt Dependency Injection
    // -------------------------------------------------------
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // -------------------------------------------------------
    // Room Database
    // -------------------------------------------------------
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // -------------------------------------------------------
    // Media3 — ExoPlayer + Transformer
    // -------------------------------------------------------
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.common)

    // -------------------------------------------------------
    // FFmpeg Kit — video processing + format fallback
    // -------------------------------------------------------
    //implementation("com.github.arthenica:ffmpeg-kit:6.0.LTS") // via JitPack

    // -------------------------------------------------------
    // OkHttp — model downloading
    // -------------------------------------------------------
    implementation(libs.okhttp)

    // -------------------------------------------------------
    // Coroutines
    // -------------------------------------------------------
    implementation(libs.kotlinx.coroutines.android)

    // -------------------------------------------------------
    // DataStore — settings + onboarding flag
    // -------------------------------------------------------
    implementation(libs.androidx.datastore.preferences)

    // -------------------------------------------------------
    // Coil — thumbnail image loading
    // -------------------------------------------------------
    implementation(libs.coil.compose)

    // -------------------------------------------------------
    // Splash Screen
    // -------------------------------------------------------
    implementation(libs.androidx.core.splashscreen)

    // -------------------------------------------------------
    // Lottie — animations
    // -------------------------------------------------------
    implementation(libs.lottie.compose)

    // -------------------------------------------------------
    // Accompanist — runtime permissions
    // -------------------------------------------------------
    implementation(libs.accompanist.permissions)

    // -------------------------------------------------------
    // Firebase
    // -------------------------------------------------------
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)      // Crash reporting
    implementation(libs.firebase.crashlytics.ndk)  // NDK/C++ crash support
    implementation(libs.firebase.analytics)        // Usage analytics
    implementation(libs.firebase.perf)             // Performance monitoring
    implementation(libs.firebase.config)           // Remote Config

    // -------------------------------------------------------
    // RevenueCat
    // -------------------------------------------------------
    implementation(libs.revenuecat.purchases)

    // -------------------------------------------------------
    // Testing

    // -------------------------------------------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}