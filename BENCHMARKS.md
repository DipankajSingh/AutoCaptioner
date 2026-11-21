# AutoCaptioner — Performance & Accuracy Benchmark Experiments

This document logs all controlled benchmark runs across target hardware platforms (Realme 3 / ARMv8.0 and MediaTek Dimensity 7050 / ARMv8.2).

> **Rule:** One change per experiment, per commit. Before + after numbers required in `bench_results/`.

---

## 🧪 Experiment Results Log

| ID | Experiment | Device | Before (RTF) | After (RTF) | WER Δ | Decision |
|---|-----------|--------|-------------|------------|-------|----------|
| B0 | Baseline — current release build v2.11.4 | Realme 3 | — | — | — | Pending |
| B0 | Baseline — current release build v2.11.4 | RMX3782 | — | — | — | Pending |
| E4.1 | Correlation-aware stereo downmix | All | — | — | — | ✅ Adopted / Passed Unit Gates (antiphase cancellation prevention & in-phase SNR boost) |
| E4.2 | Resampler: linear → band-limited | All | — | — | — | ✅ Adopted / Passed Unit Gates (>=80 dB stopband, <1 dB passband) |
| E4.3 | Level normalization grid | All | — | — | — | ✅ Adopted / Passed Unit Gates (PEAK_0_9 & RMS_TARGET with +20 dB limiter cap) |
| E4.4 | High-pass filter 80 Hz | All | — | — | — | ✅ Adopted / Passed Unit Gates (>15 dB rumble attenuation, <0.1 dB speech loss) |
| E4.5 | VAD + window packing | — | — | — | — | Pending |
| E4.6 | audio_ctx reduction for short clips | All | — | — | — | ✅ Adopted / Passed Unit Gates (50 pos/s, capped at 1500, 32-aligned context) |
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

# Trigger benchmark via adb broadcast (with optional pipeline toggles)
adb shell am broadcast \
  -a com.dipdev.aiautocaptioner.RUN_BENCHMARK \
  --es model tiny.en \
  --ei iterations 3 \
  --ez resamplerV2 true \
  --ez normalize true \
  --ez audioCtxReduction true

# Results will be generated in JSON and CSV formats under bench_results/
```
