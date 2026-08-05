package com.dipdev.aiautocaptioner.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Background Profiles ───────────────────────────────────────────────────────
// Deep Space (used by the faceless teleprompter overlay)
val DeepSpaceBackground = Color(0xFF0F172A)

// True Black
val TrueBlackBackground = Color(0xFF000000)
val TrueBlackSurface = Color(0xFF0F0F0F)
val TrueBlackSurfaceVariant = Color(0xFF1F1F1F)

// Shared Elements
val GlassSurface = Color(0x8018181B)

// ── Text Colors ───────────────────────────────────────────────────────────────
val TextPrimary = Color(0xFFFAFAFA)
val TextSecondary = Color(0xFFA1A1AA)
val OutlineColor = Color(0xFF3F3F46)

// ── Semantic Accents ──────────────────────────────────────────────────────────
// These replace the old primary "brand" color with specific domain accents
val AccentBlue   = Color(0xFF3B82F6)  // Video / Caption Editor
val AccentViolet = Color(0xFF8B5CF6)  // Style Editor
val AccentAmber  = Color(0xFFF59E0B)  // Export / Save
val AccentRose   = Color(0xFFF43F5E)  // Errors / Delete

// ── CompositionLocal: lets each screen provide its accent color ───────────────
val LocalAccentColor = staticCompositionLocalOf { AccentAmber }
