# AutoCaptioner Benchmark Results & Harness

This directory stores performance benchmarks, Real-Time Factor (RTF) measurements, and Word Error Rate (WER) outputs for AutoCaptioner.

## Directory Structure
- `bench_corpus/`: Local directory containing input audio/video test clips (`.mp4`, `.wav`, etc.) and reference ground truth `.txt` files.
- `bench_results/`: Stores raw output `results_<timestamp>.json` and `results_<timestamp>.csv` pulled from target devices.
- `scripts/`:
  - `run_corpus.sh`: Laptop automation script to push clips, trigger broadcast intent, and pull results.
  - `calculate_wer.py`: Python script for calculating WER against ground truth reference text.
  - `aggregate_bench.py`: Python script generating Markdown summary tables from raw benchmark JSON outputs.

## How to Run Benchmark on Device

### 1. Push Test Corpus to Device
```bash
adb push ./bench_corpus/* /sdcard/Android/data/com.dipdev.aiautocaptioner/files/benchmark/corpus/
```

### 2. Trigger Benchmark Broadcast via ADB
```bash
adb shell am broadcast \
  -a com.dipdev.aiautocaptioner.RUN_BENCHMARK \
  --es model tiny.en \
  --ei iterations 3
```

Optional intent extras:
- `--es model <modelId>` (e.g., `tiny.en`, `base.en`, `base`)
- `--ei threads <numThreads>` (0 for auto/optimal)
- `--ez flashAttention <true|false>`
- `--ei iterations <num>` (default: 3)
- `--es language <langCode>` (default: "en")

### 3. Pull Results
```bash
adb pull /sdcard/Android/data/com.dipdev.aiautocaptioner/files/benchmark/results/. ./bench_results/
```

### 4. Aggregate & Calculate WER
```bash
python3 scripts/aggregate_bench.py ./bench_results
python3 scripts/calculate_wer.py ./bench_results/results_<timestamp>.json ./bench_corpus
```
