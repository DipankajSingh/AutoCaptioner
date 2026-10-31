# AutoCaptioner - Deep Technical & Architectural Specification

This document provides a comprehensive, code-level architectural specification for **AutoCaptioner**. It details every subsystem, native JNI C++ interop, audio extraction pipeline, custom physics and graphics rendering engine, database schema, Room DAO queries, state machines, and hardware video export pipeline.

---

## Table of Contents
1. [System Architecture & Overview](#1-system-architecture--overview)
2. [Native C++ & JNI Layer (`cpp/` & `core/whisper`)](#2-native-c--jni-layer-cpp--corewhisper)
3. [Audio Extraction & Processing Pipeline (`core/audio`)](#3-audio-extraction--processing-pipeline-coreaudio)
4. [Whisper Transcription Manager & Segmenter (`core/whisper`)](#4-whisper-transcription-manager--segmenter-corewhisper)
5. [Custom Graphics & Caption Engine (`engine/`)](#5-custom-graphics--caption-engine-engine)
6. [Multi-Pass OpenGL Hardware Renderer (`engine/render/`)](#6-multi-pass-opengl-hardware-renderer-enginerender)
7. [Video Export & MediaCodec Encoding (`ui/export/`)](#7-video-export--mediacodec-encoding-uiexport)
8. [Database Schema, DAOs & Repositories (`data/`)](#8-database-schema-daos--repositories-data)
9. [UI Screens, Navigation & State Management (`ui/`)](#9-ui-screens-navigation--state-management-ui)
10. [Hardware Benchmarking & Model Recommendation (`core/device/`)](#10-hardware-benchmarking--model-recommendation-coredevice)
11. [Hilt Dependency Injection Architecture (`di/`)](#11-hilt-dependency-injection-architecture-di)

---

## 1. System Architecture & Overview

AutoCaptioner is an offline-first, on-device video captioning studio built with 100% Kotlin, Jetpack Compose, C++ JNI, Room, and OpenGL ES 3.0.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              Jetpack Compose UI                                 │
│  (Onboarding · Home · Processing · Video Editor · Caption Editor · Export UI)   │
└────────────────────────┬────────────────────────────────┬───────────────────────┘
                         │                                │
                         ▼                                ▼
       ┌─────────────────────────────────┐     ┌─────────────────────┐
       │      Hilt ViewModel Layer       │     │ Shared Media Player │
       │ (StateFlow / Screen MVI States) │     │ (ExoPlayer / Media3)│
       └────────────────┬────────────────┘     └─────────────────────┘
                        │
       ┌────────────────┴────────────────┬───────────────────────────┐
       ▼                                 ▼                           ▼
┌───────────────┐               ┌─────────────────┐         ┌─────────────────┐
│ Room Database │               │ Custom Graphics │         │ Native C++ AI   │
│  Repository   │               │ Caption Engine  │         │  Whisper JNI    │
└───────────────┘               └─────────────────┘         └─────────────────┘
```

---

## 2. Native C++ & JNI Layer (`cpp/` & `core/whisper`)

### 2.1 Native C++ Build Configuration (`CMakeLists.txt`)
The C++ core compiles GGML and Whisper.cpp libraries into a shared native library (`libautocaptioner_jni.so`) targeting `arm64-v8a` with ARM NEON SIMD vectorization.

### 2.2 JNI Bridge Interface (`jni_bridge.cpp`)
The JNI bridge exposes C functions matching Kotlin native declarations:

```cpp
// Native Method Signatures
JNIEXPORT jlong JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeInit(JNIEnv* env, jobject thiz, jstring modelPath);
JNIEXPORT void JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeFree(JNIEnv* env, jobject thiz, jlong contextPtr);
JNIEXPORT jint JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeFullTranscribe(JNIEnv* env, jobject thiz, jlong contextPtr, jfloatArray audioSamples, jint sampleCount, jint threadCount, jstring language);
JNIEXPORT jint JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeGetSegmentCount(JNIEnv* env, jobject thiz, jlong contextPtr);
JNIEXPORT jstring JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeGetSegmentText(JNIEnv* env, jobject thiz, jlong contextPtr, jint segmentIndex);
JNIEXPORT jlong JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeGetSegmentStart(JNIEnv* env, jobject thiz, jlong contextPtr, jint segmentIndex);
JNIEXPORT jlong JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeGetSegmentEnd(JNIEnv* env, jobject thiz, jlong contextPtr, jint segmentIndex);
JNIEXPORT jint JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeGetTokenCount(JNIEnv* env, jobject thiz, jlong contextPtr, jint segmentIndex);
JNIEXPORT jobject JNICALL Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_nativeGetTokenData(JNIEnv* env, jobject thiz, jlong contextPtr, jint segmentIndex, jint tokenIndex);
```

### 2.3 Execution Parameters
* `whisper_full_params` are configured with:
  * `params.strategy = WHISPER_SAMPLING_GREEDY`
  * `params.n_threads = min(4, CPU_CORES)`
  * `params.token_timestamps = true` (enables word-level millisecond alignment)
  * `params.single_segment = false`
  * `params.language = languageCode` (or `"auto"` for automatic detection)

---

## 3. Audio Extraction & Processing Pipeline (`core/audio`)

Before AI transcription can execute, source video files must yield a clean 16kHz 16-bit mono PCM WAV stream.

### 3.1 Extraction Pipeline Flow (`AudioExtractionUseCase.kt`)
1. **Track Discovery**: `MediaExtractor` inspects the input video URI and locates the first track with `mime.startsWith("audio/")`.
2. **Format Inspection**: Retrieves sample rate, channel count, and duration.
3. **Hardware Decoding**: `MediaCodec` configures a decoder for the selected audio format (e.g. AAC, MP3, Opus).
4. **Resampling & Channel Reduction**:
   * Reads output `ByteBuffer` buffers from `MediaCodec`.
   * Downsamples source sample rate (e.g. 44.1kHz / 48kHz) to target **16000 Hz**.
   * Downmixes stereo/multi-channel samples into single-channel 16-bit PCM mono samples.
5. **WAV File Serialization**:
   * Writes a 44-byte canonical RIFF/WAVE header followed by raw 16-bit little-endian PCM sample frames:
     * Header layout: Bytes 0–3 (`RIFF`), 4–7 (`ChunkSize`), 8–11 (`WAVE`), 12–15 (`fmt `), 16–19 (16 for PCM), 20–21 (AudioFormat 1), 22–23 (NumChannels 1), 24–27 (SampleRate 16000), 28–31 (ByteRate 32000), 32–33 (BlockAlign 2), 34–35 (BitsPerSample 16), 36–39 (`data`), 40–43 (`DataSize`).

---

## 4. Whisper Transcription Manager & Segmenter (`core/whisper`)

### 4.1 Orchestrator State Machine (`TranscriptionManager.kt`)
`TranscriptionManager` executes audio transcription inside a `ForegroundService` container (`TranscriptionForegroundService.kt`), broadcasting progression updates via `StateFlow<TranscriptionState>`:

```
┌─────────────────┐      ┌──────────────┐      ┌──────────────┐      ┌───────────┐
│ ExtractingAudio ├────► │ Transcribing ├────► │  Segmenting  ├────► │ Completed │
└────────┬────────┘      └──────┬───────┘      └──────┬───────┘      └───────────┘
         │                      │                     │
         ▼                      ▼                     ▼
┌────────────────────────────────────────────────────────────────────────────────┐
│                                 Error Handling                                 │
└────────────────────────────────────────────────────────────────────────────────┘
```

### 4.2 Segmenting & Short-Form Optimization (`CaptionSegmenter.kt`)
Raw Whisper output usually returns long sentences or paragraphs. Short-form videos (Reels/Shorts) require 1 to 4 words per frame on vertical screens.

**Segmentation Rules Algorithm**:
1. **Target Chunk Size**: 1 to 4 words per segment.
2. **Line Length Limit**: Maximum 18 characters per line to avoid awkward text truncation.
3. **Pause Thresholding**: If the gap between word $W_n$ and word $W_{n+1}$ exceeds **300ms**, force a segment break regardless of word count.
4. **Punctuation Boundaries**: End of sentence marks (`.`, `?`, `!`, `,`) force an immediate line termination.

---

## 5. Custom Graphics & Caption Engine (`engine/`)

The caption engine converts plain text and timestamps into dynamic, spring-animated vector layouts rendered at 60 FPS.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                CaptionEngine                                    │
└────────┬──────────────────────┬────────────────────────┬────────────────────────┘
         │                      │                        │
         ▼                      ▼                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌──────────────────┐
│  LayoutEngine   │    │   TimingEngine   │    │ AnimationEngine  │
│(Bounds/Wrapping)│    │ (Active Word t)  │    │ (Spring Physics) │
└─────────────────┘    └──────────────────┘    └──────────────────┘
```

### 5.1 Paint & Style Factory (`CaptionPaints.kt` & `PresetFactory.kt`)
* **Text Paint Setup**:
  * `fillPaint`: `Paint(ANTI_ALIAS_FLAG)` with `Paint.Style.FILL`.
  * `strokePaint`: `Paint(ANTI_ALIAS_FLAG)` with `Paint.Style.STROKE`, `strokeJoin = Paint.Join.ROUND`, `strokeCap = Paint.Cap.ROUND`.
  * `shadowPaint`: Configured via `Paint.setShadowLayer(radius, dx, dy, shadowColor)`.
  * `backgroundPaint`: `Paint(ANTI_ALIAS_FLAG)` with rounded corner rectangle parameters (`RectF`, `cornerRadius`).
* **Presets Supported**:
  * **Hormozi**: Bold uppercase font (`Montserrat Black`), vibrant yellow highlight, solid black outline stroke, scale pop animation.
  * **Neon Glow**: Cyberpunk neon cyan text with outer glowing blur and elastic animation.
  * **Vibrant Yellow**: High-contrast yellow and white dual-tone line highlights.
  * **Minimal**: Clean, elegant white text with soft dark drop shadow and subtle fade animation.

### 5.2 Safe-Zone Layout Calculation (`LayoutEngine.kt`)
Calculates safe drawing bounds on mobile screens:
* **Horizontal Safe Margins**: Default 10% side padding (`marginHorizontalRatio = 0.1f`).
* **Vertical Positioning**: Controlled by `positionY` (`0.0f` = Top, `0.5f` = Center, `0.8f` = Bottom).
* **Multi-Line Text Wrapping**:
  * Evaluates total text width using `Paint.measureText()`.
  * If width exceeds `maxWidth = canvasWidth * (1f - 2 * marginHorizontalRatio)`, breaks line at closest word boundary.

### 5.3 Timing Engine & Word Lifecycle (`TimingEngine.kt` & `WordLifecycle.kt`)
Given current playback timestamp $T_{ms}$:
* Locates active `CaptionSegmentEntity` where $Segment.startMs \le T_{ms} \le Segment.endMs$.
* Locates active `CaptionWordEntity` where $Word.startMs \le T_{ms} \le Word.endMs$.
* Computes normalized word animation time:
  $$t_{progress} = \text{clamp}\left(\frac{T_{ms} - Word.startMs}{Word.endMs - Word.startMs}, 0.0, 1.0\right)$$

### 5.4 Physics & Spring Oscillators (`SpringUtils.kt` & `AnimationEngine.kt`)
Animators compute transforms without keyframes using damped harmonic oscillator equations:

$$y(t) = 1 - e^{-\zeta \omega_n t} \left(\cos(\omega_d t) + \frac{\zeta}{\sqrt{1-\zeta^2}} \sin(\omega_d t)\right)$$

Where:
* $\zeta$ = Damping Ratio (e.g. `0.5f` for bouncy, `1.0f` for critically damped).
* $\omega_n$ = Undamped Natural Frequency.
* $\omega_d = \omega_n \sqrt{1 - \zeta^2}$ = Damped Frequency.

#### Active Word Animators
1. **`BounceAnimator`**: Scales word up to `1.25x` and applies vertical spring offset.
2. **`ScalePopAnimator`**: Rapid scale pop (`0.8x` $\rightarrow$ `1.15x` $\rightarrow$ `1.0x`).
3. **`ElasticAnimator`**: Overshooting scale expansion with damped spring oscillation.
4. **`SpringPopAnimator`**: Combined scale pop and $5^\circ$ rotational tilt.
5. **`FlipAnimator`**: 3D X/Y rotation matrix transformation (`Camera.rotateX()`).
6. **`ShakeAnimator`**: High-frequency horizontal/rotational jitter for emphatic words.
7. **`TypeWriterAnimator`**: Character-by-character reveal progress.
8. **`SlideUpAnimator` / `SlideDownAnimator`**: Off-screen translation entry.
9. **`FadeAnimator`**: Alpha opacity interpolation ($0.0 \rightarrow 1.0$).
10. **`NoneAnimator`**: Instant static text render.

---

## 6. Multi-Pass OpenGL Hardware Renderer (`engine/render/`)

For high-performance 60 FPS playback and hardware video export, caption drawing uses an OpenGL ES 3.0 multi-pass pipeline.

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           OpenGL ES 3.0 Context (`EglCore.kt`)                  │
└────────────────────────────────────────┬────────────────────────────────────────┘
                                         │
                                         ▼
                               ┌───────────────────┐
                               │ RenderingPipeline │
                               └─────────┬─────────┘
                                         │
        ┌────────────────────────────────┼────────────────────────────────┐
        │                                │                                │
        ▼                                ▼                                ▼
┌───────────────┐              ┌───────────────────┐            ┌──────────────────┐
│BackgroundPass │              │    OutlinePass    │            │   TextFillPass   │
│(Pills/Shadows)│              │  (Text Strokes)   │            │(Active Highlights│
└───────────────┘              └───────────────────┘            └──────────────────┘
```

### 6.1 Multi-Pass Pipeline Breakdown
* **Pass 1: `BackgroundPass`**: Renders rounded rectangle background pills behind active text words or lines onto the target FBO (Framebuffer Object).
* **Pass 2: `OutlinePass`**: Renders expanded text path stroke outlines in a secondary shader pass.
* **Pass 3: `TextFillPass`**: Renders main text fill glyphs, applying color gradients and active word highlight colors.

---

## 7. Video Export & MediaCodec Encoding (`ui/export/`)

Video export renders edited captions and overlays onto source video frames, encoding a final MP4 file via Android hardware codecs.

### 7.1 Pipeline Architecture (`ExportForegroundService.kt`)

```
┌────────────────┐     ┌────────────────┐     ┌─────────────────┐     ┌──────────────┐
│ MediaExtractor ├────►│ MediaCodec     ├────►│ SurfaceTexture  ├────►│ EGL Offscreen│
│ (Source MP4)   │     │ (Decoder)      │     │ (OES Texture)   │     │ Render Thread│
└────────────────┘     └────────────────┘     └─────────────────┘     └──────┬───────┘
                                                                             │
┌────────────────┐     ┌────────────────┐                             │
│ MediaMuxer     │◄────┤ MediaCodec     │◄───────────────────────────────────┘
│ (Output MP4)   │     │ (Encoder H.264)│ (Draw Video Frame + Caption Overlay)
└────────────────┘     └────────────────┘
```

### 7.2 Step-by-Step Export Pipeline
1. **Service Initialization**: Spawns `ExportForegroundService` with notification channel and `WAKE_LOCK`.
2. **Codec Setup**:
   * Creates `MediaExtractor` for source video and audio tracks.
   * Configures `MediaCodec` H.264 video encoder targeting target width/height (e.g. 1080x1920) and high bitrate (~15 Mbps).
   * Obtains encoder input `Surface`.
3. **EGL Context Creation**:
   * Initializes `EglCore` and wraps the encoder input surface with a `WindowSurface`.
   * Creates an OES external texture ID for video decoding.
4. **Frame-by-Frame Processing Loop**:
   * `MediaExtractor` feeds compressed video frames into `MediaCodec` decoder.
   * `Decoder` outputs raw frame to `SurfaceTexture`.
   * `SurfaceTexture.updateTexImage()` binds video frame.
   * `RenderingPipeline` draws video texture pass + `CaptionEngine` vector overlay at exact frame timestamp $T_{pts}$.
   * `windowSurface.setPresentationTime(ptsNanos)` sets Android presentation time.
   * `windowSurface.swapBuffers()` sends composite frame to `MediaCodec` encoder.
5. **Audio Muxing**:
   * Concurrently extracts, transcodes (if necessary), and muxes audio track into output `MediaMuxer`.
6. **Completion**: Writes final file record into `ExportedFileEntity` and clears notification.

---

## 8. Database Schema, DAOs & Repositories (`data/`)

### 8.1 Database Entity Relational Schema (`AppDatabase.kt`)

```
┌──────────────────┐       1:N       ┌──────────────────────┐       1:N       ┌───────────────────┐
│  ProjectEntity   ├────────────────►│ CaptionSegmentEntity ├────────────────►│ CaptionWordEntity │
└────────┬─────────┘                 └──────────────────────┘                 └───────────────────┘
         │
         │ 1:1                       1:N       ┌───────────────────┐
         ├────────────────────────────────────►│ TextOverlayEntity │
         │                                     └───────────────────┘
         │ 1:1
         ├────────────────────────────────────►│ CaptionStyleEntity│
         │                                     └───────────────────┘
         │ 1:N                       1:N       ┌────────────────────┐
         ├────────────────────────────────────►│ ImageOverlayEntity │
         │                                     └────────────────────┘
         │ 1:N                       1:N       ┌────────────────────┐
         └────────────────────────────────────►│ ExportedFileEntity │
                                               └────────────────────┘
```

### 8.2 Entity Definitions

#### 1. `ProjectEntity`
```kotlin
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val videoPath: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val aspectRatio: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

#### 2. `CaptionSegmentEntity`
```kotlin
@Entity(
    tableName = "caption_segments",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class CaptionSegmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val sequenceIndex: Int
)
```

#### 3. `CaptionWordEntity`
```kotlin
@Entity(
    tableName = "caption_words",
    foreignKeys = [
        ForeignKey(
            entity = CaptionSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("segmentId"), Index("projectId")]
)
data class CaptionWordEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val segmentId: String,
    val projectId: String,
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Float = 1.0f
)
```

#### 4. `CaptionStyleEntity`
```kotlin
@Entity(
    tableName = "caption_styles",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class CaptionStyleEntity(
    @PrimaryKey val projectId: String,
    val fontFamily: String = "Montserrat",
    val fontSize: Float = 28f,
    val primaryTextColor: Int = 0xFFFFFFFF.toInt(),
    val secondaryTextColor: Int = 0xFFE0E0E0.toInt(),
    val highlightTextColor: Int = 0xFFFFD700.toInt(),
    val outlineColor: Int = 0xFF000000.toInt(),
    val outlineWidth: Float = 4f,
    val shadowColor: Int = 0x80000000.toInt(),
    val shadowRadius: Float = 8f,
    val shadowOffsetX: Float = 2f,
    val shadowOffsetY: Float = 4f,
    val activeWordAnimation: String = "BOUNCE",
    val displayMode: String = "WORD_BY_WORD",
    val positionY: Float = 0.8f
)
```

#### 5. `TextOverlayEntity` & `ImageOverlayEntity`
Stores user stickers and graphical text overlays with `startMs`, `endMs`, position (`x`, `y`), scale, and rotation angle.

#### 6. `ExportedFileEntity`
Logs output MP4 files with file size, resolution, and creation timestamp.

---

## 9. UI Screens, Navigation & State Management (`ui/`)

Navigation uses Jetpack Navigation Compose with type-safe `@Serializable` routes (`Screen.kt` & `NavGraph.kt`).

```
                              ┌──────────────────┐
                              │ Screen.Onboarding│
                              └────────┬─────────┘
                                       │
                                       ▼
                             ┌───────────────────┐
                             │Screen.SetupLanguage│
                             └─────────┬─────────┘
                                       │
                                       ▼
                             ┌───────────────────┐
                             │Screen.SetupQuality│
                             └─────────┬─────────┘
                                       │
                                       ▼
                                ┌─────────────┐
                                │ Screen.Home │
                                └──────┬──────┘
                                       │
            ┌──────────────────────────┼──────────────────────────┬──────────────────────────┐
            ▼                          ▼                          ▼                          ▼
   ┌──────────────────┐       ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
   │Screen.SmartRecord│       │Screen.Processing│        │Screen.VideoEditor│       │ Screen.Settings │
   └────────┬─────────┘       └────────┬────────┘        └────────┬────────┘        └─────────────────┘
            │                          │                          │
            └────────────────────────► │                          ▼
                                       │                 ┌──────────────────┐
                                       ▼                 │  Screen.Export   │
                              ┌────────────────────┐     └──────────────────┘
                              │Screen.CaptionEditor│
                              └────────────────────┘
```

### Screen ViewModel Architecture

1. **`HomeViewModel`**:
   * Observes `ProjectRepository.getAllProjects()`.
   * Manages video import routines (`importVideo` / `importVideoQuick`).
2. **`ProcessingViewModel`**:
   * Drives audio extraction, transcription foreground service binding, progress polling, and error recovery.
3. **`EditorViewModel` & `SharedPlayerViewModel`**:
   * **`SharedPlayerViewModel`**: Scoped to `ProjectEditorGraph`, maintaining a single ExoPlayer instance across Video Editor and Style tabs to avoid player re-instantiation stutter.
   * **`HistoryManager.kt`**: Implements undo/redo state stacks for caption styling and text edits.
   * **`OverlayManager.kt`**: Handles touch drag/pinch-scale positioning for image/text overlays.
4. **`CaptionEditorViewModel`**:
   * Provides fine-grained word timestamp editing, word splitting, merging, and word insertion.
5. **`ExportViewModel`**:
   * Binds to `ExportForegroundService`, streaming progress percentage and navigating to `ExportHistoryScreen` upon completion.
6. **`CameraRecorderViewModel` & `SmartRecorderScreen`**:
   * Uses CameraX `VideoCapture<Recorder>` with a 16:9 `ResolutionSelector` aspect ratio strategy matching preview framing to recorded video output.
   * Dynamic camera lens switching (`LaunchedEffect(previewView, cameraSelector, lifecycleOwner)`) via top control bar toggle, bottom controls row flip button, and double-tap gesture on the live preview surface.
   * Lifecycle-aware recording safety (`DisposableEffect(lifecycleOwner)` observing `ON_PAUSE`/`ON_STOP`) to cleanly finalize active recordings when navigating away or backgrounding the app.
   * Display rotation matching (`previewView.display?.rotation`) on `Preview` and `VideoCapture`.
   * Enforces a 3-minute max recording cap, near-limit warning badge, review overlay, and project creation import pipeline.

---

## 10. Hardware Benchmarking & Model Recommendation (`core/device/`)

To prevent Out-Of-Memory (OOM) crashes on low-end devices, `DeviceCheckUseCase` benchmarks hardware specs before recommending GGML Whisper models:

### Benchmark Threshold Logic
* **RAM Memory Thresholds**:
  * `< 3.0 GB RAM`: Recommends `tiny.en` (~75 MB RAM footprint).
  * `3.0 GB - 5.5 GB RAM`: Recommends `base.en` (~140 MB RAM footprint).
  * `5.5 GB - 7.5 GB RAM`: Recommends `small` (~460 MB RAM footprint).
  * `> 7.5 GB RAM`: Recommends `medium` (~1.5 GB RAM footprint).
* **Language-Specific Routing & Hinglish Model Tiering**:
  * **Hinglish (`hinglish`)**: Automatically recommends `hi.hinglish.swift` (~57 MB, Q5_1 quantized GGML build) across all hardware profiles to avoid out-of-memory risks while ensuring sub-8-second transcription per 30-second clip on mobile CPU.
  * **Roman Script Decoding**: Configured with `whisperCode = "en"` in `WhisperLanguages.kt`, enforcing Whisper token generation in Latin alphabet space (*"Kal office mein meeting hai"*) to prevent Devanagari Unicode fallback loops.
  * **Onboarding Model Tiering**: `OnboardingModelTierMapper` groups Hinglish models into three tiers:
    * **Fast & Light (Recommended)**: `hi.hinglish.swift` (57 MB)
    * **Standard**: `hi.hinglish.q5` (547 MB)
    * **Pro Quality**: `hi.hinglish.q8` (834 MB)
* **CPU Evaluation**: Evaluates available ARM CPU cores (`Runtime.getRuntime().availableProcessors()`) to assign optimal thread count (`n_threads`).

---

## 11. Hilt Dependency Injection Architecture (`di/`)

Hilt manages singleton dependency graph scopes across the app:

* **`AppModule.kt`**:
  * `@Provides @Singleton fun provideApplicationContext(@ApplicationContext context: Context): Context`
  * Provides `FileStorageManager`, `DeviceCapabilityUseCase`, `MediaProcessor`.
* **`DatabaseModule.kt`**:
  * `@Provides @Singleton fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase`
  * Provides `ProjectDao`, `CaptionSegmentDao`, `CaptionWordDao`, `CaptionStyleDao`, `TextOverlayDao`, `ImageOverlayDao`, `ExportedFileEntityDao`.
* **`WhisperModule.kt`**:
  * `@Provides @Singleton fun provideWhisperEngine(context: Context): WhisperEngine`
  * Provides `TranscriptionManager`.
