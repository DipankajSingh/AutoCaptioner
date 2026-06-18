# AutoCaptioner Codebase Audit Report

This report consolidates the findings from a manual manual code inspection of both the Android App (`app/` directory) and the Web Frontend (`website/` directory).

## Critical

### [Android] Hardcoded RevenueCat Test API Key & Debug Logging in Production
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/AutoCaptionerApp.kt`
* **Observation:** The application initializes RevenueCat with a hardcoded test API key and sets `Purchases.logLevel = LogLevel.DEBUG`. 
* **Recommendation:** Extract the API key into `BuildConfig` or strings resources to separate test and production environments. Wrap `LogLevel.DEBUG` inside a `if (BuildConfig.DEBUG)` check.

### [Web] Unbounded `setInterval` for Animations
* **File:** `website/main.js`
* **Observation:** The hero caption animation uses a `setInterval` loop to toggle the `.active` class every 800ms. This continues to run indefinitely even when the browser tab is inactive.
* **Recommendation:** Refactor the animation to use CSS `@keyframes` which are natively optimized by the browser to pause when the tab is inactive, or use `IntersectionObserver` to only run the interval when visible.

## Major

### [Android] Foreground Service Exceptions Swallowed
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/service/TranscriptionService.kt`
* **Observation:** The `startForeground` method is wrapped in a `try/catch` block that completely ignores exceptions. On Android 14+, missing permissions or improperly declared foreground service types cause `startForeground` to throw exceptions, and swallowing them will lead to the system crashing the app shortly after.
* **Recommendation:** Handle the exception properly (e.g., abort the transcription, show an error to the user). Ensure that all Android 14+ specific foreground service permissions and types are correctly requested before launching the service.

### [Android] Severe Rendering Performance Overhead (GC Churn)
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/engine/CaptionRenderer.kt` and `CaptionUtils.kt`
* **Observation:** Rapid object creation in the `draw` method (e.g., using `.map { }` to allocate new lists on every frame) leads to massive Garbage Collection churn, causing dropped frames and lag during video playback.
* **Recommendation:** Pre-calculate `TimedWord` models and text layouts. Cache the layout lines and update them only when the segment data or current phrase changes, rather than rebuilding lists continuously on every `onDraw` frame.

### [Web] Unhandled Invalid Selectors in Smooth Scroll
* **File:** `website/main.js`
* **Observation:** The smooth scroll script uses `document.querySelector(targetId)` where `targetId` is derived directly from the anchor's `href` attribute. If it starts with a number, an exception is thrown, breaking all subsequent JS execution.
* **Recommendation:** Replace `document.querySelector(targetId)` with `document.getElementById(targetId.substring(1))` which safely handles ID strings that begin with numbers.

### [Web] Missing Fallbacks for Environment Variables
* **File:** `website/main.js` and `<script>` tags in `website/privacy.html` / `website/terms.html`
* **Observation:** The code assigns links using `import.meta.env` directly. If these environment variables are missing during the build process, they resolve to `undefined` and break links.
* **Recommendation:** Implement fallbacks for these variables (e.g., `const playStoreLink = import.meta.env.VITE_PLAY_STORE_LINK || '#';`).

## Minor

### [Android] Broken String Interpolation in Logs
* **File:** `app/src/main/java/com/dipdev/aiautocaptioner/data/repository/CaptionRepository.kt`
* **Observation:** The log statement uses an escaped dollar sign `\${defaults.size}`, printing literally instead of evaluating the variable.
* **Recommendation:** Remove the backslash to correctly interpolate the variable string.

### [Android] `allowBackup=true` Without Custom Data Extraction Rules
* **File:** `app/src/main/AndroidManifest.xml`
* **Observation:** The manifest allows backups, which could needlessly bloat the user's Google Drive cloud backup with large ML models stored in the `filesDir`.
* **Recommendation:** Either set `android:allowBackup="false"` or specify `android:dataExtractionRules` and `android:fullBackupContent` to explicitly exclude the models directory.

### [Android] Room Schema Export Not Configured
* **File:** `app/build.gradle.kts`
* **Observation:** The Room KSP compiler is added, but schema export is not configured, producing a build warning and making database migrations hard to track.
* **Recommendation:** Add the Room schema location argument to the `ksp` block in `build.gradle.kts`.

### [Android] Obfuscated Stack Traces Configuration
* **File:** `app/proguard-rules.pro`
* **Observation:** The `-keepattributes SourceFile,LineNumberTable` lines are commented out, risking obfuscated line numbers in Firebase stack traces.
* **Recommendation:** Uncomment these lines in `proguard-rules.pro`.

### [Web] Leftover Default Vite Boilerplate Code
* **File:** `website/src/` directory
* **Observation:** Remnants of the default Vite project template are still present, cluttering the codebase.
* **Recommendation:** Delete the `website/src/` directory entirely to maintain a clean codebase.

### [Web] Broken Grid Layout in Subpage Footers
* **File:** `website/privacy.html` and `website/terms.html`
* **Observation:** The footer markup omits certain sections but the CSS grid is strictly defined, leaving empty grid columns and inconsistent layout.
* **Recommendation:** Create a modifier class for the subpages that uses a simplified grid template, or include the full footer navigation uniformly.

### [Web] Missing Open Graph / SEO Metadata
* **File:** `website/index.html`, `website/privacy.html`, `website/terms.html`
* **Observation:** The HTML `<head>` lacks standard Open Graph and Twitter Card metadata for rich link previews.
* **Recommendation:** Add standard Open Graph and Twitter Card `<meta>` tags to the `<head>` of all HTML files.

### [Web] Missing Content Security Policy (CSP)
* **File:** `website/index.html`, `website/privacy.html`, `website/terms.html`
* **Observation:** There is no CSP defined, increasing theoretical vulnerability to XSS attacks.
* **Recommendation:** Add a baseline CSP meta tag restricting scripts and styles to `self` and trusted domains.
