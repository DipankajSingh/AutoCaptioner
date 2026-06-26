package com.dipdev.aiautocaptioner.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TWO_PI = (2.0 * PI).toFloat()

// ═══════════════════════════════════════════════════════════════════════════
// CONFIGURATION — tweak these to adjust the ribbon look & feel
// ═══════════════════════════════════════════════════════════════════════════

// ── Grid density ─────────────────────────────────────────────────────────
/** Number of dots along the ribbon length */
private const val COLS = 90
/** Number of dots across the ribbon width */
private const val ROWS = 22

// ── Ribbon geometry ──────────────────────────────────────────────────────
/** Ribbon half-width as a fraction of screen height */
private const val RIBBON_HALF_WIDTH = 0.21f
/** Spine wave amplitude as a fraction of screen height */
private const val WAVE_AMPLITUDE = 0.10f
/** Spine wave frequency multiplier (PI * this = bends). 1.0 = 1 bend */
private const val WAVE_FREQUENCY = 0f
/** Twist frequency multiplier (PI * this). 1.0 = 1 front-facing band */
private const val TWIST_FREQUENCY = 1f
/** How fast the twist rotates (time multiplier) */
private const val TWIST_SPEED = 1f

// ── Diagonal path ────────────────────────────────────────────────────────
/** Ribbon start Y as a fraction of screen height (negative = above top) */
private const val START_Y_FRACTION = -0.15f
/** Ribbon end Y as a fraction of screen height (>1 = below bottom) */
private const val END_Y_FRACTION = 1.15f
/** Ribbon start X as a fraction of screen width (negative = off left) */
private const val START_X_FRACTION = -0.08f
/** Total X span as a fraction of screen width */
private const val X_SPAN_FRACTION = 1.16f

// ── Animation timing ─────────────────────────────────────────────────────
/** Full animation cycle duration in milliseconds (longer = slower) */
private const val CYCLE_DURATION_MS = 240_000
/** Fade-in duration in milliseconds */
private const val FADE_IN_MS = 2500
/** Per-page vertical shift as a fraction of screen height */
private const val PAGE_SHIFT_AMOUNT = -0.02f

// ── Dot appearance ───────────────────────────────────────────────────────
/** Minimum dot radius in dp (back-face dots) */
private const val DOT_RADIUS_MIN = 0.5f
/** Additional dot radius in dp scaled by depth (front-face dots) */
private const val DOT_RADIUS_DEPTH_SCALE = 1.8f
/** Minimum dot alpha (back-face) */
private const val DOT_ALPHA_MIN = 0.05f
/** Additional alpha scaled by depth (front-face) */
private const val DOT_ALPHA_DEPTH_SCALE = 0.75f
/** Alpha threshold — skip dots dimmer than this */
private const val ALPHA_CUTOFF = 0.015f

// ── Colours resolved from MaterialTheme inside the composable (see below) ──

// ═══════════════════════════════════════════════════════════════════════════

/**
 * A single flowing 3D ribbon surface made of glowing dots.
 * Sweeps diagonally from the top-left to the bottom-right corner.
 * Slow, seamless, infinite animation.
 */
@Composable
fun ParticleWave(
    modifier: Modifier = Modifier,
    currentPage: Int = 0
) {
    // ── Seamless infinite time ────────────────────────────────────────────
    val transition = rememberInfiniteTransition(label = "wave")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI * 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = CYCLE_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    // ── Fade in ──────────────────────────────────────────────────────────
    var fadeTarget by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { fadeTarget = 1f }
    val globalAlpha by animateFloatAsState(
        targetValue = fadeTarget,
        animationSpec = tween(FADE_IN_MS),
        label = "fade"
    )

    // ── Page shift ───────────────────────────────────────────────────────
    val pageShift by animateFloatAsState(
        targetValue = currentPage * PAGE_SHIFT_AMOUNT,
        animationSpec = tween(600),
        label = "page"
    )

    // ── Colours (front → back) — resolved here so Canvas can capture them ──
    val colorFront    = MaterialTheme.colorScheme.primary.copy(alpha = 1f)      // brightest — full primary
    val colorFrontMid = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)   // primary, slightly dim
    val colorMid      = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)   // mid-depth shade
    val colorBackMid  = MaterialTheme.colorScheme.surface                       // deep surface
    val colorBack     = MaterialTheme.colorScheme.background                    // deepest — background

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val d = density

        // Diagonal baseline: top-left corner → bottom-right corner
        val startY = h * (START_Y_FRACTION + pageShift)
        val endY   = h * (END_Y_FRACTION + pageShift)

        for (col in 0 until COLS) {
            val u = col.toFloat() / (COLS - 1).toFloat()

            // ── Spine: diagonal line + wave bend ─────────────────────────
            val baseY = startY + (endY - startY) * u
            val spineY = baseY + sin(u * PI.toFloat() * WAVE_FREQUENCY + time) * h * WAVE_AMPLITUDE

            val x = w * START_X_FRACTION + (w * X_SPAN_FRACTION) * u

            // ── Twist ────────────────────────────────────────────────────
            val twistAngle = u * PI.toFloat() * TWIST_FREQUENCY + time * TWIST_SPEED

            // ── Tangent for perpendicular direction ──────────────────────
            val du = 0.005f
            val uNext = (u + du).coerceAtMost(1f)
            val baseYNext = startY + (endY - startY) * uNext
            val spineYNext = baseYNext + sin(uNext * PI.toFloat() * WAVE_FREQUENCY + time) * h * WAVE_AMPLITUDE
            val xNext = w * START_X_FRACTION + (w * X_SPAN_FRACTION) * uNext

            val tdx = xNext - x
            val tdy = spineYNext - spineY
            val tLen = kotlin.math.sqrt(tdx * tdx + tdy * tdy).coerceAtLeast(1f)
            val perpX = -tdy / tLen
            val perpY = tdx / tLen

            val ribbonHalf = h * RIBBON_HALF_WIDTH

            for (row in 0 until ROWS) {
                val v = row.toFloat() / (ROWS - 1).toFloat()
                val vCentered = v * 2f - 1f

                val projectedOffset = vCentered * cos(twistAngle)
                val depth = vCentered * sin(twistAngle)
                val depthNorm = (depth + 1f) * 0.5f

                val offset = projectedOffset * ribbonHalf
                val px = x + perpX * offset
                val py = spineY + perpY * offset

                if (px < -15f || px > w + 15f || py < -15f || py > h + 15f) continue

                val radius = (DOT_RADIUS_MIN + depthNorm * DOT_RADIUS_DEPTH_SCALE) * d
                val baseAlpha = DOT_ALPHA_MIN + depthNorm * DOT_ALPHA_DEPTH_SCALE
                val edgeFade = 1f - (abs(u - 0.5f) * 2f).let { it * it * it }
                val widthFade = 1f - abs(vCentered).let { it * it }
                val alpha = (baseAlpha * edgeFade * widthFade * globalAlpha).coerceIn(0f, 1f)
                if (alpha < ALPHA_CUTOFF) continue

                val color = when {
                    depthNorm > 0.75f -> lerpColor(colorFrontMid, colorFront, (depthNorm - 0.75f) / 0.25f)
                    depthNorm > 0.5f  -> lerpColor(colorMid, colorFrontMid, (depthNorm - 0.5f) / 0.25f)
                    depthNorm > 0.25f -> lerpColor(colorBackMid, colorMid, (depthNorm - 0.25f) / 0.25f)
                    else -> lerpColor(colorBack, colorBackMid, depthNorm / 0.25f)
                }

                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(px, py)
                )
            }
        }
    }
}

private fun lerpColor(a: Color, b: Color, f: Float): Color {
    val t = f.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f
    )
}
