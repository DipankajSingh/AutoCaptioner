#!/usr/bin/env python3
"""
calculate_wer.py - Calculates Word Error Rate (WER) using Levenshtein distance
between ground-truth reference transcripts (.txt) and benchmark output (results_*.json).

Usage:
    python3 scripts/calculate_wer.py <results_json_file> <reference_dir>
"""

import sys
import os
import json
import re

def normalize_text(text: str) -> list:
    """Normalize text by converting to lowercase and stripping punctuation."""
    text = text.lower()
    text = re.sub(r'[^\w\s]', '', text)
    return text.split()

def levenshtein_distance(ref: list, hyp: list) -> tuple:
    """Computes Levenshtein distance matrix (S, D, I)."""
    r_len = len(ref)
    h_len = len(hyp)
    dp = [[0] * (h_len + 1) for _ in range(r_len + 1)]

    for i in range(r_len + 1):
        dp[i][0] = i
    for j in range(h_len + 1):
        dp[0][j] = j

    for i in range(1, r_len + 1):
        for j in range(1, h_len + 1):
            if ref[i - 1] == hyp[j - 1]:
                dp[i][j] = dp[i - 1][j - 1]
            else:
                dp[i][j] = 1 + min(dp[i - 1][j],      # Deletion
                                   dp[i][j - 1],      # Insertion
                                   dp[i - 1][j - 1])  # Substitution

    total_edit_distance = dp[r_len][h_len]
    wer = (total_edit_distance / r_len) * 100.0 if r_len > 0 else 0.0
    return total_edit_distance, r_len, wer

def main():
    if len(sys.argv) < 3:
        print("Usage: python3 calculate_wer.py <results_json_file> <reference_dir>")
        sys.exit(1)

    results_file = sys.argv[1]
    ref_dir = sys.argv[2]

    if not os.path.exists(results_file):
        print(f"Error: Results file not found: {results_file}")
        sys.exit(1)

    with open(results_file, 'r', encoding='utf-8') as f:
        data = json.load(f)

    device_model = data.get("deviceModel", "Unknown")
    config = data.get("config", {})
    runs = data.get("runs", [])

    print(f"=== WER Calculation Report ===")
    print(f"Device: {device_model}")
    print(f"Model: {config.get('modelId')}")
    print(f"Total runs evaluated: {len(runs)}")
    print("-" * 60)
    print(f"{'Clip Name':<30} | {'Ref Words':<10} | {'Hyp Words':<10} | {'WER (%)':<8}")
    print("-" * 60)

    total_ref_words = 0
    total_edits = 0

    for run in runs:
        clip_name = run.get("clipName", "")
        base_name = os.path.splitext(clip_name)[0]
        ref_file = os.path.join(ref_dir, f"{base_name}.txt")

        if not os.path.exists(ref_file):
            print(f"{clip_name:<30} | {'MISSING REF':<30}")
            continue

        with open(ref_file, 'r', encoding='utf-8') as rf:
            ref_raw = rf.read()

        ref_tokens = normalize_text(ref_raw)
        hyp_tokens = normalize_text(run.get("transcriptText", ""))

        edits, ref_len, wer = levenshtein_distance(ref_tokens, hyp_tokens)
        total_ref_words += ref_len
        total_edits += edits

        print(f"{clip_name:<30} | {ref_len:<10} | {len(hyp_tokens):<10} | {wer:<8.2f}%")

    overall_wer = (total_edits / total_ref_words * 100.0) if total_ref_words > 0 else 0.0
    print("-" * 60)
    print(f"OVERALL WER: {overall_wer:.2f}% (Total Edits: {total_edits}, Total Ref Words: {total_ref_words})")

if __name__ == "__main__":
    main()
