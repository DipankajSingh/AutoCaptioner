# Third-Party Dependencies, Models & Open Source Licenses

This document outlines the third-party native libraries, pre-trained AI models, and open-source licenses used in AutoCaptioner.

---

## 1. Native C++ Libraries

| Library | Version / Tag | Repository / Source | License |
| :--- | :--- | :--- | :--- |
| **whisper.cpp** | `v1.9.5` | [ggml-org/whisper.cpp](https://github.com/ggml-org/whisper.cpp) | MIT License |
| **ggml** | Embedded with `whisper.cpp` | [ggml-org/ggml](https://github.com/ggml-org/ggml) | MIT License |

---

## 2. Pre-trained Whisper GGML Models & Sources

Models loaded via `whisper_models.json` in `app/src/main/assets/whisper_models.json`:

### Official OpenAI Whisper Models (GGML Format)
- **Source:** [ggerganov/whisper.cpp (Hugging Face)](https://huggingface.co/ggerganov/whisper.cpp)
- **License:** MIT / Apache-2.0 (OpenAI Whisper weights)
- **Models:**
  - `tiny.en` (77.7 MB) — English-only Tiny
  - `base.en` (147.9 MB) — English-only Base
  - `small.en` (487.6 MB) — English-only Small
  - `base` (147.9 MB) — Multilingual Base
  - `small` (487.6 MB) — Multilingual Small

### Fine-Tuned & Quantized Community Models
- **Hinglish Swift / Turbo:**
  - `hi.hinglish.swift` (59.7 MB, Q5_1) — [rohitag13/whisper-hindi2hinglish-swift-GGUF](https://huggingface.co/rohitag13/whisper-hindi2hinglish-swift-GGUF)
  - `hi.hinglish.q5` (547.3 MB) & `hi.hinglish.q8` (834.6 MB) — [Marquestra/Whisper-Hindi2Hinglish-Apex-GGML](https://huggingface.co/Marquestra/Whisper-Hindi2Hinglish-Apex-GGML)
- **Spanish:**
  - `es.tiny` (77.7 MB) — [fffffx/whisper-es-tiny-ggml-format](https://huggingface.co/fffffx/whisper-es-tiny-ggml-format)
- **German:**
  - `de.tiny.q8` (43.5 MB, Q8_0) — [Pomni/whisper-tiny-german-1224-ggml-allquants](https://huggingface.co/Pomni/whisper-tiny-german-1224-ggml-allquants)
- **Traditional Chinese / Taiwan:**
  - `zh-TW.base.q5` (59.7 MB, Q5_1) — [Qwe1325/whisper-base-tw-ggml](https://huggingface.co/Qwe1325/whisper-base-tw-ggml)
- **Hindi (Devanagari):**
  - `hi.tiny` (77.7 MB) — [wabisabisocial/whisper-tiny-hindi-ggml](https://huggingface.co/wabisabisocial/whisper-tiny-hindi-ggml)
  - `hi.base` (147.9 MB) — [khidrew/whisper-base-hindi-ggml](https://huggingface.co/khidrew/whisper-base-hindi-ggml)
- **Cantonese:**
  - `yue.base` (147.9 MB) — [wabisabisocial/whisper-base-cantonese-ggml](https://huggingface.co/wabisabisocial/whisper-base-cantonese-ggml)
- **French, Italian, Korean, Russian, Arabic:**
  - `fr.tiny`, `fr.base`, `it.tiny`, `it.base.q5`, `ko.tiny`, `ko.base`, `ru.tiny`, `ru.base`, `ar.base` — Hugging Face GGML repositories (`wabisabisocial`, `LocalAI-io`, `B1uqa`).

---

## 3. Android AndroidX & Jetpack Libraries

- **CameraX:** `androidx.camera:camera-core`, `camera-video`, `camera-view`, `camera-lifecycle` (Apache-2.0)
- **Jetpack Compose & Material 3:** `androidx.compose.material3` (Apache-2.0)
- **Room Database:** `androidx.room` (Apache-2.0)
- **DataStore Preferences:** `androidx.datastore` (Apache-2.0)
- **Firebase:** Analytics, Crashlytics, Performance Monitoring (Apache-2.0)
