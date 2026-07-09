package com.dipdev.aiautocaptioner.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Background Profiles ───────────────────────────────────────────────────────
// Deep Space
val DeepSpaceBackground = Color(0xFF0F172A)
val DeepSpaceSurface = Color(0xFF1E293B)
val DeepSpaceSurfaceVariant = Color(0xFF334155)

// True Black
val TrueBlackBackground = Color(0xFF000000)
val TrueBlackSurface = Color(0xFF0F0F0F)
val TrueBlackSurfaceVariant = Color(0xFF1F1F1F)

// Matte Dark
val MatteDarkBackground = Color(0xFF18181B)
val MatteDarkSurface = Color(0xFF27272A)
val MatteDarkSurfaceVariant = Color(0xFF3F3F46)

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
val AccentCyan   = Color(0xFF00F0FF)  // Processing / AI Generation
val AccentAmber  = Color(0xFFF59E0B)  // Export / Save
val AccentRose   = Color(0xFFF43F5E)  // Errors / Delete

// Legacy compatibility for simple fallbacks if needed anywhere
val FlatBlack = Color(0xFF121212)
val FlatGray  = Color(0xFF2D2D2D)
val FlatWhite = Color(0xFFF5F5F5)

// ── CompositionLocal: lets each screen provide its accent color ───────────────
val LocalAccentColor = staticCompositionLocalOf { AccentAmber }
