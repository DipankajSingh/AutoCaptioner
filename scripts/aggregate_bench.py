#!/usr/bin/env python3
"""
aggregate_bench.py - Aggregates JSON benchmark outputs into a Markdown summary table.

Usage:
    python3 scripts/aggregate_bench.py <bench_results_dir>
"""

import sys
import os
import glob
import json
from collections import defaultdict

def main():
    bench_dir = sys.argv[1] if len(sys.argv) > 1 else "./bench_results"

    json_files = glob.glob(os.path.join(bench_dir, "results_*.json"))
    if not json_files:
        print(f"No result JSON files found in {bench_dir}")
        sys.exit(0)

    print("# Benchmark Results Summary\n")
    print("| Timestamp | Device | Model | Clip Name | Duration (s) | Median Whisper (s) | Median RTF |")
    print("|---|---|---|---|---|---|---|")

    for jf in sorted(json_files):
        with open(jf, 'r', encoding='utf-8') as f:
            data = json.load(f)

        ts = data.get("timestamp", "N/A")
        device = data.get("deviceModel", "N/A")
        config = data.get("config", {})
        model_id = config.get("modelId", "N/A")

        # Group runs by clipName
        clip_runs = defaultdict(list)
        clip_durations = {}

        for run in data.get("runs", []):
            clip = run.get("clipName")
            clip_runs[clip].append(run.get("whisperTimeMs", 0))
            clip_durations[clip] = run.get("clipDurationMs", 0)

        for clip, times in clip_runs.items():
            dur_s = clip_durations[clip] / 1000.0
            sorted_times = sorted(times)
            median_time_ms = sorted_times[len(sorted_times) // 2]
            median_time_s = median_time_ms / 1000.0
            median_rtf = median_time_ms / clip_durations[clip] if clip_durations[clip] > 0 else 0.0

            print(f"| {ts} | {device} | {model_id} | {clip} | {dur_s:.1f}s | {median_time_s:.2f}s | {median_rtf:.3f} |")

if __name__ == "__main__":
    main()
