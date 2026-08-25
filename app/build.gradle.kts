plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.perf)
    alias(libs.plugins.room)
    alias(libs.plugins.kotlin.serialization)
}



android {
    namespace = "com.dipdev.aiautocaptioner"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dipdev.aiautocaptioner"
        minSdk = 24
        targetSdk = 37
        versionCode = 25
        versionName = "2.11.2"

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
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {

    // -------------------------------------------------------
    // Core Android & Lifecycle
    // -------------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.bundles.lifecycle)

    // -------------------------------------------------------
    // Compose
    // -------------------------------------------------------
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.feather)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // -------------------------------------------------------
    // Navigation & Dependency Injection
    // -------------------------------------------------------
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // -------------------------------------------------------
    // Data & Persistence
    // -------------------------------------------------------
    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)

    // -------------------------------------------------------
    // Media & Camera
    // -------------------------------------------------------
    implementation(libs.bundles.camera)
    implementation(libs.bundles.media3)
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.mediapipe.tasks.vision)

    // -------------------------------------------------------
    // Networking & Image Loading
    // -------------------------------------------------------
    implementation(libs.okhttp)
    implementation(libs.bundles.coil)

    // -------------------------------------------------------
    // Firebase & RevenueCat
    // -------------------------------------------------------
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)
    implementation(libs.revenuecat.purchases)

    // -------------------------------------------------------
    // Coroutines
    // -------------------------------------------------------
    implementation(libs.kotlinx.coroutines.android)

    // -------------------------------------------------------
    // Testing
    // -------------------------------------------------------
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

