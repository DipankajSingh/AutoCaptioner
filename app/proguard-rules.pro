# ============================================================
# ProGuard / R8 rules for AutoCaptioner
# ============================================================

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Kotlinx Serialization  (THE CRASH FIX)
# R8 renames @Serializable classes & strips their $serializer,
# breaking kotlinx.serialization at runtime.
# ============================================================
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers @kotlinx.serialization.Serializable class com.dipdev.aiautocaptioner.** {
    *** Companion;
}
-keepclasseswithmembers class com.dipdev.aiautocaptioner.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.dipdev.aiautocaptioner.**$$serializer { *; }
-keepclassmembers class com.dipdev.aiautocaptioner.** {
    *** Companion;
}
-keepclassmembers class com.dipdev.aiautocaptioner.ui.navigation.Screen {
    *** Onboarding;
    *** DeviceCheck;
    *** ModelManager;
    *** ModelDownload;
    *** Home;
    *** Settings;
    *** ExportHistory;
    *** ProjectEditorGraph;
    *** VideoEditor;
    *** Processing;
    *** CaptionEditor;
    *** Export;
    *** SmartRecorder;
}

# ============================================================
# Hilt / Dagger  (reflection-based DI)
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$ViewWithFragmentContextWrapper { *; }

-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.Module class * { *; }
-keep class * extends dagger.hilt.android.internal.** { *; }

-keepclassmembers,allowobfuscation class * {
    @dagger.hilt.* <fields>;
    @dagger.hilt.* <methods>;
}

# Hilt generated components and factories
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-keep class **_HiltModules* { *; }
-keep class **_GeneratedInjector { *; }
-keep class **_MembersInjector { *; }

# ============================================================
# Room Database
# ============================================================
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-keep @androidx.room.Embedded class * { *; }
-keep @androidx.room.Relation class * { *; }

-keep class com.dipdev.aiautocaptioner.data.db.entity.** { *; }
-keep class com.dipdev.aiautocaptioner.data.db.dao.** { *; }
-keep class com.dipdev.aiautocaptioner.data.db.Converters { *; }
-keep class com.dipdev.aiautocaptioner.data.db.AppDatabase { *; }

-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# ============================================================
# Kotlin Coroutines
# ============================================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembers class kotlin.coroutines.SafeContinuation { *; }
-dontwarn kotlinx.coroutines.**

# ============================================================
# AndroidX Lifecycle + ViewModel
# ============================================================
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# ============================================================
# Compose Navigation (type-safe routes)
# ============================================================
-keep class androidx.navigation.compose.** { *; }
-keep class androidx.navigation.** { *; }

# ============================================================
# JNI / Native — keep WhisperEngine native methods
# ============================================================
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine { *; }
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine$ProgressListener { *; }
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine$SegmentListener { *; }
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperException { *; }

# ============================================================
# Firebase
# ============================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ============================================================
# MediaPipe
# ============================================================
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ============================================================
# RevenueCat
# ============================================================
-keep class com.revenuecat.purchases.** { *; }
-keep class com.revenuecat.purchases.models.** { *; }
-dontwarn com.revenuecat.purchases.**

# ============================================================
# Coil (image loading)
# ============================================================
-keep class coil3.** { *; }
-keep class io.coil3.** { *; }
-dontwarn coil3.**
-dontwarn io.coil3.**

# ============================================================
# Lottie
# ============================================================
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ============================================================
# OkHttp
# ============================================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ============================================================
# Media3 / ExoPlayer
# ============================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================================
# Splash Screen
# ============================================================
-keep class androidx.core.splashscreen.** { *; }

# ============================================================
# DataStore
# ============================================================
-keep class androidx.datastore.** { *; }
-keep class * extends androidx.datastore.preferences.core.Preferences { *; }

# ============================================================
# Google Play Services (AD_ID, etc.)
# ============================================================
-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }

# ============================================================
# General — keep enums (Room TypeConverters use .valueOf)
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep all custom exceptions for crash reporting
-keep class com.dipdev.aiautocaptioner.core.** { *; }