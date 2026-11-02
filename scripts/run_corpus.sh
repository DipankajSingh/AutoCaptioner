#!/usr/bin/env bash
# run_corpus.sh - Laptop automation script for AutoCaptioner benchmark harness
#
# Usage:
#   ./scripts/run_corpus.sh [SERIAL] [MODEL_ID] [ITERATIONS]
# Example:
#   ./scripts/run_corpus.sh RMX3782 tiny.en 3

set -euo pipefail

SERIAL="${1:-}"
MODEL_ID="${2:-tiny.en}"
ITERATIONS="${3:-3}"

ADB_CMD="adb"
if [ -n "$SERIAL" ]; then
    ADB_CMD="adb -s $SERIAL"
fi

PKG="com.dipdev.aiautocaptioner"
DEVICE_CORPUS_DIR="/sdcard/Android/data/${PKG}/files/benchmark/corpus"
DEVICE_RESULTS_DIR="/sdcard/Android/data/${PKG}/files/benchmark/results"
LOCAL_CORPUS_DIR="./bench_corpus"
LOCAL_RESULTS_DIR="./bench_results"

echo "=== AutoCaptioner Benchmark Harness Runner ==="
echo "Target Device: ${SERIAL:-default}"
echo "Model ID: ${MODEL_ID}"
echo "Iterations per clip: ${ITERATIONS}"
echo "==============================================="

mkdir -p "$LOCAL_RESULTS_DIR"

if [ -d "$LOCAL_CORPUS_DIR" ] && [ "$(ls -A "$LOCAL_CORPUS_DIR" 2>/dev/null)" ]; then
    echo "[1/4] Pushing test corpus from ${LOCAL_CORPUS_DIR} to ${DEVICE_CORPUS_DIR}..."
    $ADB_CMD shell mkdir -p "$DEVICE_CORPUS_DIR"
    $ADB_CMD push "$LOCAL_CORPUS_DIR"/* "$DEVICE_CORPUS_DIR/"
else
    echo "[!] Warning: Local ${LOCAL_CORPUS_DIR} directory is empty or missing."
    echo "[!] Place .wav/.mp4 test clips into ${LOCAL_CORPUS_DIR}/ before running full corpus benchmarks."
fi

echo "[2/4] Triggering benchmark broadcast intent on device..."
$ADB_CMD shell am broadcast \
    -a com.dipdev.aiautocaptioner.RUN_BENCHMARK \
    --es model "$MODEL_ID" \
    --ei iterations "$ITERATIONS"

echo "[3/4] Waiting for benchmark to process..."
sleep 5

echo "[4/4] Pulling results from ${DEVICE_RESULTS_DIR} to ${LOCAL_RESULTS_DIR}..."
$ADB_CMD pull "$DEVICE_RESULTS_DIR/." "$LOCAL_RESULTS_DIR/"

echo "=== Done! Results pulled to ${LOCAL_RESULTS_DIR}/ ==="
