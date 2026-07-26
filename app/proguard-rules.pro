# ============================================================
# ProGuard / R8 rules for AutoCaptioner
# ============================================================

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Kotlinx Serialization
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

# ============================================================
# Navigation Compose type-safe routes — Screen sealed class
# data objects generate serializers differently from data classes;
# both variants must survive R8 to prevent navigation crashes.
# ============================================================
-keep class com.dipdev.aiautocaptioner.ui.navigation.Screen { *; }
-keep class com.dipdev.aiautocaptioner.ui.navigation.Screen$* { *; }

# ============================================================
# compose-icons-feather (br.com.devsrsouza.compose.icons)
# Library does NOT ship consumer rules — icon objects get stripped.
# ============================================================
-keep class compose.icons.** { *; }
-dontwarn compose.icons.**

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
-keep @dagger.hilt.Module class * { *; }
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
-keep class * extends androidx.lifecycle.ViewModelProvider$Factory { *; }

# ============================================================
# JNI / Native — keep WhisperEngine native methods
# ============================================================
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine { *; }
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine$ProgressListener { *; }
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine$SegmentListener { *; }
-keepnames class com.dipdev.aiautocaptioner.core.whisper.WhisperException { *; }

# ============================================================
# Firebase — no consumer rules shipped in AAR
# ============================================================
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# ============================================================
# Lottie — no consumer rules shipped in AAR
# ============================================================
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ============================================================
# Media3 / ExoPlayer — no consumer rules shipped in AAR
# ============================================================
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ============================================================
# MediaPipe — no consumer rules shipped in AAR
# ============================================================
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# ============================================================
# General — keep enums (Room TypeConverters use .valueOf)
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
