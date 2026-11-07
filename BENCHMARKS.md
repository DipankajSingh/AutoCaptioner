# AutoCaptioner — Performance & Accuracy Benchmark Experiments

This document logs all controlled benchmark runs across target hardware platforms (Realme 3 / ARMv8.0 and MediaTek Dimensity 7050 / ARMv8.2).

> **Rule:** One change per experiment, per commit. Before + after numbers required in `bench_results/`.

---

## 🧪 Experiment Results Log

| ID | Experiment | Device | Before (RTF) | After (RTF) | WER Δ | Decision |
|---|-----------|--------|-------------|------------|-------|----------|
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

## 📐 Benchmark Harness Usage

To run on-device benchmarks:
```bash
# Push test corpus audio clips to app's external files directory
adb push corpus/ /sdcard/Android/data/com.dipdev.aiautocaptioner/files/

# Results will be generated in JSON and CSV formats under bench_results/
```
