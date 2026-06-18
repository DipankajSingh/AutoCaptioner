# AutoCaptioner App - Codebase Audit Findings

## Critical

### 1. Hardcoded RevenueCat Test API Key & Debug Logging in Production
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/AutoCaptionerApp.kt` (Lines 24-30)
* **Description:** The application initializes RevenueCat with a hardcoded test API key (`"test_GTLdsaEjNvvBCJkIemFTKrOoHzA"`) and sets `Purchases.logLevel = LogLevel.DEBUG`. If this is released to production, real purchases will fail, and sensitive purchase flow details will be logged.
* **Recommendation:** Extract the API key into `BuildConfig` or strings resources to separate test and production environments. Wrap `LogLevel.DEBUG` inside a `if (BuildConfig.DEBUG)` check.

## High

### 2. Foreground Service Exceptions Swallowed
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/service/TranscriptionService.kt` (Lines 46-56)
* **Description:** The `startForeground` method is wrapped in a `try/catch` block that completely ignores the exception to "prevent fatal crashes". However, on Android 14+, missing permissions or improperly declared foreground service types cause `startForeground` to throw a `SecurityException` or `InvalidForegroundServiceTypeException`. Swallowing this exception will still lead to the system crashing the app shortly after with a `ForegroundServiceDidNotStartInTimeException` because the foreground promise was never fulfilled.
* **Recommendation:** Handle the exception properly (e.g., abort the transcription, show an error to the user). Ensure that all Android 14+ specific foreground service permissions and types are correctly requested before launching the service.

### 3. Severe Rendering Performance Overhead (GC Churn)
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/engine/CaptionRenderer.kt` and `CaptionUtils.kt`
* **Description:** In the `draw` method (which runs at up to 60fps), `CaptionUtils.buildTimedWords` uses `.map { }` to allocate a new list of `TimedWord` objects on every single frame. The `lines.map { Triple(...) }` also allocates new collections. This rapid object creation leads to massive Garbage Collection (GC) churn, causing dropped frames and lag during video playback.
* **Recommendation:** Pre-calculate `TimedWord` models and text layouts. Cache the layout lines in the ViewModel or update them only when the segment data or current phrase changes, rather than rebuilding lists continuously on every `onDraw` frame.

## Minor / Best Practices

### 4. Broken String Interpolation in Logs
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/data/repository/CaptionRepository.kt` (Line 306)
* **Description:** The log statement `Log.i(TAG, "Initialized \${defaults.size} default styles")` has an escaped dollar sign. It will literally print `\${defaults.size}` instead of evaluating the variable.
* **Recommendation:** Remove the backslash: `"${defaults.size}"`.

### 5. `allowBackup=true` Without Custom Data Extraction Rules
* **File:** `app/src/main/AndroidManifest.xml` (Line 22)
* **Description:** The manifest sets `android:allowBackup="true"`. Since the app deals with video processing and downloaded models, backing up the `filesDir` (where the Whisper ML models are downloaded) could needlessly bloat the user's Google Drive cloud backup and slow down device transfers.
* **Recommendation:** Either set `android:allowBackup="false"` or specify `android:dataExtractionRules` and `android:fullBackupContent` to explicitly exclude the `models/` directory from auto-backup.

### 6. Room Schema Export Not Configured
* **File:** `app/build.gradle.kts`
* **Description:** The Room KSP compiler is added, but schema export is not configured. This will produce a build warning and makes it difficult to track database migrations over time.
* **Recommendation:** Add the Room schema location argument to the `ksp` block in `build.gradle.kts`:
```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

### 7. Obfuscated Stack Traces Configuration
* **File:** `app/proguard-rules.pro`
* **Description:** The `-keepattributes SourceFile,LineNumberTable` lines are commented out. While the Crashlytics Gradle plugin often injects these automatically, explicitly declaring them ensures that line numbers are properly retained and stack traces in Firebase are deobfuscated accurately.
* **Recommendation:** Uncomment these lines in `proguard-rules.pro`.
