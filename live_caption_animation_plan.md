# Live Caption Animation Polish Plan

## Goal
To elevate the live caption processing UI to a premium, polished level by fixing sudden jarring transitions and improving the scrolling behavior.

## Current Issues Identified
1. **Jarring State Changes**: Currently, when a word transitions from "recent" (last 3 segments) to "older", its font weight and color snap instantly (e.g., from Bold Primary to Normal Gray). This looks glitchy and unpolished.
2. **Scrolling Jitter**: Auto-scrolling the `FlowRow` using `delay(50)` and `scrollState.maxValue` can cause jittery or skipped frames.
3. **Basic Entrance Animation**: Words currently only fade in (`alpha` from 0 to 1). This feels static.

## Proposed Solutions

### 1. Smooth Color Transitions
I will introduce `animateColorAsState` so that when a word stops being the "active" recent word, it gracefully fades from the highlighted primary color into the standard dim surface color, rather than snapping instantly.

### 2. Entrance Animation (Scale + Slide)
When a new word or segment drops into the feed, it shouldn't just fade. I will add a subtle scale-up (e.g., 0.8x to 1.0x) and slight upward vertical shift (`offsetY`), making it feel like the AI is organically typing or dropping the words into the box.

### 3. Font Weight Stability
Animating `FontWeight` directly in Compose isn't supported smoothly without custom variable fonts. To avoid layout jumping when a word goes from `Bold` to `Normal` (which changes its width and causes the whole paragraph to abruptly reflow), I will keep a consistent `FontWeight.Medium` for all text and rely entirely on color luminance, glow, and opacity to denote the "active" words. This guarantees a stable layout.

### 4. Better Auto-Scroll Handling
Instead of a hardcoded 50ms delay, I will bind the scroll effect directly to the `scrollState.maxValue` using a side effect that triggers anytime the max scroll extent grows, ensuring the view elegantly glides down precisely as new text wraps to a new line.

## Requesting Feedback
Does this approach sound like the premium feel you're looking for, or did you have a specific visual style in mind (e.g., a "karaoke" style fill, typewriter effect, etc.)?
