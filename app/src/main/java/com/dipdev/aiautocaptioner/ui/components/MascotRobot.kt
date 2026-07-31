package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentBlue
import kotlin.math.cos
import kotlin.math.sin

// ─────────────────────────────────────────────────────────────────────────────
// MascotMode — drives mascot expression and animation per app context
// ─────────────────────────────────────────────────────────────────────────────

internal sealed class MascotMode {
    /** At rest — dialogs, idle screens */
    object Idle        : MascotMode()
    /** Cautious — confirmation dialogs */
    object Warning     : MascotMode()
    /** Alarmed — errors, destructive actions */
    object Error       : MascotMode()
    /** Positive — success, confirmations */
    object Success     : MascotMode()
    /** Transcribing audio — caption lines become a live equalizer */
    object Listening   : MascotMode()
    /** Loading model / AI warmup — orbital eyes, spinning triangle */
    object Thinking    : MascotMode()
    /** Fetching model from network — arrow falls from antenna */
    object Downloading : MascotMode()
    /** Rendering video — scan line sweeps screen, wipe captions */
    object Exporting   : MascotMode()
    /** Everything done — full bounce, sparks, lines solid */
    object Celebrating : MascotMode()
}

// ─────────────────────────────────────────────────────────────────────────────
// MascotRobot — pixel-accurate Canvas recreation of the TV-robot logo
// ─────────────────────────────────────────────────────────────────────────────
//
// All geometry is derived from foreground_icon.svg (108 × 108 viewBox).
// Animation layer is fully driven by MascotMode.
// All animated float values are read inside onDrawBehind — zero layout
// invalidations per animation frame.
//
// Body gradient is always amber→rose regardless of mode (brand identity).
// Contextual colour (AccentBlue etc.) is carried only by new mode elements.
//
@Composable
internal fun MascotRobot(
    mode: MascotMode,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "mascot")

    // ── Antenna glow ─────────────────────────────────────────────────────────
    val antennaGlow by inf.animateFloat(
        initialValue = 0.65f,
        targetValue  = 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mode) {
                    MascotMode.Error       -> 260
                    MascotMode.Celebrating -> 180
                    MascotMode.Warning     -> 560
                    MascotMode.Listening   -> 390
                    MascotMode.Exporting   -> 900
                    else                   -> 1700
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "antennaGlow"
    )

    // ── Body breathe — calm states only ──────────────────────────────────────
    val bodyBreathe by inf.animateFloat(
        initialValue = 1.000f,
        targetValue  = when (mode) {
            MascotMode.Idle,
            MascotMode.Thinking,
            MascotMode.Downloading -> 1.018f
            else                   -> 1.000f
        },
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bodyBreathe"
    )

    // ── Body sway X — Idle only ──────────────────────────────────────────────
    val bodySwayX by inf.animateFloat(
        initialValue = 0f,
        targetValue  = if (mode is MascotMode.Idle) 1.5f else 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bodySwayX"
    )

    // ── Body shake X — Error ─────────────────────────────────────────────────
    val bodyShakeX by inf.animateFloat(
        initialValue =  0f,
        targetValue  = if (mode is MascotMode.Error) 4f else 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(75, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bodyShakeX"
    )

    // ── Body vertical bounce — Success / Celebrating ─────────────────────────
    // FastOutSlowInEasing reversed on the way down → natural gravity feel
    val bodyBounceY by inf.animateFloat(
        initialValue = 0f,
        targetValue  = when (mode) {
            MascotMode.Success     ->  -7f
            MascotMode.Celebrating -> -11f
            else                   ->   0f
        },
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bodyBounceY"
    )

    // ── Play triangle scale — Success / Celebrating ───────────────────────────
    val playScale by inf.animateFloat(
        initialValue = 1.00f,
        targetValue  = when (mode) {
            MascotMode.Success     -> 1.35f
            MascotMode.Celebrating -> 1.55f
            else                   -> 1.00f
        },
        animationSpec = infiniteRepeatable(
            animation  = tween(560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playScale"
    )

    // ── Play triangle rotation — Thinking ────────────────────────────────────
    val triangleRotation by inf.animateFloat(
        initialValue = 0f,
        targetValue  = 360f,
        animationSpec = infiniteRepeatable(
            animation  = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "triangleRotation"
    )

    // ── Caption jitter — Warning / Error ─────────────────────────────────────
    val captionJitter by inf.animateFloat(
        initialValue = 0f,
        targetValue  = when (mode) {
            MascotMode.Error   -> 3.8f
            MascotMode.Warning -> 1.5f
            else               -> 0f
        },
        animationSpec = infiniteRepeatable(
            animation  = tween(310, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "captionJitter"
    )

    // ── Equalizer bars — Listening (staggered independent frequencies) ────────
    val eq1 by inf.animateFloat(initialValue = 0.30f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(480, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq1")
    val eq2 by inf.animateFloat(initialValue = 0.55f, targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(640, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq2")
    val eq3 by inf.animateFloat(initialValue = 0.40f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq3")
    // Amber line: fastest / highest amplitude — the "beat"
    val eq4 by inf.animateFloat(initialValue = 0.20f, targetValue = 1.00f,
        animationSpec = infiniteRepeatable(tween(390, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "eq4")

    // ── Orbit angle — Thinking (eye circular drift, sin/cos) ─────────────────
    val orbitAngle by inf.animateFloat(
        initialValue = 0f,
        targetValue  = (2f * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbitAngle"
    )

    // ── Think phase — Thinking (sequential caption fade, 0..4) ───────────────
    val thinkPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue  = 4f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinkPhase"
    )

    // ── Download progress — Downloading (arrow fall 0→1, restart) ────────────
    val downloadY by inf.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(900, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "downloadY"
    )

    // ── Scan line — Exporting (top→bottom sweep 0→1, restart) ────────────────
    val scanLineY by inf.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLineY"
    )

    // ── Film blink — Exporting (alternating perforation alpha) ───────────────
    val filmBlink by inf.animateFloat(
        initialValue = 0.15f,
        targetValue  = 1.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "filmBlink"
    )

    // ── Export wipe — Exporting (caption staggered wipe 0→1) ─────────────────
    val exportWipe by inf.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "exportWipe"
    )

    // ── Spark phase — Success / Celebrating (0→1, restart) ───────────────────
    val sparkPhase by inf.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkPhase"
    )

    // ── Eye tilt — spring-driven by mode ─────────────────────────────────────
    val eyeTiltDeg by animateFloatAsState(
        targetValue = when (mode) {
            MascotMode.Error                       -> 28f
            MascotMode.Warning                     -> 15f
            MascotMode.Success, MascotMode.Celebrating -> -8f
            MascotMode.Downloading                 ->  8f
            else                                   ->  0f
        },
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "eyeTilt"
    )

    // ── Eye height scale — spring-driven ─────────────────────────────────────
    val eyeHeightScale by animateFloatAsState(
        targetValue = when (mode) {
            MascotMode.Listening   -> 1.30f  // wide-eyed attention
            MascotMode.Celebrating -> 0.18f  // happy squint
            MascotMode.Success     -> 0.55f  // soft squint
            else                   -> 1.00f
        },
        animationSpec = spring(dampingRatio = 0.50f, stiffness = 260f),
        label = "eyeHeightScale"
    )

    // ── Warning body lean — squash/stretch toward viewer ─────────────────────
    val bodyLeanX by animateFloatAsState(
        targetValue    = if (mode is MascotMode.Warning) 1.03f else 1.0f,
        animationSpec  = spring(dampingRatio = 0.7f, stiffness = 200f),
        label          = "bodyLeanX"
    )
    val bodyLeanY by animateFloatAsState(
        targetValue    = if (mode is MascotMode.Warning) 0.97f else 1.0f,
        animationSpec  = spring(dampingRatio = 0.7f, stiffness = 200f),
        label          = "bodyLeanY"
    )

    // Composite transform values (computed in composition, captured in closure)
    val finalScaleX = bodyLeanX * bodyBreathe
    val finalScaleY = bodyLeanY * bodyBreathe
    val finalTransX = if (mode is MascotMode.Error) bodyShakeX else bodySwayX * 0.4f
    val finalTransY = bodyBounceY

    // Gradient — always amber→rose, brand identity is constant
    val gradTop    = AccentAmber.copy(alpha = antennaGlow)
    val gradBottom = Color(0xFFF5000B).copy(alpha = antennaGlow)

    Box(
        modifier = modifier.drawWithCache {
            val W  = size.width
            val H  = size.height
            val sx = W / 108f
            val sy = H / 108f

            fun px(v: Float) = v * sx
            fun py(v: Float) = v * sy
            fun pw(v: Float) = v * sx
            fun ph(v: Float) = v * sy

            val earColor    = Color(0xFFFFA454)
            val bezelColor  = Color(0xFF733300)
            val screenColor = Color(0xFF00000B)
            val faceWhite   = Color(0xFFFAFAFA)
            val playWhite   = Color(0xFFFBFBFB)
            val amberLine   = AccentAmber

            onDrawBehind {
                translate(left = finalTransX * sx, top = finalTransY * sy) {
                    scale(
                        scaleX = finalScaleX,
                        scaleY = finalScaleY,
                        pivot  = Offset(W / 2f, H / 2f)
                    ) {

                        // ════════════════════════════════════════════════════
                        // 1. EARS — peach rounded rects flanking the head
                        // ════════════════════════════════════════════════════
                        val earW  = pw(15.24f)
                        val earH  = ph(21.86f)
                        val earCR = CornerRadius(pw(8.04f), ph(6.93f))
                        val earY  = py(43.04f)

                        drawRoundRect(color = earColor, topLeft = Offset(px(70.84f), earY), size = Size(earW, earH), cornerRadius = earCR)
                        drawRoundRect(color = earColor, topLeft = Offset(px(22.42f), earY), size = Size(earW, earH), cornerRadius = earCR)

                        // ════════════════════════════════════════════════════
                        // 2. GRADIENT BODY — antenna ball, stem, head shell
                        // ════════════════════════════════════════════════════
                        val gradBrush = Brush.verticalGradient(
                            0.14f to gradTop,
                            1.00f to gradBottom,
                            startY = py(20.44f),
                            endY   = py(78.12f)
                        )
                        drawCircle(brush = gradBrush, radius = pw(4.8f), center = Offset(px(54.1f), py(24.0f)))
                        drawRoundRect(brush = gradBrush, topLeft = Offset(px(51.9f), py(27.5f)), size = Size(pw(4.4f), ph(8.5f)), cornerRadius = CornerRadius(pw(2f), ph(2f)))
                        drawRoundRect(brush = gradBrush, topLeft = Offset(px(26.24f), py(34.0f)), size = Size(pw(55.95f), ph(44.12f)), cornerRadius = CornerRadius(pw(11.5f), ph(11.5f)))

                        // ── Downloading: chevron arrow falls from above antenna ──
                        if (mode is MascotMode.Downloading) {
                            drawDownloadArrow(
                                cx       = px(54.1f),
                                baseY    = py(14f),
                                progress = downloadY,
                                sz       = pw(5.2f),
                                color    = AccentBlue
                            )
                        }

                        // ════════════════════════════════════════════════════
                        // 3. BEZEL — dark-sienna TV frame
                        // ════════════════════════════════════════════════════
                        drawRoundRect(
                            color        = bezelColor,
                            topLeft      = Offset(px(29.39f), py(33.80f)),
                            size         = Size(pw(49.61f), ph(41.10f)),
                            cornerRadius = CornerRadius(pw(8.89f), ph(11.25f))
                        )

                        // ════════════════════════════════════════════════════
                        // 4. SCREEN + EXPORTING scan line
                        // ════════════════════════════════════════════════════
                        val scrL = px(32.66f);  val scrT = py(37.28f)
                        val scrW = pw(43.05f);  val scrH = ph(34.13f)

                        drawRoundRect(
                            color        = screenColor,
                            topLeft      = Offset(scrL, scrT),
                            size         = Size(scrW, scrH),
                            cornerRadius = CornerRadius(pw(7.99f), ph(9.69f))
                        )

                        if (mode is MascotMode.Exporting) {
                            val scanY = scrT + scanLineY * scrH
                            clipRect(left = scrL, top = scrT, right = scrL + scrW, bottom = scrT + scrH) {
                                drawLine(
                                    color       = amberLine.copy(alpha = 0.72f),
                                    start       = Offset(scrL + pw(2.5f), scanY),
                                    end         = Offset(scrL + scrW - pw(2.5f), scanY),
                                    strokeWidth = ph(1.5f)
                                )
                            }
                        }

                        // ════════════════════════════════════════════════════
                        // 5. EYES
                        // ════════════════════════════════════════════════════
                        val eyeHalfW = pw(1.49f)
                        val eyeHalfH = ph(3.99f) * eyeHeightScale
                        val eyeCR    = CornerRadius(pw(1.49f), pw(1.49f))

                        // Thinking: each eye drifts in an independent elliptical orbit
                        val orbitX = if (mode is MascotMode.Thinking) pw(1.5f) * sin(orbitAngle) else 0f
                        val orbitY = if (mode is MascotMode.Thinking) ph(1.0f) * cos(orbitAngle) else 0f
                        // Exporting: both eyes drift slightly right (watching the render)
                        val lookX  = if (mode is MascotMode.Exporting)   pw(1.5f) else 0f
                        // Downloading: both eyes drift slightly down (watching the fetch)
                        val lookY  = if (mode is MascotMode.Downloading) ph(1.5f) else 0f

                        val lCx = px(43.39f) + orbitX + lookX
                        val lCy = py(46.27f) + orbitY + lookY
                        val rCx = px(65.35f) - orbitX + lookX   // right eye mirrors orbit
                        val rCy = py(46.27f) - orbitY + lookY

                        rotate(degrees = -eyeTiltDeg, pivot = Offset(lCx, lCy)) {
                            drawRoundRect(color = faceWhite, topLeft = Offset(lCx - eyeHalfW, lCy - eyeHalfH), size = Size(eyeHalfW * 2f, eyeHalfH * 2f), cornerRadius = eyeCR)
                        }
                        rotate(degrees = eyeTiltDeg, pivot = Offset(rCx, rCy)) {
                            drawRoundRect(color = faceWhite, topLeft = Offset(rCx - eyeHalfW, rCy - eyeHalfH), size = Size(eyeHalfW * 2f, eyeHalfH * 2f), cornerRadius = eyeCR)
                        }

                        // ════════════════════════════════════════════════════
                        // 6. PLAY TRIANGLE / FILM FRAME / SPINNING LOADER
                        // ════════════════════════════════════════════════════
                        val triCx = px(53.94f);  val triCy = py(47.28f)

                        when (mode) {
                            is MascotMode.Exporting -> {
                                // Two film perforations blinking in alternation
                                val pW = pw(3.5f);  val pH = ph(5.2f)
                                val pY = triCy - pH / 2f
                                drawRoundRect(
                                    color        = faceWhite.copy(alpha = filmBlink),
                                    topLeft      = Offset(triCx - pw(5.2f), pY),
                                    size         = Size(pW, pH),
                                    cornerRadius = CornerRadius(pw(0.8f), pw(0.8f))
                                )
                                drawRoundRect(
                                    color        = faceWhite.copy(alpha = (1f - filmBlink + 0.15f).coerceIn(0f, 1f)),
                                    topLeft      = Offset(triCx + pw(1.7f), pY),
                                    size         = Size(pW, pH),
                                    cornerRadius = CornerRadius(pw(0.8f), pw(0.8f))
                                )
                            }
                            is MascotMode.Thinking -> {
                                // Slowly spinning triangle — like an internal loading spinner
                                rotate(degrees = triangleRotation, pivot = Offset(triCx, triCy)) {
                                    val triLx = pw(3.04f);  val triRx = pw(3.03f);  val triH = ph(3.53f)
                                    drawPath(Path().apply {
                                        moveTo(triCx - triLx, triCy - triH)
                                        lineTo(triCx + triRx, triCy)
                                        lineTo(triCx - triLx, triCy + triH)
                                        close()
                                    }, color = playWhite)
                                }
                            }
                            else -> {
                                // Standard play triangle, scale-animated for bouncy states
                                val triLx = pw(3.04f) * playScale
                                val triRx = pw(3.03f) * playScale
                                val triH  = ph(3.53f) * playScale
                                drawPath(Path().apply {
                                    moveTo(triCx - triLx, triCy - triH)
                                    lineTo(triCx + triRx, triCy)
                                    lineTo(triCx - triLx, triCy + triH)
                                    close()
                                }, color = playWhite)
                            }
                        }

                        // ════════════════════════════════════════════════════
                        // 7. CAPTION LINES — mouth area, mode-driven
                        // ════════════════════════════════════════════════════
                        val lCR1 = CornerRadius(ph(1.01f), ph(1.01f))
                        val lCR3 = CornerRadius(ph(0.92f), ph(0.92f))
                        val j    = pw(captionJitter)

                        when (mode) {
                            is MascotMode.Listening -> {
                                // Equalizer: each line independently driven
                                drawRoundRect(color = amberLine,                                topLeft = Offset(px(58.42f), py(56.88f)), size = Size(pw(10.37f) * eq4, ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = 0.55f + 0.45f * eq1), topLeft = Offset(px(37.34f), py(57.19f)), size = Size(pw(18.91f) * eq1, ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = 0.55f + 0.45f * eq2), topLeft = Offset(px(37.51f), py(60.83f)), size = Size(pw(31.96f) * eq2, ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = 0.55f + 0.45f * eq3), topLeft = Offset(px(40.18f), py(64.51f)), size = Size(pw(25.02f) * eq3, ph(1.84f)), cornerRadius = lCR3)
                            }

                            is MascotMode.Thinking -> {
                                // Sequential fade: lines appear one by one like "..." thinking
                                val p = thinkPhase
                                fun seqAlpha(start: Float): Float {
                                    val window = 1.2f
                                    return when {
                                        p < start           -> 0f
                                        p < start + 0.4f    -> (p - start) / 0.4f
                                        p < start + window  -> 1f
                                        p < start + window + 0.4f -> 1f - (p - start - window) / 0.4f
                                        else                -> 0f
                                    }.coerceIn(0f, 1f)
                                }
                                drawRoundRect(color = faceWhite.copy(alpha = seqAlpha(0.0f) * 0.85f), topLeft = Offset(px(37.34f), py(57.19f)), size = Size(pw(18.91f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = seqAlpha(0.8f) * 0.85f), topLeft = Offset(px(37.51f), py(60.83f)), size = Size(pw(31.96f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = seqAlpha(1.6f) * 0.85f), topLeft = Offset(px(40.18f), py(64.51f)), size = Size(pw(25.02f), ph(1.84f)), cornerRadius = lCR3)
                                drawRoundRect(color = amberLine.copy(alpha  = seqAlpha(2.4f) * 0.90f), topLeft = Offset(px(58.42f), py(56.88f)), size = Size(pw(10.37f), ph(2.02f)), cornerRadius = lCR1)
                            }

                            is MascotMode.Downloading -> {
                                // Left-to-right progressive fill, driven by downloadY
                                val p = downloadY
                                drawRoundRect(color = amberLine.copy(alpha = 0.9f), topLeft = Offset(px(58.42f), py(56.88f)), size = Size(pw(10.37f) * p, ph(2.02f)), cornerRadius = lCR1)
                                if (p > 0.35f) drawRoundRect(color = faceWhite.copy(alpha = 0.7f), topLeft = Offset(px(37.34f), py(57.19f)), size = Size(pw(18.91f) * ((p - 0.35f) / 0.65f).coerceIn(0f, 1f), ph(2.02f)), cornerRadius = lCR1)
                                if (p > 0.65f) drawRoundRect(color = faceWhite.copy(alpha = 0.5f), topLeft = Offset(px(37.51f), py(60.83f)), size = Size(pw(31.96f) * ((p - 0.65f) / 0.35f).coerceIn(0f, 1f), ph(2.02f)), cornerRadius = lCR1)
                            }

                            is MascotMode.Exporting -> {
                                // Staggered wipe — each line fills in a quarter-phase window
                                fun wipeW(from: Float, until: Float, maxW: Float): Float =
                                    maxW * ((exportWipe - from) / (until - from)).coerceIn(0f, 1f)
                                drawRoundRect(color = amberLine,                     topLeft = Offset(px(58.42f), py(56.88f)), size = Size(wipeW(0f, 0.25f, pw(10.37f)), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = 0.85f), topLeft = Offset(px(37.34f), py(57.19f)), size = Size(wipeW(0.25f, 0.5f, pw(18.91f)), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = 0.65f), topLeft = Offset(px(37.51f), py(60.83f)), size = Size(wipeW(0.5f, 0.75f, pw(31.96f)), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite.copy(alpha = 0.45f), topLeft = Offset(px(40.18f), py(64.51f)), size = Size(wipeW(0.75f, 1f, pw(25.02f)), ph(1.84f)), cornerRadius = lCR3)
                            }

                            is MascotMode.Celebrating -> {
                                // All lines full-width and solid — celebration finish
                                drawRoundRect(color = amberLine, topLeft = Offset(px(58.42f), py(56.88f)), size = Size(pw(10.37f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite, topLeft = Offset(px(37.34f), py(57.19f)), size = Size(pw(18.91f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite, topLeft = Offset(px(37.51f), py(60.83f)), size = Size(pw(31.96f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite, topLeft = Offset(px(40.18f), py(64.51f)), size = Size(pw(25.02f), ph(1.84f)), cornerRadius = lCR3)
                            }

                            else -> {
                                // Standard lines with optional jitter (Warning, Error, Idle, etc.)
                                drawRoundRect(color = amberLine, topLeft = Offset(px(58.42f) - j,        py(56.88f)), size = Size(pw(10.37f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite, topLeft = Offset(px(37.34f) + j,        py(57.19f)), size = Size(pw(18.91f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite, topLeft = Offset(px(37.51f) - j,        py(60.83f)), size = Size(pw(31.96f), ph(2.02f)), cornerRadius = lCR1)
                                drawRoundRect(color = faceWhite, topLeft = Offset(px(40.18f) + j * 0.4f, py(64.51f)), size = Size(pw(25.02f), ph(1.84f)), cornerRadius = lCR3)
                            }
                        }

                        // ════════════════════════════════════════════════════
                        // 8. STAR SPARKS — Success / Celebrating
                        // ════════════════════════════════════════════════════
                        if (mode is MascotMode.Success || mode is MascotMode.Celebrating) {

                            fun phaseAlpha(offset: Float): Float {
                                val p = (sparkPhase + offset) % 1f
                                return when {
                                    p < 0.25f -> p / 0.25f
                                    p < 0.65f -> 1f
                                    else      -> 1f - (p - 0.65f) / 0.35f
                                }.coerceIn(0f, 1f)
                            }
                            fun phaseSize(offset: Float): Float {
                                val p = (sparkPhase + offset) % 1f
                                return if (p < 0.25f) p / 0.25f else 1f
                            }

                            drawStarSpark(Offset(px(27f), py(30f)), pw(3.8f) * phaseSize(0.00f), amberLine.copy(alpha = phaseAlpha(0.00f) * 0.90f))
                            drawStarSpark(Offset(px(81f), py(30f)), pw(3.0f) * phaseSize(0.50f), amberLine.copy(alpha = phaseAlpha(0.50f) * 0.75f))

                            if (mode is MascotMode.Celebrating) {
                                drawStarSpark(Offset(px(21f), py(53f)), pw(2.4f) * phaseSize(0.25f), amberLine.copy(alpha = phaseAlpha(0.25f) * 0.60f))
                                drawStarSpark(Offset(px(87f), py(53f)), pw(2.4f) * phaseSize(0.75f), amberLine.copy(alpha = phaseAlpha(0.75f) * 0.60f))
                            }
                        }

                    } // end scale
                } // end translate
            } // end onDrawBehind
        } // end drawWithCache
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DialogType adapter — existing callers remain untouched
// ─────────────────────────────────────────────────────────────────────────────
@Composable
internal fun MascotRobot(
    type: DialogType,
    modifier: Modifier = Modifier
) {
    MascotRobot(
        mode = when (type) {
            DialogType.INFO    -> MascotMode.Idle
            DialogType.WARNING -> MascotMode.Warning
            DialogType.ERROR   -> MascotMode.Error
            DialogType.SUCCESS -> MascotMode.Success
        },
        modifier = modifier
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// DrawScope helpers — pure Canvas, no allocations at draw time
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Animated download chevron: falls downward from [baseY] and fades out as
 * [progress] goes 0→1, then the caller restarts the driver for a loop.
 */
private fun DrawScope.drawDownloadArrow(
    cx: Float, baseY: Float, progress: Float, sz: Float, color: Color
) {
    val alpha  = (1f - progress * 1.1f).coerceIn(0f, 1f)
    val yOff   = progress * sz * 2.4f
    val y      = baseY + yOff
    val halfW  = sz * 0.56f
    val stemHW = sz * 0.15f
    val stemH  = sz * 0.52f

    val path = Path().apply {
        // Downward arrowhead
        moveTo(cx - halfW, y)
        lineTo(cx + halfW, y)
        lineTo(cx,         y + sz * 0.72f)
        close()
        // Vertical stem above arrowhead
        moveTo(cx - stemHW, y - stemH)
        lineTo(cx + stemHW, y - stemH)
        lineTo(cx + stemHW, y)
        lineTo(cx - stemHW, y)
        close()
    }
    drawPath(path = path, color = color.copy(alpha = alpha))
}

/**
 * 4-pointed star spark — used for Success and Celebrating states.
 * Drawn entirely with Path; no Bitmap allocation.
 */
private fun DrawScope.drawStarSpark(center: Offset, radius: Float, color: Color) {
    if (radius <= 0f) return
    val inner  = radius * 0.36f
    val points = 4
    val path   = Path()
    for (i in 0 until points * 2) {
        val angle = (i * Math.PI / points - Math.PI / 2.0).toFloat()
        val r     = if (i % 2 == 0) radius else inner
        val x     = center.x + r * cos(angle)
        val y     = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color)
}
