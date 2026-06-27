package com.dipdev.aiautocaptioner.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Brand / Primary ──────────────────────────────────────────────────────────
val EmeraldPrimary     = Color(0xFF10B981)
val EmeraldPrimaryDark = Color(0xFF059669)
val EmeraldOnPrimary   = Color(0xFFFFFFFF)

// Legacy accent variants (kept for backwards compatibility in Settings theme picker)
val FlatGreenPrimary = Color(0xFF00FF7F)
val FlatGreenDark    = Color(0xFF008040)
val PurplePrimary    = Color(0xFFA855F7)
val PurpleDark       = Color(0xFF7E22CE)
val BluePrimary      = Color(0xFF3B82F6)
val BlueDark         = Color(0xFF1D4ED8)

// ── Dark theme backgrounds (unchanged) ───────────────────────────────────────
val PremiumBackground    = Color(0xFF09090B)
val PremiumSurface       = Color(0xFF18181B)
val PremiumSurfaceVariant= Color(0xFF27272A)
val GlassSurface         = Color(0x8018181B)

// ── Light theme backgrounds ───────────────────────────────────────────────────
val LightBackground     = Color(0xFFF8FAFC) // cool near-white
val LightSurface        = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5F9)
val LightOutline        = Color(0xFFCBD5E1)

// ── Dark text ─────────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFFAFAFA) // dark theme
val TextSecondary = Color(0xFFA1A1AA) // dark theme
val LightTextPrimary   = Color(0xFF0F172A) // light theme
val LightTextSecondary = Color(0xFF64748B) // light theme

// ── Semantic accent colors ────────────────────────────────────────────────────
// Each represents a screen/feature domain. Used as icon tints, chip fills (15% alpha),
// progress bar fills, left-border strips — never as page backgrounds.
val AccentAmber  = Color(0xFFF59E0B)  // Video Editor
val AccentBlue   = Color(0xFF3B82F6)  // Caption Editor
val AccentViolet = Color(0xFF8B5CF6)  // Style Editor
val AccentRose   = Color(0xFFF43F5E)  // Errors / Delete
val AccentYellow = Color(0xFFEAB308)  // Export
val AccentCyan   = Color(0xFF06B6D4)  // Processing / AI

// ── CompositionLocal: lets each screen provide its accent color ───────────────
// Usage: CompositionLocalProvider(LocalAccentColor provides AccentAmber) { ... }
// Consume: val accent = LocalAccentColor.current
val LocalAccentColor = staticCompositionLocalOf { EmeraldPrimary }

// ── Legacy compatibility ──────────────────────────────────────────────────────
val FlatBlack = Color(0xFF121212)
val FlatGray  = Color(0xFF2D2D2D)
val FlatWhite = Color(0xFFF5F5F5)
