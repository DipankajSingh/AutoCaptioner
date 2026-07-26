package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentRose

// ─────────────────────────────────────────────────────────────────────────────
// MascotRobot — pixel-accurate Canvas recreation of the TV-robot logo
// ─────────────────────────────────────────────────────────────────────────────
//
// All geometry is derived from foreground_icon.svg (108 × 108 viewBox).
// Coordinates computed by the SVG geometry analyser:
//
//   EARS (both):   w=15.24  h=21.86  rx=8.04  ry=6.93  fill=#ffa454
//     Right ear:   x=70.84  y=43.04
//     Left  ear:   x=22.42  y=43.04
//   BODY (gradient arc):  ~x=26.24..82.19  y=20.44..78.12
//   BEZEL (brown):  x=29.39 y=33.80  w=49.61  h=41.10  rx=8.89/11.25  fill=#733300
//   SCREEN (black): x=32.66 y=37.28  w=43.05  h=34.13  rx=7.99/9.69   fill=#00000b
//   LEFT EYE:  centre=(43.39, 46.27)   pill ~w=2.97 h=7.97  fill=#fafafa
//   RIGHT EYE: centre=(65.35, 46.27)   pill ~w=2.97 h=7.97  fill=#fafafa
//   PLAY TRI:  vertices in SVG≈(50.90,44.29) (56.97,51.34) (56.97,43.22)
//              centre=(53.94, 47.28)  fill=#fbfbfb
//   LINE 1 (white):  x=37.34 y=57.19  w=18.91  h=2.02  rx=1.01
//   LINE 2 (white):  x=37.51 y=60.83  w=31.96  h=2.02  rx=1.01
//   LINE 3 (white):  x=40.18 y=64.51  w=25.02  h=1.84  rx=0.92
//   LINE 4 (amber):  x=58.42 y=56.88  w=10.37  h=2.02  rx=1.01  fill=#f59e0b
//
// Animation drivers:
//   • antennaGlow  — pulsing alpha on the gradient body (INFO: slow, ERROR: fast)
//   • eyeTilt      — rotation applied to each eye pill (WARNING: ±12°, ERROR: ±22°)
//   • playScale    — scale of the play triangle (SUCCESS: bounces)
//   • captionJitter — tiny x-offset on caption lines (WARNING/ERROR: subtle jitter)

@Composable
internal fun MascotRobot(
    type: DialogType,
    modifier: Modifier = Modifier
) {
    // ── Continuous animation drivers ──────────────────────────────────────────
    val inf = rememberInfiniteTransition(label = "mascot")

    val antennaGlow by inf.animateFloat(
        initialValue = 0.65f,
        targetValue  = 1.00f,
        animationSpec = infiniteRepeatable(
            animation   = tween(
                durationMillis = when (type) {
                    DialogType.ERROR   -> 380
                    DialogType.WARNING -> 680
                    else               -> 1700
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "antennaGlow"
    )

    val playPulse by inf.animateFloat(
        initialValue = 1.00f,
        targetValue  = if (type == DialogType.SUCCESS) 1.32f else 1.00f,
        animationSpec = infiniteRepeatable(
            animation  = tween(580, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "playPulse"
    )

    val captionJitter by inf.animateFloat(
        initialValue = 0f,
        targetValue  = if (type == DialogType.ERROR || type == DialogType.WARNING) 3.8f else 0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(320, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "captionJitter"
    )

    // ── One-shot: eye tilt driven by type ────────────────────────────────────
    val eyeTiltDeg by animateFloatAsState(
        targetValue = when (type) {
            DialogType.ERROR   -> 28f
            DialogType.WARNING -> 15f
            DialogType.SUCCESS -> -8f
            DialogType.INFO    -> 0f
        },
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "eyeTilt"
    )

    // ── Gradient colours — shift based on type ───────────────────────────────
    val gradTop = when (type) {
        DialogType.ERROR   -> AccentRose.copy(alpha = antennaGlow)
        DialogType.WARNING -> AccentAmber.copy(alpha = antennaGlow)
        DialogType.SUCCESS -> AccentAmber
        DialogType.INFO    -> AccentAmber.copy(alpha = antennaGlow)
    }
    val gradBottom = when (type) {
        DialogType.ERROR   -> Color(0xFFF5000B).copy(alpha = antennaGlow)
        DialogType.WARNING -> AccentRose.copy(alpha = antennaGlow)
        DialogType.SUCCESS -> AccentAmber
        DialogType.INFO    -> Color(0xFFF5000B).copy(alpha = antennaGlow)
    }

    Box(
        modifier = modifier.drawWithCache {
            // All SVG coords are in a 108×108 space; scale to actual dp size.
            val W  = size.width
            val H  = size.height
            val sx = W / 108f
            val sy = H / 108f

            // Helpers — convert SVG coords/sizes to screen pixels.
            fun px(svgX: Float) = svgX * sx
            fun py(svgY: Float) = svgY * sy
            fun pw(svgW: Float) = svgW * sx
            fun ph(svgH: Float) = svgH * sy

            val earColor    = Color(0xFFFFA454)
            val bezelColor  = Color(0xFF733300)
            val screenColor = Color(0xFF00000B)
            val faceWhite   = Color(0xFFFAFAFA)
            val playWhite   = Color(0xFFFBFBFB)
            val amberLine   = AccentAmber

            onDrawBehind {
                // ════════════════════════════════════════════════════════════
                // 1. EARS — two peach rounded-rects on each side
                //    Both at y=43.04; right at x=70.84, left at x=22.42
                // ════════════════════════════════════════════════════════════
                val earW  = pw(15.24f)
                val earH  = ph(21.86f)
                val earCR = CornerRadius(pw(8.04f), ph(6.93f))
                val earY  = py(43.04f)

                drawRoundRect(
                    color        = earColor,
                    topLeft      = Offset(px(70.84f), earY),
                    size         = Size(earW, earH),
                    cornerRadius = earCR
                )
                drawRoundRect(
                    color        = earColor,
                    topLeft      = Offset(px(22.42f), earY),
                    size         = Size(earW, earH),
                    cornerRadius = earCR
                )

                // ════════════════════════════════════════════════════════════
                // 2. GRADIENT BODY (antenna ball + stem + main head shape)
                // ════════════════════════════════════════════════════════════
                val gradBrush = Brush.verticalGradient(
                    0.14f to gradTop,
                    1.00f to gradBottom,
                    startY = py(20.44f),
                    endY   = py(78.12f)
                )

                // Antenna ball — small circle, centre (54.1, 24.0)
                drawCircle(
                    brush  = gradBrush,
                    radius = pw(4.8f),
                    center = Offset(px(54.1f), py(24.0f))
                )
                // Antenna stem — thin rounded rect connecting ball to head
                drawRoundRect(
                    brush        = gradBrush,
                    topLeft      = Offset(px(51.9f), py(27.5f)),
                    size         = Size(pw(4.4f), ph(8.5f)),
                    cornerRadius = CornerRadius(pw(2.0f), ph(2.0f))
                )
                // Main head — large rounded rect (the outer gradient shell)
                drawRoundRect(
                    brush        = gradBrush,
                    topLeft      = Offset(px(26.24f), py(34.0f)),
                    size         = Size(pw(55.95f), ph(44.12f)),
                    cornerRadius = CornerRadius(pw(11.5f), ph(11.5f))
                )

                // ════════════════════════════════════════════════════════════
                // 3. BEZEL — dark-sienna rounded rect (the TV frame)
                // ════════════════════════════════════════════════════════════
                drawRoundRect(
                    color        = bezelColor,
                    topLeft      = Offset(px(29.39f), py(33.80f)),
                    size         = Size(pw(49.61f), ph(41.10f)),
                    cornerRadius = CornerRadius(pw(8.89f), ph(11.25f))
                )

                // ════════════════════════════════════════════════════════════
                // 4. SCREEN — near-black inner face
                // ════════════════════════════════════════════════════════════
                drawRoundRect(
                    color        = screenColor,
                    topLeft      = Offset(px(32.66f), py(37.28f)),
                    size         = Size(pw(43.05f), ph(34.13f)),
                    cornerRadius = CornerRadius(pw(7.99f), ph(9.69f))
                )

                // ════════════════════════════════════════════════════════════
                // 5. EYES — two white vertical pills
                // ════════════════════════════════════════════════════════════
                val eyeHalfW = pw(1.49f)
                val eyeHalfH = ph(3.99f)
                val eyeCR    = CornerRadius(pw(1.49f), ph(1.49f))

                val leftEyeCx  = px(43.39f);  val leftEyeCy  = py(46.27f)
                val rightEyeCx = px(65.35f);  val rightEyeCy = py(46.27f)

                // Left eye — rotates counter-clockwise for WARNING/ERROR
                rotate(degrees = -eyeTiltDeg, pivot = Offset(leftEyeCx, leftEyeCy)) {
                    drawRoundRect(
                        color        = faceWhite,
                        topLeft      = Offset(leftEyeCx - eyeHalfW, leftEyeCy - eyeHalfH),
                        size         = Size(eyeHalfW * 2f, eyeHalfH * 2f),
                        cornerRadius = eyeCR
                    )
                }
                // Right eye — rotates clockwise (mirror of left)
                rotate(degrees = eyeTiltDeg, pivot = Offset(rightEyeCx, rightEyeCy)) {
                    drawRoundRect(
                        color        = faceWhite,
                        topLeft      = Offset(rightEyeCx - eyeHalfW, rightEyeCy - eyeHalfH),
                        size         = Size(eyeHalfW * 2f, eyeHalfH * 2f),
                        cornerRadius = eyeCR
                    )
                }

                // ════════════════════════════════════════════════════════════
                // 6. PLAY TRIANGLE — right-pointing triangle (the "nose")
                // ════════════════════════════════════════════════════════════
                val triCx = px(53.94f)
                val triCy = py(47.28f)

                val triLx = pw(3.04f) * playPulse
                val triRx = pw(3.03f) * playPulse
                val triH  = ph(3.53f) * playPulse

                val playPath = Path().apply {
                    moveTo(triCx - triLx, triCy - triH)
                    lineTo(triCx + triRx, triCy)
                    lineTo(triCx - triLx, triCy + triH)
                    close()
                }
                drawPath(path = playPath, color = playWhite)

                // ════════════════════════════════════════════════════════════
                // 7. CAPTION LINES (the "mouth" / text indicator area)
                // ════════════════════════════════════════════════════════════
                val lineH1CR = CornerRadius(ph(1.01f), ph(1.01f))
                val lineH3CR = CornerRadius(ph(0.92f), ph(0.92f))
                val j = pw(captionJitter)

                // Amber accent line (top-right position)
                drawRoundRect(
                    color        = amberLine,
                    topLeft      = Offset(px(58.42f) - j, py(56.88f)),
                    size         = Size(pw(10.37f), ph(2.02f)),
                    cornerRadius = lineH1CR
                )
                // White line 1 — short
                drawRoundRect(
                    color        = faceWhite,
                    topLeft      = Offset(px(37.34f) + j, py(57.19f)),
                    size         = Size(pw(18.91f), ph(2.02f)),
                    cornerRadius = lineH1CR
                )
                // White line 2 — long (full width of screen)
                drawRoundRect(
                    color        = faceWhite,
                    topLeft      = Offset(px(37.51f) - j, py(60.83f)),
                    size         = Size(pw(31.96f), ph(2.02f)),
                    cornerRadius = lineH1CR
                )
                // White line 3 — medium
                drawRoundRect(
                    color        = faceWhite,
                    topLeft      = Offset(px(40.18f) + j * 0.4f, py(64.51f)),
                    size         = Size(pw(25.02f), ph(1.84f)),
                    cornerRadius = lineH3CR
                )
            }
        }
    )
}
