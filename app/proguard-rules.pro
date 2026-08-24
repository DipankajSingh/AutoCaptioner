# ============================================================
# ProGuard / R8 rules for AutoCaptioner
# ============================================================

# Preserve line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ============================================================
# Kotlinx Serialization
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
# JNI / Native — keep WhisperEngine native methods
# ============================================================
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine { *; }
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine$ProgressListener { *; }
-keep class com.dipdev.aiautocaptioner.core.whisper.WhisperEngine$SegmentListener { *; }
-keepnames class com.dipdev.aiautocaptioner.core.whisper.WhisperException { *; }

# ============================================================
# Media3 Transformer + Effect — these AARs ship NO consumer
# ProGuard rules. Without this keep, R8's aggressive inlining
# disrupts the SDK_INT >= 31 guard around LogSessionId, causing
# a NoClassDefFoundError on Android 10 and all pre-API-31
# devices (minSdk = 24, so Android 7–11 are all affected).
# ============================================================
-keep class androidx.media3.transformer.** { *; }
-keep class androidx.media3.effect.** { *; }
-dontwarn androidx.media3.**

# ============================================================
# Enums — Room TypeConverters call Enum.valueOf(String) at
# runtime for every DB read/write. R8 can rename enum constants,
# silently breaking all caption style and project data reads.
# ============================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ============================================================
# MediaPipe — no consumer rules shipped in AAR
# ============================================================
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

