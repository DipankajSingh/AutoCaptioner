plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("com.google.firebase.firebase-perf")
}



android {
    namespace = "com.dipdev.aiautocaptioner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dipdev.aiautocaptioner"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "1.6"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        @Suppress("UnstableApiUsage")
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++17")
                abiFilters("arm64-v8a")
            }
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Allow FFmpeg Kit packaging
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
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
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // -------------------------------------------------------
    // Navigation
    // -------------------------------------------------------
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // -------------------------------------------------------
    // Lifecycle + ViewModel
    // -------------------------------------------------------
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // -------------------------------------------------------
    // Hilt Dependency Injection
    // -------------------------------------------------------
    implementation("com.google.dagger:hilt-android:2.55")
    ksp("com.google.dagger:hilt-compiler:2.55")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // -------------------------------------------------------
    // Room Database
    // -------------------------------------------------------
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // -------------------------------------------------------
    // Media3 — ExoPlayer + Transformer
    // -------------------------------------------------------
    implementation("androidx.media3:media3-exoplayer:1.10.0")
    implementation("androidx.media3:media3-ui:1.10.0")
    implementation("androidx.media3:media3-transformer:1.10.0")
    implementation("androidx.media3:media3-effect:1.10.0")
    implementation("androidx.media3:media3-common:1.10.0")

    // -------------------------------------------------------
    // FFmpeg Kit — video processing + format fallback
    // -------------------------------------------------------
    //implementation("com.github.arthenica:ffmpeg-kit:6.0.LTS") // via JitPack

    // -------------------------------------------------------
    // OkHttp — model downloading
    // -------------------------------------------------------
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // -------------------------------------------------------
    // Coroutines
    // -------------------------------------------------------
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // -------------------------------------------------------
    // DataStore — settings + onboarding flag
    // -------------------------------------------------------
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // -------------------------------------------------------
    // Coil — thumbnail image loading
    // -------------------------------------------------------
    implementation("io.coil-kt:coil-compose:2.7.0")

    // -------------------------------------------------------
    // Splash Screen
    // -------------------------------------------------------
    implementation("androidx.core:core-splashscreen:1.2.0")

    // -------------------------------------------------------
    // Lottie — animations
    // -------------------------------------------------------
    implementation("com.airbnb.android:lottie-compose:6.7.1")

    // -------------------------------------------------------
    // Accompanist — runtime permissions
    // -------------------------------------------------------
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")

    // -------------------------------------------------------
    // Firebase
    // -------------------------------------------------------
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-crashlytics")      // Crash reporting
    implementation("com.google.firebase:firebase-crashlytics-ndk")  // NDK/C++ crash support
    implementation("com.google.firebase:firebase-analytics")        // Usage analytics
    implementation("com.google.firebase:firebase-perf")             // Performance monitoring
    implementation("com.google.firebase:firebase-config")           // Remote Config

    // -------------------------------------------------------
    // Testing
    // -------------------------------------------------------
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}