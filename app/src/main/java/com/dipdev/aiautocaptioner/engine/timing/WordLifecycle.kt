package com.dipdev.aiautocaptioner.engine.timing

/**
 * Rich lifecycle state for a timed word.
 *
 * Replaces the flat isActive/isPast booleans with an explicit state machine
 * that drives both timing logic and rendering decisions.
 */
enum class WordLifecycle {
    /** Not yet visible — before its enter animation window. */
    UPCOMING,
    /** Within its enter animation window (startTimeMs - animMs .. startTimeMs). */
    ENTERING,
    /** Currently being spoken (startTimeMs .. endTimeMs). */
    ACTIVE,
    /** Spoken, within its exit animation window (endTimeMs .. endTimeMs + animMs). */
    EXITING,
    /** Fully exited — not rendered. */
    REMOVED
}
