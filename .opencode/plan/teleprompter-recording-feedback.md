# Plan: Polished Teleprompter + Recording Feedback

## Problem Statement
1. **Teleprompter is primitive**: Tiny 320dp floating window with no countdown, no progress, no mirror mode, no font size control. UI completely disappears when playing. Not competitive with dedicated teleprompter apps (PromptSmart, Teleprompter Premium, etc.)
2. **Recording feedback is weak**: Only a timer and pulsing record button. No visual "on-air" indicator, no audio meter in camera mode, countdown is just a plain number.

## Goal
Create a teleprompter that competes with dedicated teleprompter apps, and give the user clear, professional feedback during recording.

---

## Part 1: Polished Teleprompter Rewrite

### 1A. Camera Teleprompter (`TeleprompterOverlay.kt`) — Full Rewrite

**Current**: 320dp floating draggable window. Fades to invisible when playing. BasicTextField editable during playback.

**New Design**:
- **Full-screen semi-transparent dark scrim** (85% black) over camera feed — user sees themselves through the scrim
- **Large readable text** with adjustable font size (28sp / 36sp / 44sp)
- **Focal point highlight bar** — horizontal line at vertical center where user's eyes should track, AccentCyan glow
- **Bionic reading** (keep existing — bolds first half of each word for faster reading)
- **3-2-1 countdown** before auto-scroll starts — large animated numbers
- **Constant-speed auto-scroll** based on WPM setting (50-300 WPM)
- **Progress indicator** — thin AccentCyan bar at top showing % of script scrolled
- **Word count + time remaining** — subtle text below progress bar
- **Tap to pause/resume** — single tap anywhere toggles scroll
- **Mirror mode toggle** — flips text horizontally for front-facing camera use
- **Font size toggle** — cycles small/medium/large
- **"Back to top" button** — appears when script finishes, scrolls back to start
- **Clean controls when idle**: Bottom bar with Play/Pause, WPM slider, Font size, Mirror, Close
- **Controls fade when playing**: Only a tiny "Tap to pause" hint remains; progress bar stays visible

**Implementation**:
- Replace `BasicTextField` with a `Text` composable (read-only when scrolling, editable when paused with `BasicTextField`)
- Use `LaunchedEffect` with `delay(16)` loop + `scrollState.scrollBy()` for constant-speed smooth scrolling
- Countdown uses `Animatable` for scale + alpha animation per number
- Mirror mode: `Modifier.graphicsLayer { scaleX = if (mirrorMode) -1f else 1f }`
- Progress: `scrollState.value.toFloat() / scrollState.maxValue`

### 1B. Faceless Teleprompter (`FacelessTeleprompterOverlay.kt`) — Full Rewrite

**Current**: Full-screen but primitive. Karaoke highlighting via scroll-fraction (inaccurate). No countdown, no progress, no font control.

**New Design**:
Same features as Camera teleprompter, plus:
- **Karaoke-style word highlighting** — words turn AccentCyan as they scroll past the focal point, unscrolled words dimmed
- **Solid DeepSpace background** (no scrim needed)
- **Larger default text** (36sp)
- **No mirror mode** (not needed for faceless)

**Implementation**:
- Word-position-based karaoke (track which word is at the focal point using layout coordinates, not scroll fraction)
- Same countdown, progress, controls as camera version

### 1C. Shared Teleprompter Components (new file: `TeleprompterComponents.kt`)

Extract shared composables:
- `TeleprompterCountdown` — animated 3-2-1 overlay
- `TeleprompterProgressBar` — thin top bar with percentage
- `TeleprompterControls` — bottom control bar (Play/Pause, WPM, Font, Mirror, Close)
- `TeleprompterFocalBar` — the centered highlight line
- `BionicReadingTransformation` — existing, moved here
- `KaraokeBionicTransformation` — existing, moved here

---

## Part 2: Recording Feedback

### 2A. "ON AIR" Recording Indicator (`SmartRecorderOverlays.kt`)

**New composable: `RecordingIndicator`**
- Pulsing **red border** around the entire screen (2dp, AccentRose, alpha oscillates 0.3↔1.0)
- **"REC" badge** in top-right corner — red dot + "REC" text, pulses in sync with border
- Visible only when `recordingState == RECORDING`
- Uses `infiniteRepeatable` animation

### 2B. Animated Countdown (`SmartRecorderOverlays.kt`)

**Replace current plain number countdown with `AnimatedCountdown`**:
- Large number (120sp) at center
- **Scale-up + fade-out animation** per number (3→2→1)
- Subtle ring/arc that completes around the number
- Dark scrim behind (keep existing 50% alpha)
- After 1, brief "Go!" flash in AccentCyan before recording starts

### 2C. Audio Level Meter During Camera Recording

**Limitation**: CameraX doesn't expose audio levels during video recording. 
**Workaround**: Since audio amplitude is only available in Faceless mode, skip audio meter for camera mode. The "ON AIR" border + REC badge + timer provide sufficient visual feedback.

### 2D. Recording State Transitions

- **Start recording**: Brief scale animation on the record button + red border fades in
- **Stop recording**: Red border fades out + brief "Processing..." chip appears at bottom

---

## Files to Modify

| File | Changes |
|------|---------|
| `TeleprompterOverlay.kt` | **Complete rewrite** — full-screen camera teleprompter with countdown, progress, mirror, font size, tap-to-pause |
| `FacelessTeleprompterOverlay.kt` | **Complete rewrite** — full-screen faceless teleprompter with karaoke, countdown, progress |
| `SmartRecorderOverlays.kt` | Add `RecordingIndicator`, `AnimatedCountdown`, `ProcessingChip` |
| `SmartRecorderControls.kt` | No changes needed |
| `SmartRecorderScreen.kt` | Replace countdown overlay with `AnimatedCountdown`, add `RecordingIndicator`, update teleprompter calls with `onDismiss` |
| `SmartRecorderViewModel.kt` | No state changes needed (teleprompter state is local to the overlay composables) |
| `strings.xml` | Add teleprompter strings: mirror, font size, back to top, script complete, tap to pause, REC, processing |

---

## String Resources to Add

```
recorder_teleprompter_mirror = "Mirror"
recorder_teleprompter_font_size = "Font"
recorder_teleprompter_back_to_top = "Back to top"
recorder_teleprompter_script_complete = "Script complete"
recorder_teleprompter_tap_to_pause = "Tap to pause"
recorder_teleprompter_tap_to_resume = "Tap to resume"
recorder_teleprompter_words_remaining = "%d words left"
recorder_teleprompter_minutes_remaining = "~%d min left"
recorder_rec = "REC"
recorder_processing = "Processing..."
```

---

## Implementation Order

1. Add new string resources to `strings.xml`
2. Rewrite `TeleprompterOverlay.kt` (camera mode — full-screen, polished)
3. Rewrite `FacelessTeleprompterOverlay.kt` (faceless mode — karaoke, polished)
4. Add `RecordingIndicator` + `AnimatedCountdown` to `SmartRecorderOverlays.kt`
5. Update `SmartRecorderScreen.kt` to use new components
6. Build & verify

## Verification
- `./gradlew compileDebugKotlin` — must pass
- Visual: Open recorder → tap Script → verify teleprompter opens full-screen with controls
- Test: Paste text → press Play → verify countdown → verify constant-speed scroll
- Test: Tap to pause → verify controls reappear
- Test: Scroll to end → verify "Back to top" appears
- Test: Start recording → verify red border + REC badge appear
- Test: Countdown before recording → verify animated numbers
