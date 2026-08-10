# Undo/Redo Architecture Fix

## The Core Problem
The user correctly identified a symptom, but the root cause is a combination of bugs we found earlier (Bug 1, Bug 2, Bug 10). 
When a project is loaded, `HistoryManager.reset()` clears the history.
When the first edit (like moving an image) happens, it saves the pre-edit state at index 0.
If the user undoes, it restores index 0. 
If they undo again (because `canUndo` is still true at index 0), `historyIndex` drops to -1, which triggers `onUndoToOriginal()`.
`onUndoToOriginal()` is hardcoded to wipe ALL overlays!
The user perceives this as the app remembering the state from before the image was imported in a previous session, but it's actually just `onUndoToOriginal` nuking the canvas.

## Fix Strategy

### 1. Fix `HistoryManager` state management
Instead of starting with an empty history, `HistoryManager` should be initialized with the state at project load (the "baseline" state).
- Remove `onUndoToOriginal()` completely.
- When `loadProject` finishes, call `historyManager.setBaseline(...)`. This saves the initial DB state as `history[0]`.
- `historyIndex` should start at 0, not -1.
- `canUndo` should be `historyIndex > 0` (you can't undo the baseline).
- `canRedo` should be `historyIndex < history.size - 1`.
- When making an edit (e.g., `UpdateOverlay`), we DON'T need to save the pre-state if we just save the POST-state!
Wait, if we save the POST-state, we need to save it AFTER the edit.
But the current architecture calls `saveState()` BEFORE the edit.
If they call it BEFORE the edit, then `history` grows with pre-states.
Let's stick to their architecture but fix the indices.

### 2. Adjusting `HistoryManager` to work correctly with pre-state saves
If they call `saveState()` BEFORE an edit:
- Baseline is already saved.
- Edit happens. `saveState()` shouldn't do anything if the current UI state matches the last saved state!
Actually, their `saveState` checks: `if (historyIndex >= 0 && history[historyIndex] == snapshot) return`.
So if baseline is `history[0]`, and `saveState` is called before the first edit, it returns (does nothing)!
Then the edit happens. The UI state is now DIFFERENT from `history[0]`.
But `history` only has `history[0]`. It doesn't have the new state!
This means the new state is NEVER in the history list until we call `saveState` again!
This is why `undo()` has that weird logic: `if (historyIndex == history.size - 1) saveState(currentSnapshot)`.

This design is very brittle. We should fix it gracefully.
