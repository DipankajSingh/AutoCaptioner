# AutoCaptioner: Transcription Speed & Accuracy — Living Tracker

> **Source:** AutoCaptioner Transcription Investigation — Part 0 of 8 (Evidence Ledger & Roadmap)
> **Prepared:** October 31, 2026 | **Last updated:** November 2, 2026
> **Priority order:** Accuracy first, Speed second
> **Rule:** One change per experiment, per commit. Before + after numbers required in `bench_results/`.

---

## 🗺️ Overall Progress

| Phase | Status | Notes |
|-------|--------|-------|
| **Device recon** | ✅ Done | Both phones read via adb |
| **Owner decisions** | 🟡 5 of 14 resolved | See blocker list below |
| **Part 1 — Telemetry** | 🟡 Fully instrumented | TxAnalytics, TxRecord, timers, Crashlytics, Perf traces, diagnostics |
| **Part 2 — Benchmark harness** | 🟡 Harness built | Device harness & laptop scripts complete |
| **Part 3 — Whisper re-setup** | 🟡 Configured | NativeLibLoader, ARMv8.2 loader, CMake v1.9.5, S6/S7 complete |
| **Part 4 — Audio pipeline** | ⬜ Not started | |
| **Part 5 — Decoding/threading** | ⬜ Not started | Awaiting Part 2 baseline (abort callback ready) |
| **Part 6 — Models/onboarding** | 🟡 Guardrails built | D6/G5 language guards complete; awaiting Part 5 results |
| **Part 7 — Long-video guard** | 🟡 Phase 1 ready | VideoLengthGuard & native JNI abort callback |
| **Part 8 — Rollout** | 🟡 Docs & plan ready | Documentation, licenses, benchmark log, and release workflow ready |

---

## 📋 Implementation Order

```
Step 1  →  Tag B0, build benchmark harness, measure baseline — NO publishing
Step 2  →  Build Parts 3 + 4 + 1 + 7 locally
Step 3  →  First test pass vs. B0 → decide Part 5 settings
Step 4  →  Build + test Part 6 from measured speed table
Step 5  →  Publish once: internal testing track → staged production rollout
Step 6  →  Part 7 Phase 2 (block processing) — post-release
```

---

## 📱 Device Registry (Read via ADB — Nov 2, 2026)

### Device 1 — Realme 3 (RMX1825) `NRUGQO9PGAZXAYPF`

| Property | Value |
|----------|-------|
| SoC | Helio P70 — MT6771V/WT |
| Android | 10 (SDK 29) |
| ABI | arm64-v8a |
| Total RAM | **2,797 MB** (~2.73 GB) |
| CPU layout | 4× Cortex-A53 (`0xd03`) + 4× Cortex-A73 (`0xd09`) |
| CPU Features | `fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid` |
| `asimddp` | ❌ Absent |
| `fphp` / `asimdhp` | ❌ Absent |
| ARMv8.2 | ❌ **NO — baseline armv8-a only** |
| App RAM tier | **Below 3,000 MB → `tiny.en` (English) / `base` (others)** |

> [!IMPORTANT]
> PDF assumed Realme 3 gets `base.en` — **WRONG**. At 2,797 MB it falls below the 3,000 MB threshold and auto-gets `tiny.en` for English. This affects Part 6 recommender logic.

### Device 2 — Realme Narzo 60 5G? (RMX3782) `Y9NJFYQ8TC6HR4CU`

| Property | Value |
|----------|-------|
| SoC | **MediaTek Dimensity 7050 (MT6835)** |
| Android | **15** |
| ABI | arm64-v8a |
| Total RAM | **5,749 MB** (~5.48 GB) |
| CPU layout | 6× Cortex-A55 (`0xd05`) + 2× Cortex-A78 (`0xd0b`) |
| CPU Features | `fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp` |
| `asimddp` | ✅ **Present on all 8 cores** |
| `fphp` / `asimdhp` | ✅ **Present on all 8 cores** |
| ARMv8.2 | ✅ **YES — ARMv8.2 library WILL ship** |
| App RAM tier | **Below 6,000 MB → `base` / `base.en`** (NOT `small`) |

> [!IMPORTANT]
> **ARMv8.2 library ships in this release** — Part 3 condition fully met.
> PDF assumed "8 GB phone" → `small.en`. **WRONG** — at 5,749 MB it falls below the 6,000 MB threshold and auto-gets `base`/`base.en`. Recommender in Part 6 must be fixed.

---

## 🔑 Owner Decisions & Blockers

| # | Decision | Status | Resolved |
|---|----------|--------|---------|
| 1 | Realme 3 cpuinfo + RAM | ✅ Done | Nov 2, 2026 |
| 2 | RMX3782 cpuinfo + RAM | ✅ Done | Nov 2, 2026 |
| 3 | ARMv8.2 test phone availability | ✅ Done | Nov 2, 2026 — RMX3782 qualifies |
| 4 | Support email for Play Store reply | ⬜ Pending | — |
| 5 | Does video import have any length limit today? | ⬜ Pending | — |
| 6 | Pass rules: ≥10% lower RTF + ≤0.5 pp WER | ⬜ Pending | — |
| 7 | Time budget — Balanced tier (~20 s / 30-s clip?) | ⬜ Pending | — |
| 8 | Time budget — Accurate tier (~45 s / 30-s clip?) | ⬜ Pending | — |
| 9 | Long-video policy: warn-only vs. hard cap + dialog wording | ⬜ Pending | — |
| 10 | Self-host model files? + Licence review of third-party fine-tunes | ⬜ Pending | — |
| 11 | Keep all current language fine-tunes in catalog? | ⬜ Pending | — |
| 12 | Privacy + consent position for new telemetry | ⬜ Pending | — |
| 13 | Firebase Remote Config acceptable for feature flags? | ⬜ Pending | — |
| 14 | Record + correct the test corpus clips (Part 2) | ⬜ Pending | Owner must record |

---

## 🔬 Findings Log

> Append every verified fact, refuted claim, or new discovery here with a date.

| Date | Finding | Status | Impact |
|------|---------|--------|--------|
| Nov 2, 2026 | Realme 3 RAM = 2,797 MB — below 3,000 MB threshold | VERIFIED | Auto-gets `tiny.en`, not `base.en`. Part 6 bug. |
| Nov 2, 2026 | Realme 3 has NO `asimddp` / FP16 — pure ARMv8.0 | VERIFIED | Baseline library only for this device |
| Nov 2, 2026 | RMX3782 (MT6835) has `asimddp` + `fphp` + `asimdhp` on all 8 cores | VERIFIED | ARMv8.2 library ships in this release |
| Nov 2, 2026 | RMX3782 RAM = 5,749 MB — below 6,000 MB threshold | VERIFIED | Auto-gets `base`, not `small`. Part 6 bug. |
| Nov 2, 2026 | RMX3782 runs Android 15 | VERIFIED | Thermal API (API 29+) available; full telemetry support |
| Oct 31, 2026 | Temperature fallback re-runs only decoder (not encoder) | REFUTED (original claim) | Far less slowdown than original report claimed |
| Oct 31, 2026 | VAD cannot give 3–6× speedup as claimed | REFUTED | Encoder cost is per 30-s window regardless |
| Oct 31, 2026 | Official tiny.en and base.en quantized files are q5_1 + q8_0, NOT q5_0 | VERIFIED | Part 5 quantization experiments must use correct files |
| Oct 31, 2026 | English models are unquantized FP16 | VERIFIED | Big opportunity for quantization speedup |
| Oct 31, 2026 | No speed figure in the PDF is a real measurement | CONFIRMED | Everything is a target until Part 2 baseline exists |
| Oct 31, 2026 | Current resampler is plain linear interpolation, no anti-aliasing | VERIFIED | Part 4 E4.2 replacement is needed |
| Oct 31, 2026 | Model reloads on every transcription run | VERIFIED | Part 5 E5.5 model residency experiment needed |
| Oct 31, 2026 | No abort callback in JNI — cancel doesn't stop native decoding | VERIFIED | Part 5 + Part 7 must add this |
| Oct 31, 2026 | whisper.cpp version string says 1.7.4 but code is between v1.8.4–v1.8.5 | VERIFIED | Stale version string — clean up in Part 3 |
| Oct 31, 2026 | Firebase + BigQuery telemetry currently unreachable (setup missing) | VERIFIED | Part 1 must build this before release |
| Oct 31, 2026 | Onboarding DeviceCheck hard-codes `en` for model recommendation | VERIFIED | Part 6 bug — language-first flow needed |

---

## 🧪 Experiment Results Log

> Fill in after each Part 2 benchmark run. Leave blank until measured.

| ID | Experiment | Device | Before (RTF) | After (RTF) | WER Δ | Decision |
|----|-----------|--------|-------------|------------|-------|----------|
| B0 | Baseline — current release build v2.11.4 | Realme 3 | — | — | — | Pending |
| B0 | Baseline — current release build v2.11.4 | RMX3782 | — | — | — | Pending |
| E4.2 | Resampler: linear → band-limited | — | — | — | — | Pending |
| E4.3 | Level normalization grid | — | — | — | — | Pending |
| E4.4 | High-pass filter 80 Hz | — | — | — | — | Pending |
| E4.5 | VAD + window packing | — | — | — | — | Pending |
| E4.6 | audio_ctx reduction for short clips | — | — | — | — | Pending |
| E5.1 | Fallback policy comparison | — | — | — | — | Pending |
| E5.2 | Thread count sweep | — | — | — | — | Pending |
| E5.3 | Flash attention A/B | — | — | — | — | Pending |
| E5.4 | Quantization: FP16 vs q8_0 vs q5_1 | — | — | — | — | Pending |
| E5.5 | Model residency (keep loaded) | — | — | — | — | Pending |

---

## 📝 Session Log

| Date | Session | Key Actions |
|------|---------|-------------|
| Oct 31, 2026 | PDF authored | 33-page investigation written; 8-part plan defined |
| Nov 2, 2026 | Session 1 | PDF read; master task list created; both devices connected and read via adb; ARMv8.2 unblocked; 2 recommender bugs discovered |
| Nov 2, 2026 | Session 2 | Built benchmark harness (Part 2), Telemetry core (Part 1), NativeLibLoader & JNI timings (Part 3), VideoLengthGuard & instant JNI cancel (Part 7) |
| Nov 7, 2026 | Session 3 | R1-R5 camera recorder bug fixes, B1-B2 language and model compatibility guards (D6/G5), C1-C3 Firebase analytics schema & Crashlytics keys (T7, T10, T11), S6/D1-D3 documentation updates (PROJECT_DOCUMENTATION.md, THIRD_PARTY.md, BENCHMARKS.md, RELEASE.md) |

---

## 🐛 Bugs Discovered

| # | Bug | Where | Impact | Resolved |
|---|-----|-------|--------|---------|
| BUG-01 | Onboarding `DeviceCheck` hard-codes language `en` when recommending — non-English users get wrong model | `DeviceCheck` screen | Medium — wrong model auto-selected | ✅ Resolved (Phase B / B1 — DeviceCheck uses selected language) |
| BUG-02 | No guard for English-only model paired with non-English language | Model selection logic | High — wrong/empty transcription | ✅ Resolved (Phase B / B2 — Language/model compatibility guard) |
| BUG-03 | RAM tier thresholds don't match device reality: RMX3782 at 5,749 MB gets `base`, not `small` as the code comment implies a "6 GB" phone would | Recommender logic | Medium — suboptimal model for device | Part 6 |
| BUG-04 | Realme 3 at 2,797 MB gets `tiny.en` (below 3 GB), but PDF assumed it gets `base.en` | Recommender logic | Low — mismatched expectations | Part 6 |
| BUG-05 | No abort callback in JNI — pressing Cancel does not stop native decoding | `jni_bridge.cpp` | High — app stuck until whisper_full returns | ✅ Resolved (Part 7 — JNI abort_callback) |
| BUG-06 | Model reloads on every run (initialized + released in finally block) — load time not measured | `TranscriptionManager.kt` | Medium — wasted time per run | Part 5 E5.5 |
| BUG-07 | `whisper_print_timings` never called; no fallback counter exists | `jni_bridge.cpp` | Medium — can't measure or debug slowdowns | ✅ Resolved (Part 1 T3 — JNI getNativeTimings) |
| BUG-08 | whisper.cpp version string in CMake says `1.7.4` but actual code is between v1.8.4–v1.8.5 | `CMakeLists.txt` | Low — stale metadata | ✅ Resolved (Part 3 S6 — CMake v1.9.5) |
| BUG-09 | Resampler is plain linear interpolation — no anti-aliasing low-pass filter | `AudioExtractionUseCase.kt` | Medium — potential aliasing artifacts | Part 4 E4.2 |
| BUG-10 | `PROJECT_DOCUMENTATION.md` is stale (wrong thread rule, library names, JNI names, medium tier, benchmark claims) | Docs | Low — misleads contributors | ✅ Resolved (Part 3 S6 / D1-D3 — Docs updated with NativeLibLoader, whisper-lib, JNI signatures, and RAM tiers) |

---

## 🚩 Feature Flags (to implement in Part 8)

| Flag | Controls | Default | Kill-switch for |
|------|----------|---------|----------------|
| `vad_enabled` | VAD + window packing | OFF | Part 4 E4.5 |
| `preprocess_resampler_v2` | Band-limited resampler | OFF | Part 4 E4.2 |
| `preprocess_normalize` | Level normalization | OFF | Part 4 E4.3 |
| `audio_ctx_reduction` | Shorter encoder context | OFF | Part 4 E4.6 |
| `thread_policy_v2` | New thread selection | OFF | Part 5 E5.2 |
| `cpu_variant_override` | Force baseline or ARMv8.2 | OFF | Part 3 S4 |
| `recommender_v2` | Language-first recommendation | OFF | Part 6 |
| `long_video_guard` | 10-min warning dialog | OFF | Part 7 |

---

## 📐 Reference: Key Metrics

| Metric | Formula |
|--------|---------|
| RTF | Whisper wall time ÷ clip duration |
| ms/window | Whisper time ÷ window count |
| Encode share | `encode_ms` ÷ Whisper time |
| Window count | `ceil(clip_seconds / 30)` |
| Outlier threshold | RTF > 3, OR clip < 60 s taking > 2 min |
| Pass rule (proposed) | ≥10% RTF reduction on both phones + WER Δ ≤ 0.5 pp |

---

## 📐 Reference: RAM Tiers (Current Code)

| RAM | English model | Other languages |
|-----|--------------|----------------|
| ≥ 6,000 MB | `small.en` | `small` |
| ≥ 3,000 MB | `base.en` | `base` |
| < 3,000 MB | `tiny.en` | `base` |
| Hinglish (any) | `hi.hinglish.swift` | — |

> [!NOTE]
> **Realme 3** (2,797 MB) → `tiny.en` / `base` &nbsp;&nbsp; **RMX3782** (5,749 MB) → `base.en` / `base`

---

*This document is a living tracker. Update it after every session, experiment, and decision.*

---



## Part 1 — Telemetry & Diagnostics

> **Goal:** Know where the time went and why for any transcription run on any device.
> **Files likely touched:** `TranscriptionManager.kt`, `WhisperEngine.kt`, `jni_bridge.cpp`, `DeviceCapabilityUseCase.kt`, new `core/diagnostics` package, Firebase config, privacy policy.

### Tasks

- [x] **T1** — Create `TranscriptionRunRecord` data class with all fields:
  - Identity & context: random `runId`, app version, model id, language code, translate flag, first-run flag
  - Input: clip duration (ms), source audio sample rate, channel count, codec MIME, resampled sample count, window count
  - Stages (ms): model download (if any), model load, audio extraction, Whisper wall time, save, total
  - Whisper internals: `sample_ms`, `encode_ms`, `decode_ms` from `whisper_get_timings`; fallback counts p and h; word count
  - Resources: threads requested, thermal status at start/end/max, power-save mode, battery %, charging state, available memory at start, low-memory flag, native heap at end
  - Outcome: success / cancelled / error type (no message text)
  - Native build: CPU variant loaded, compiled-in feature string from `whisper_print_system_info`

- [x] **T2** — Add stage timers in `TranscriptionManager` using `SystemClock.elapsedRealtime()` around: model load, extraction, Whisper, save. Keep old `elapsed_ms` param for one release for continuity.

- [x] **T3** — Native timings JNI:
  - After `whisper_full`, call `whisper_get_timings` and return 3 values to Kotlin via new JNI function
  - Install a log callback with `whisper_log_set` to capture the `'fallbacks = N p / M h'` line
  - Parse the two fallback counters; remove callback afterwards

- [x] **T4** — Device capability capture (once per install + app version):
  - Read `/proc/cpuinfo`: Features intersection across cores, CPU part values, core count
  - Record: `Build.MANUFACTURER`, `MODEL`, `HARDWARE`, `BOARD`, `SDK_INT`, supported ABIs, `SOC_MODEL` and `SOC_MANUFACTURER` (where API level allows), total RAM
  - Flags of interest: `asimddp`, `fphp`/`asimdhp`, `i8mm`, `bf16`, `sve`
  - If file unreadable, record `'unknown'`

- [x] **T5** — Thermal & power logging:
  - Record `PowerManager.currentThermalStatus` at start and end (API 29+, else `'unknown'`)
  - Track maximum thermal status via a listener during the run
  - Record: power-save mode, battery level, charging state

- [x] **T6** — Memory logging:
  - Record `ActivityManager` total and available memory, `isLowRamDevice`
  - Capture any `onTrimMemory` callbacks during the run
  - Catch and record `OutOfMemoryError`

- [x] **T7** — Firebase event schema (3 events, each within parameter cap):
  - `tx_run`: one per run (completed / cancelled / failed) — stage, internals, resource, outcome fields
  - `tx_device`: once per install + app version — device and CPU fields
  - `model_download`: model id, size, duration, network type (Wi-Fi / cellular), outcome
  - Keep old events for one release, then retire them

- [ ] **T8** — Register custom event definitions in Firebase console before build ships (Analytics → Events → Manage custom definitions). Confirm ≤25 params per event limit from Firebase docs.

- [ ] **T9** — Link BigQuery in Firebase project settings (Integrations). Confirm data appears before assuming it will.

- [x] **T10** — Crashlytics custom keys at run start: model id, threads, thermal status, window count, CPU variant. Log non-fatal `'slow_transcription'` when RTF > 2 OR total time > 120 s. Use existing `CrashReporter` wrapper.

- [x] **T11** — Performance Monitoring custom traces for model load, extraction, and Whisper — with model id and thread count as attributes (plugin already present).

- [x] **T12** — Hidden Diagnostics screen:
  - Shows last 20 `TranscriptionRunRecord`s
  - "Copy diagnostics" button copies JSON to clipboard (no personal data)
  - Works without any console setup

- [ ] **T13** — Privacy updates:
  - Update privacy policy for device and diagnostic data
  - Update Play Data safety declaration
  - Confirm consent requirements for user regions with the owner

### Acceptance Gates

- [ ] **G1** — On each test phone, a 30-s clip produces one `tx_run` event with every stage field filled; values match logcat timings within 5%
- [ ] **G2** — Events appear in DebugView (`adb shell setprop debug.firebase.analytics.app <package>`); fields appear in reports after registration
- [ ] **G3** — Diagnostics JSON contains no transcript text and no file names (manual review)
- [ ] **G4** — Instrumentation adds less than 1% to run time
- [ ] **G5** — A thrown exception inside telemetry code never fails a transcription (tested by forcing one)
- [ ] **G6 (publish gate)** — Pipeline proven end-to-end on Realme 3: events visible in DebugView, custom definitions registered, BigQuery linked

---

## Part 2 — Benchmark Harness, Corpus & Test Protocol

> **Goal:** A repeatable way to measure speed and accuracy for any change.

### Tasks

- [x] **H1** — Build a debug-only benchmark entry point:
  - Reads clips from `app's external files folder` (placed with `adb push`)
  - Runs the real pipeline N times with a config file
  - Writes JSON + CSV results to the same folder
  - Calls same code path as `TranscriptionManager` through a small interface seam, no UI

- [x] **H2** — Create a `BenchConfig` object with toggles:
  - model, threads, flash attention, fallback policy, VAD on/off, each preprocessing step, `audio_ctx`
  - Parts 4 and 5 add their own toggles here

- [x] **H3** — Raw capability probes:
  - Encoder-only benchmark on random audio (upstream's `whisper-bench`) for model × thread grids
  - ggml matrix-multiply and memcpy probes (`benchGgmlMulMat`, `benchMemcpy` from upstream Android example)
  - Part 6 can use these as a device speed measure

- [x] **H4** — Laptop scripts:
  - `run_corpus.sh` calling `whisper-cli` with same params as JNI (greedy, best_of 1, token timestamps on)
  - WER script
  - Aggregator that prints a Markdown table

- [x] **H5** — Store results in `bench_results/` folder:
  - CSV per device and date
  - Include git commit and native build ID

### Corpus (owner records — ~15 minutes plus 2 long clips)

- [ ] Self-recorded, quiet room, arm's length: 9 s, 30 s, 60 s (3 clips)
- [ ] Self-recorded, noisy (fan / traffic / kitchen): 2 clips
- [ ] Downloaded / edited with jump cuts and music: 2 clips
- [ ] Silence-heavy with long pauses: 1 clip
- [ ] Owner's native language: 2 clips
- [ ] Code-switched speech (e.g., Hinglish): 1 clip (if relevant)
- [ ] Long clips for Part 7: one 3-min + one 10-min clip
- [ ] Reference transcripts: run model first, correct by hand in UTF-8 with matching filename; second-pass sanity check on 3 clips
- [ ] Word-timing references: mark start time of 20 words across 3 clips in an audio editor (regression guard only)

### Protocol

- [ ] Use **release builds only** (not debuggable), freshly installed, model files pinned with SHA-256 recorded
- [ ] Device prep: unplugged at 50–80% battery, battery saver off, fixed brightness, other apps closed, radios off
- [ ] Start each run only when thermal status is NONE. One warm-up run (discarded), 3 measured runs in randomized order. 2-minute pause between runs. Report the **median**.
- [ ] Sustained-load test: 3 consecutive 60-s clips, recording thermal status and per-run time
- [ ] Record per device: `/proc/cpuinfo` Features + CPU parts, total RAM, SoC, Android version
- [ ] Baseline `B0` = current release build (v2.11.4)

### Acceptance Gates

- [ ] **G1** — B0 measured on both phones: 3 clips × 3 runs; results committed
- [ ] **G2** — Laptop and phone transcripts agree within ~1 pp WER on at least 6 clips
- [ ] **G3** — Run-to-run RTF variation < 10% on each phone
- [ ] **G4** — One command regenerates the full report

---

## Part 3 — Whisper Re-setup from Scratch

> **Goal:** Replace patchwork vendored tree with pinned, reproducible whisper.cpp (v1.9.5), minimal JNI, correct CPU optimizations.

### Tasks

- [ ] **S1** — Repository layout:
  - Add `whisper.cpp` as git submodule pinned to `v1.9.5` tag in `app/src/main/cpp/third_party/whisper.cpp`
  - Record commit hash in `THIRD_PARTY.md`
  - Do NOT edit upstream files; use patch files in `cpp/patches` if local changes are unavoidable
  - Delete old copied ggml/whisper files only after new build passes gates

- [x] **S2** — CMake setup (time-boxed spike first):
  - `GGML_NATIVE=OFF`; `GGML_CPU_ARM_ARCH` empty for baseline, `armv8.2-a+fp16+dotprod` for ARMv8.2 variant
  - `BUILD_SHARED_LIBS=OFF`; examples/tests/server OFF
  - Benchmark `GGML_OPENMP` and `GGML_LLAMAFILE` both ways
  - Build each variant with `ExternalProject_Add` (forward `CMAKE_TOOLCHAIN_FILE`, `ANDROID_ABI`, `ANDROID_PLATFORM`); link each static result into its own JNI shared library
  - Keep `-O3 -DNDEBUG` explicit, keep the 16 KB page-size linker flag
  - Set version and commit defines from `git describe` instead of hand-written strings

- [x] **S3** — JNI rewrite (same Kotlin contract):
  - Keep `loadModel`, `freeModel`, `getDetectedLanguage`, `transcribeWithTimestamps` with current signatures
  - Read word times with VAD-aware `whisper_full_get_token_t0` and `t1` instead of raw token data
  - Keep current params: greedy, best_of 1, token_timestamps on, max_len 0, initial prompt carried, flash attention on
  - Add timings + fallback capture from Part 1
  - Accept VAD model path and flag (leave VAD off in this part)
  - Set `params.abort_callback` tied to an atomic cancel flag (Part 7 uses this)

- [x] **S4** — Kotlin library loader (`NativeLibLoader`):
  - Read `/proc/cpuinfo`, take Features common to all cores
  - Choose ARMv8.2 library only if `asimddp` AND (`fphp` OR `asimdhp`) present on every core
  - Wrap in try/catch; offer debug override; fall back to baseline on load failure
  - If previous session crashed natively while ARMv8.2 was loaded, force baseline next time
  - Report chosen variant to telemetry

- [ ] **S5** — Model compatibility test:
  - Load and transcribe a 10-second sample with every model in `whisper_models.json` (FP16, q5_0, q5_1, q8_0, third-party fine-tunes) under the new build
  - Compare transcripts with old build on the corpus

- [x] **S6** — Cleanup:
  - Update `PROJECT_DOCUMENTATION.md` (build section, library names, JNI names)
  - Add `THIRD_PARTY.md`
  - Remove stale defines

- [x] **S7** — 16 KB alignment:
  - Verify final bundle with Google's 16 KB page-size tooling for both libraries

### CPU Variant Decision Gate

> [!IMPORTANT]
> **ARMv8.2 library ships in this release ONLY IF** a lab phone has `asimddp` AND (`fphp` OR `asimdhp`) AND the library was tested on it. If neither phone qualifies, ship only the baseline library.

- Option A (recommended first): Two static JNI library flavours — `whisper-lib` (baseline) + `whisper-lib-v82` (ARMv8.2)
- Option B (later, optional): Upstream dynamic variants with `GGML_BACKEND_DL=ON` + `GGML_CPU_ALL_VARIANTS=ON` — **revisit only after Option A is in production**

### Acceptance Gates

- [ ] **G1** — Clean checkout builds both libraries with one command; `whisper_print_system_info` shows dot-product and FP16 as 1 in ARMv8.2 library and 0 in baseline
- [ ] **G2** — Baseline library transcripts match old build within 0.5 pp WER; no crash in 20 consecutive runs per phone
- [ ] **G3** — On Realme 3 (ARMv8.0), baseline library selected; speed no more than 5% slower than B0
- [ ] **G4** — On any ARMv8.2 phone: ARMv8.2 library selected; speed gain over baseline measured and reported (no fixed threshold). If no lab phone supports ARMv8.2, this library is NOT shipped.
- [ ] **G5** — APK size change reported; 16 KB alignment check passes
- [ ] **G6** — Unit tests for cpuinfo parser using fixture text from both lab phones

---

## Part 4 — Audio Pipeline

> **Goal:** Feed Whisper cleaner and shorter audio. Every step is an experiment gated by corpus metrics.

### Architecture

- [ ] Create `AudioPreprocessor` class with one toggle per stage, driven by `BenchConfig`
- [ ] Each stage has unit tests with synthetic signals (tone, chirp, silence + speech fixture)
- [ ] Use built-in VAD and its remapping (not app-side VAD); keep app-side fallback only if built-in fails gates

### Pipeline Stages & Experiments

- [ ] **Stage 0, decode** — Make streaming: decode → downmix → resample → append (source-rate arrays are transient). Memory details in Part 7.

- [ ] **E4.1 (Stage 1, downmix)** — Compare mean downmix vs. choosing the louder channel when channels are negatively correlated. Low priority; stereo clips only.

- [ ] **E4.2 (Stage 2, resample)** — Replace linear interpolation with band-limited polyphase or windowed-sinc resampler:
  - Cutoff ~7.6 kHz for 16 kHz output, stopband attenuation ≥80 dB
  - Candidate libs: `libsamplerate`, `speexdsp`, Oboe's resampler (confirm licences before adoption)
  - Skip when source is already 16 kHz
  - Unit test: 20 kHz tone at 48 kHz input must be ≥60 dB below full scale at 16 kHz output
  - Resampling must cost < 5% of total time

- [ ] **E4.3 (Stage 3, level normalization)** — Grid over: off, peak-normalize to 0.9, RMS targets around -23 / -20 / -18 dBFS with a limiter (gain cap, e.g., +20 dB). Choose by WER, especially on quiet self-recorded clips.

- [ ] **E4.4 (Stage 4, high-pass filter)** — 80 Hz 2nd-order Butterworth. Keep only if WER improves or is neutral and hum-related errors drop.

- [ ] **E4.5 (Stage 5, VAD + window packing)** — Enable Silero VAD:
  - Models: `silero-v5.1.2` or `silero-v6.2.0` from `ggml-org/whisper-vad` on Hugging Face (~885 KB)
  - Default params: threshold 0.5, min_speech 250 ms, min_silence 100 ms, speech_pad 30 ms, samples_overlap 0.1
  - Tuning hypotheses: speech_pad_ms 100–200, min_silence 300–500 ms, max_speech_duration_s 25–29
  - Use VAD-aware token getters for correct word-time remapping
  - If VAD finds no speech, skip Whisper and report existing 'no speech' outcome — but only after confirming on corpus that no clip with real speech is dropped

- [ ] **E4.6 (Stage 6, audio context reduction)** — For clips ≤30 s (after VAD packing):
  - Set `audio_ctx` = clip seconds × 50 (50 positions/second), capped at 1500, rounded to safe multiple
  - Accuracy and hallucination risk are unknown — decide per model from corpus data only

- [ ] **E4.7 (Stage 7, denoising)** — Last and optional. Test one lightweight denoiser on noisy clips only. Adopt only with a clear WER gain.

### Acceptance Gates

- [ ] **G1** — Each stage passes unit tests; WER does not rise > 0.5 pp; hallucinations and loops do not increase
- [ ] **G2** — Word-timing error on annotated clips does not worsen beyond owner-set tolerance
- [ ] **G3** — Measured speed and window-count change reported per clip type for each adopted stage
- [ ] **G4** — Mixed clip (speech over music) and music-only clip behave sensibly: speech kept; music-only → 'no speech'

---

## Part 5 — Decoding Settings, Threading & Model Residency

> **Goal:** Choose decoder settings, fallback policy, thread count, flash attention, quantization, and model-loading behaviour from measurements.

### Experiments

- [ ] **E5.1 — Fallback policy**: Compare B0 defaults vs. reduced policy (temp step 0.4 → 0, 0.4, 0.8) vs. single-pass (step 0). Measure fallbacks per clip, time, WER, repetition loops. If Part 1 shows near-zero fallbacks, drop this experiment.

- [ ] **E5.2 — Thread count**: On each phone, test 2, 3, 4, 5, 6 threads (and 8 where available). Pick lowest time; confirm with sustained 3-clip test. Candidate policy: threads = big core count (max 4), still reduced by existing thermal rule. Also test `sched_setaffinity` pinning — drop if results are inconsistent.

- [ ] **E5.3 — Flash attention A/B**: On every phone, measure time and WER with flash attention on vs. off.

- [ ] **E5.4 — Quantization**: For each base model, compare FP16 / q8_0 / q5_1 (and optionally self-quantized q5_0). Measure time, WER, file size, and memory. Adopt quantized file as default only if WER rises ≤0.5 pp AND speed improves ≥10% on both lab phones. Record results per CPU variant.

- [ ] **E5.5 — Model residency**: Measure model load time. If it's a meaningful share of short-clip time, keep model loaded while app is in foreground; release after idle timeout (proposal: 5 min) or on memory trim signal. Respect 3 GB phone limit.

- [ ] **E5.6 — Beam search** (optional 'Accurate' mode): Compare greedy vs. beam sizes 2–5 on small and base tiers. Adopt only if accuracy gain is clear and time fits the Accurate budget.

- [ ] **E5.7 — Hallucination controls**: After VAD (Part 4), test no-speech / logprob / entropy thresholds for hallucination suppression; measure insertions in silent regions.

### Tasks

- [x] Implement abort callback (cancel flag checked inside callback) — stops native work within ~2 seconds. Also required by Part 7.
- [ ] Implement `ThreadPolicy` class: one testable function (inputs: core layout, thermal status, model tier)
- [ ] Record every experiment result in `bench_results/` with the decision made

### Acceptance Gates

- [ ] **G1** — Decision table exists for each device class (ARMv8.0 budget, ARMv8.2 mid-range, flagship): thread policy, flash attention setting, fallback policy, default quantization — each backed by committed result
- [ ] **G2** — No chosen setting raises WER > 0.5 pp on any language in the corpus
- [ ] **G3** — Cancel stops native decoding within ~2 seconds on both lab phones
- [ ] **G4** — Sustained-load test shows no more than a bounded slowdown over 3 consecutive clips

---

## Part 6 — Models, Onboarding & Recommendation

> **Goal:** Each user gets a model that is accurate and fast enough on their phone for their language, with honest expectations.
> **Release timing:** Ships in the same release as Parts 1, 3, 4 & 7 — but built AFTER the Part 2 test pass generates the measured speed table.

### Design Tasks

- [ ] **D1** — Catalog schema v2:
  - Fields: `id`, `family`, `language_scope` (English-only / multilingual / language fine-tune), `quantization`, `size`, `sha256`, pinned URL, measured minimum RAM, measured RTF per CPU class (ARMv8.0 / ARMv8.2 / newer; initially flagged as estimates), accuracy measure (corpus WER or labelled tier)
  - Tier tags: `Fast`, `Balanced`, `Accurate`

- [ ] **D2** — Compute device class at runtime from CPU variant (Part 3), big-core count, total RAM, thermal headroom. Keep it a small number of classes.

- [ ] **D3** — Time budgets (owner decision required): Balanced ≈ 20 s / 30-s clip; Accurate ≈ 45 s / 30-s clip.

- [ ] **D4** — Calibration: prefer lookup table from measured data + ongoing telemetry. Add on-device calibration (short bundled sample after download, or ggml probes from Part 2) only if phones in same class vary too much.

- [ ] **D5** — Onboarding flow:
  - Welcome → Language choice (Your Language + English + More Languages) → Recommendation card → Download
  - Recommendation card: model name, estimated time for a 30-s clip, size in MB, two alternatives (Faster / More accurate with honest trade-off line)
  - Keep existing storage check and cellular warning for files > 100 MB

- [x] **D6** — Compatibility guard:
  - Check active model supports selected language before processing
  - If English-only model + non-English language: prompt user to switch or auto-select

- [ ] **D7** — Defaults: stop auto-selecting `small` / `small.en` on ≥6 GB phones (offer as 'More accurate' until measured). Add official quantized + multilingual-tiny entries after checking official listing and Part 5 results.

- [ ] **D8** — Model cards: replace static speed text with computed estimate; replace 1–5 stars with measured/labelled tier info.

- [ ] **D9** — Hinglish: keep special case but measure the model. Document "under 8 s per 30-s clip" as unverified until tested.

- [ ] **D10** — Model management screen in settings: downloaded models, sizes, delete, default per language.

- [ ] **D11** — Telemetry: record recommended model vs. chosen model, language, any override (from Part 1 events).

### Acceptance Gates

- [ ] **G1** — Unit tests for the recommender with fixtures for: 3 GB ARMv8.0, 6 GB, 8 GB, flagship. No fixture ends with an English-only model for a non-English language.
- [ ] **G2** — On each test phone, the recommended default meets the Balanced time budget on the corpus, as measured.
- [ ] **G3** — Catalog JSON validates: every entry has a pinned URL and matching SHA-256, verified by download test.
- [ ] **G4** — Onboarding flow walked through by ≥3 people who have not seen the app; observed confusion points recorded.
- [x] **G5** — Language mismatch guard blocks English-only + non-English case in an automated test.

---

## Part 7 — Long-Video Guard & Memory

> **Goal:** Warn above 10 minutes, keep memory bounded, make cancelling work.

### Tasks — Phase 1

- [x] Add `VideoLengthGuard` use case:
  - Inputs: duration, selected model, device class
  - Outputs: warn level (NONE / WARN / STRONG_WARN) + time estimate
  - Unit tests for 10-min and 30-min boundaries

- [ ] Wire the warning dialog into every video entry point (gallery picker, share intent, all import paths):
  - Show for any video > 10 minutes, before processing starts
  - Content: video length, estimated time on this phone, "app is built for short videos", choices: Continue / Choose a faster model / Trim first / Cancel
  - Add strings to `strings.xml` for each supported UI language

- [x] Add a second-tier stronger warning above 30 minutes (proposal): mentions battery, heat, memory; suggests trimming.

- [x] Estimate formula: `windows = ceil(clip_seconds / 30)`. `estimate = extraction_estimate + windows × ms_per_window`. Show as ±30% range labelled "about".

- [ ] Add wake lock timeout derived from the estimate, with a ceiling (if none exists).

- [ ] Make audio extraction streaming (decode → downmix → resample → append): source-rate arrays never all held at once. Measure peak memory before and after.

- [ ] Before starting a long job, compare estimated peak memory with available memory; warn if unsafe on the current phone.

- [x] Implement abort path end-to-end: Kotlin cancel flag → JNI atomic → `abort_callback`. Cancelling must: stop native decoding within ~2 s, release model and foreground service, leave no partial project in broken state.

### Tasks — Phase 2 (post-release)

- [ ] Block-based processing: extract and process in blocks (e.g., 5 minutes, cut at VAD silences). Save partial captions after each block.
- [ ] JNI must apply a time offset to each block's timestamps.

### Acceptance Gates

- [ ] **G1** — Dialog appears for videos > 10 min from every entry point; does NOT appear at 9:59
- [ ] **G2** — On 3 GB phone, a 10-minute clip completes without OOM; peak memory recorded
- [ ] **G3** — Estimate is within ~40% of measured time on both phones for 3-min, 10-min, 15-min clips
- [ ] **G4** — Cancel stops native work within ~2 seconds and app returns to usable state
- [ ] **G5 (Phase 2)** — Killing app mid-job and reopening keeps already-completed blocks

---

## Part 8 — Rollout, Regression Gates & Follow-up

### Build, Test, Then Publish Checklist

- [ ] **Step 1** — Tag current code as `B0`; build minimal benchmark harness; measure B0 on Realme 3 and 8 GB phone. **No publishing.**
- [ ] **Step 2** — Build Parts 3 + 4 + 1 + 7 (all locally; nothing published)
- [ ] **Step 3** — First test pass against B0 on both phones + laptop accuracy track. Iterate until gates of Parts 3, 4, 5, 7 pass. Decide Part 5 settings. Results become the measured speed table.
- [ ] **Step 4** — Build catalog v2, onboarding, recommender (Part 6) from measured table. Test against section 6.5 gates.
- [ ] **Step 5** — Publish once:
  - First: Play internal / closed testing track (both lab phones + trusted users)
  - Then: Production with staged rollout (proposal: 20% → 50% → 100%; confirm steps in Play Console)
- [ ] **Step 6 (later)** — Part 7 Phase 2 (block processing) — post-release unless owner decides to include it

### Feature Flags & Kill Switches

| Flag | Controls |
|------|----------|
| `vad_enabled` | VAD + window packing |
| `preprocess_resampler_v2` | Band-limited resampler |
| `preprocess_normalize` | Level normalization |
| `audio_ctx_reduction` | Shorter encoder context for short clips |
| `thread_policy_v2` | New thread selection logic |
| `cpu_variant_override` | Force baseline or ARMv8.2 library |
| `recommender_v2` | New language-first recommendation |
| `long_video_guard` | 10-min warning dialog |

> [!NOTE]
> Confirm whether Firebase Remote Config is already in the project. If not, decide between adding it or using local kill-switch flags (requires an app update to toggle).

### Regression Gates (Every Release)

- [ ] Corpus WER vs. baseline on each phone; RTF vs. baseline (Part 2 rules)
- [ ] Peak memory, crash-free rate, ANR rate
- [ ] Install size; 16 KB alignment check
- [ ] Load test of every model in catalog: load + transcribe a 10-s sample
- [ ] Language mismatch tests
- [ ] Cancellation test
- [ ] 3-clip thermal soak test
- [ ] Word-timing regression guard on annotated clips

### Post-Release Monitoring Dashboards

- [ ] Set up: RTF by model and SoC, outlier list, error and cancel rates, fallback rates, first-run vs. later runs, CPU variant in use
- [ ] Alerts: 90th-percentile RTF > 3 for any model; any rise in native crash rate after a release
- [ ] Compare each release against previous using same metrics

### Rollback Plan

- [ ] Document how to: turn off a feature flag, halt the staged rollout, ship a hotfix
- [ ] Keep baseline native library selectable by override so a bad variant can be disabled quickly

### Documentation Updates

- [x] Correct `PROJECT_DOCUMENTATION.md`: recommendation logic, thread rule, library and JNI names, claimed benchmarking, medium tier
- [x] Add `THIRD_PARTY.md`: whisper.cpp tag + commit, model sources and licences
- [x] Add `BENCHMARKS.md` with latest results
- [x] Add runbook for the benchmark harness

### Reviewer Follow-up

- [ ] Send Play Store developer reply (≤350 chars), asking reviewer for: model, language, first-run, and thermal details by email
- [ ] After fix ships, send results to reviewer by email
- [ ] Keep a note in the Play reply thread listing what was found and fixed

### Program Success Criteria (targets set after Part 2 baseline)

- [ ] For each device class: time for a 30-s clip with the default model ≤ agreed number of seconds
- [ ] Review scenario: a 9-s clip on a flagship-class phone (8–12 GB) completes within an agreed time with default model. Default model must no longer be the heaviest on such phones.
- [ ] Accuracy no worse than baseline on the corpus, for every supported language
- [ ] Telemetry shows no model + SoC combination with 90th-percentile RTF above the agreed alert level

---

*Last updated: November 2, 2026 — source: AutoCaptioner Transcription Investigation PDF (33 pages, Oct 31 2026)*
*Canonical location: `/home/dipankaj/AndroidStudioProjects/AutoCaptioner/TRANSCRIPTION_ROADMAP.md`*
