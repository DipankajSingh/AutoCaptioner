package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.AccentRose
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────────────────────
// DialogType — drives mascot expression and accent colour
// ─────────────────────────────────────────────────────────────────────────────

enum class DialogType {
    /** Neutral/informational — permissions, general notices */
    INFO,
    /** Caution — "are you sure", cellular warnings */
    WARNING,
    /** Destructive — deletes, errors */
    ERROR,
    /** Positive — future confirmations */
    SUCCESS
}

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The single universal dialog for the entire app.
 *
 * Swap the Material AlertDialog for this everywhere. The TV-robot mascot animates
 * its expression based on [type], giving the user immediate emotional context
 * before they read a word of text.
 *
 * @param modifier          Applied to the dialog card.
 * @param type              Drives mascot expression and accent colour.
 * @param title             Short, bold headline.
 * @param body              Optional body text. Ignored when [content] is set.
 * @param content           Optional arbitrary composable (e.g. a TextField). When
 *                          provided, [body] is not rendered.
 * @param confirmText       Primary action button label.
 * @param onConfirm         Primary action callback.
 * @param isConfirmEnabled  Set false to disable the primary button (e.g. blank input).
 * @param dismissText       Optional secondary/cancel button label.
 * @param onDismiss         Secondary action callback; falls back to [onDismissRequest].
 * @param onDismissRequest  Called on outside tap or back press.
 */
@Composable
fun UniversalDialog(
    modifier: Modifier = Modifier,
    type: DialogType = DialogType.INFO,
    title: String,
    body: String? = null,
    content: @Composable (() -> Unit)? = null,
    confirmText: String,
    onConfirm: () -> Unit,
    isConfirmEnabled: Boolean = true,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    // Staggered entrance — same pattern as CustomPaywallDialog.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(40); visible = true }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false   // we own the width
        )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 6 }
        ) {
            DialogCard(
                modifier         = modifier,
                type             = type,
                title            = title,
                body             = body,
                content          = content,
                confirmText      = confirmText,
                onConfirm        = onConfirm,
                isConfirmEnabled = isConfirmEnabled,
                dismissText      = dismissText,
                onDismiss        = onDismiss ?: onDismissRequest
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DialogCard — card whose top edge bulges up into a smooth arch housing the mascot
// ─────────────────────────────────────────────────────────────────────────────

// Mascot size.
private val MASCOT_SIZE_DP = 104.dp

@Composable
private fun DialogCard(
    modifier: Modifier,
    type: DialogType,
    title: String,
    body: String?,
    content: (@Composable () -> Unit)?,
    confirmText: String,
    onConfirm: () -> Unit,
    isConfirmEnabled: Boolean,
    dismissText: String?,
    onDismiss: () -> Unit
) {
    val accent    = type.accentColor()
    val surface   = MaterialTheme.colorScheme.surface
    val archShape = remember { DialogArchShape() }

    Box(
        modifier         = modifier.fillMaxWidth(0.92f),
        contentAlignment = Alignment.TopCenter
    ) {
        // ── Main card surface, clipped to the arch shape ───────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(archShape)
                .background(surface)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accent.copy(alpha = 0.45f),
                            accent.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                        )
                    ),
                    shape = archShape
                )
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                .drawBehind {
                    // Paint an accent wash over the top arch zone only.
                    // Because drawBehind runs inside the already-clipped bounds,
                    // it perfectly follows the arch outline with zero cropping.
                    val headerH = size.height * 0.38f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.22f),
                                accent.copy(alpha = 0.00f)
                            ),
                            startY = 0f,
                            endY   = headerH
                        ),
                        size = Size(size.width, headerH)
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Mascot lives inside the arch dome ──────────────────────────────
            Spacer(Modifier.height(12.dp))
            MascotRobot(
                type     = type,
                modifier = Modifier.size(MASCOT_SIZE_DP)
            )
            Spacer(Modifier.height(20.dp))

            Text(
                text      = title,
                style     = MaterialTheme.typography.titleLarge,
                color     = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )

            if (body != null || content != null) {
                Spacer(Modifier.height(8.dp))
                if (content != null) {
                    content()
                } else {
                    Text(
                        text      = body!!,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier  = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ── Action buttons ─────────────────────────────────────────────
            if (dismissText != null) {
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick  = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                        )
                    ) {
                        Text(
                            text  = dismissText,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick  = onConfirm,
                        enabled  = isConfirmEnabled,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = primaryButtonColors(accent)
                    ) {
                        Text(confirmText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                Button(
                    onClick  = onConfirm,
                    enabled  = isConfirmEnabled,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = primaryButtonColors(accent)
                ) {
                    Text(confirmText, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DialogArchShape — traced directly from the reference SVG (ref.svg), not
// hand-tuned. The dome is two cubic Beziers per side (not one) — that's what
// the single-cubic version was missing: with two curves you get a control
// point that can sit level with the peak (horizontal tangent at the top) AND
// a separate control point that sits level with the flat top edge
// (horizontal tangent at the bottom), so both joins are seamless. A single
// cubic can only guarantee one of those two tangencies, which is exactly
// where the old kink came from.
//
// All numbers below are the literal control points from the SVG's path data
// (peak-relative, in the SVG's own units), scaled by `sx` so the whole shape
// resizes proportionally with the card's actual width — the shape you get
// is the same shape at any dialog width. Corner radius scales the same way,
// since the reference uses one consistent radius throughout.
// ─────────────────────────────────────────────────────────────────────────────

private class DialogArchShape : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        // Reference SVG's card width (wall to wall) — everything below is
        // scaled relative to this so the traced shape reproduces exactly,
        // regardless of the dialog's actual rendered width.
        val sx = w / 348.93945f

        val cr = 27.323349f * sx   // corner radius (all four corners)
        val ah = 54.822262f * sx   // flat top edge y — how far the dome rises above it

        // Dome control points, relative to the peak (0,0), straight from the SVG.
        // L6 is the point where the dome meets the flat top edge.
        val l1x = -25.82779f;  val l1y = 0.000723f
        val l2x = -48.8123f;   val l2y = 15.129802f
        val l3x = -60.10045f;  val l3y = 38.021972f
        val l4x = -64.44129f;  val l4y = 46.825112f
        val l5x = -73.87413f;  val l5y = 54.822262f
        val l6x = -83.71507f   // l6y == l5y == the flat-edge height

        val aw = -l6x * sx   // dome half-width at the flat edge

        val path = Path().apply {
            moveTo(cr, ah)
            lineTo(cx - aw, ah)   // top-left flat segment → dome's left base

            // Left half of the dome, base → peak (SVG curve run in reverse —
            // reversing a Bezier's control points retraces the identical curve).
            cubicTo(
                cx + l5x * sx, ah,
                cx + l4x * sx, l4y * sx,
                cx + l3x * sx, l3y * sx
            )
            cubicTo(
                cx + l2x * sx, l2y * sx,
                cx + l1x * sx, l1y * sx,
                cx, 0f
            )

            // Right half of the dome, peak → base (mirror image of the left).
            cubicTo(
                cx - l1x * sx, l1y * sx,
                cx - l2x * sx, l2y * sx,
                cx - l3x * sx, l3y * sx
            )
            cubicTo(
                cx - l4x * sx, l4y * sx,
                cx - l5x * sx, ah,
                cx + aw, ah
            )

            lineTo(w - cr, ah)

            // ── Corners + sides — unchanged from the original, already-correct logic ──
            arcTo(Rect(w - 2 * cr, ah, w, ah + 2 * cr), 270f, 90f, false)
            lineTo(w, h - cr)
            arcTo(Rect(w - 2 * cr, h - 2 * cr, w, h), 0f, 90f, false)
            lineTo(cr, h)
            arcTo(Rect(0f, h - 2 * cr, 2 * cr, h), 90f, 90f, false)
            lineTo(0f, ah + cr)
            arcTo(Rect(0f, ah, 2 * cr, ah + 2 * cr), 180f, 90f, false)

            close()
        }

        return Outline.Generic(path)
    }
}

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
private fun MascotRobot(
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
        targetValue  = if (type == DialogType.SUCCESS) 1.32f else 1.00f,  // more visible at 104dp
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
            DialogType.ERROR   -> 28f   // more pronounced at larger size
            DialogType.WARNING -> 15f
            DialogType.SUCCESS -> -8f   // slight outward/happy squint
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

                drawRoundRect(            // right ear
                    color        = earColor,
                    topLeft      = Offset(px(70.84f), earY),
                    size         = Size(earW, earH),
                    cornerRadius = earCR
                )
                drawRoundRect(            // left ear
                    color        = earColor,
                    topLeft      = Offset(px(22.42f), earY),
                    size         = Size(earW, earH),
                    cornerRadius = earCR
                )

                // ════════════════════════════════════════════════════════════
                // 2. GRADIENT BODY (antenna ball + stem + main head shape)
                //    Gradient: amber (#f59e0b) at 14% → red (#f5000b) at 100%
                //    Spans vertically from y≈20.44 (top) to y≈78.12 (bottom)
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
                //    x=29.39  y=33.80  w=49.61  h=41.10  rx=8.89  ry=11.25
                // ════════════════════════════════════════════════════════════
                drawRoundRect(
                    color        = bezelColor,
                    topLeft      = Offset(px(29.39f), py(33.80f)),
                    size         = Size(pw(49.61f), ph(41.10f)),
                    cornerRadius = CornerRadius(pw(8.89f), ph(11.25f))
                )

                // ════════════════════════════════════════════════════════════
                // 4. SCREEN — near-black inner face
                //    x=32.66  y=37.28  w=43.05  h=34.13  rx=7.99  ry=9.69
                // ════════════════════════════════════════════════════════════
                drawRoundRect(
                    color        = screenColor,
                    topLeft      = Offset(px(32.66f), py(37.28f)),
                    size         = Size(pw(43.05f), ph(34.13f)),
                    cornerRadius = CornerRadius(pw(7.99f), ph(9.69f))
                )

                // ════════════════════════════════════════════════════════════
                // 5. EYES — two white vertical pills
                //    Precise centres from SVG analysis:
                //      Left  eye: (43.39, 46.27)
                //      Right eye: (65.35, 46.27)
                //    Each pill: ~w=2.97  h=7.97  (near-vertical — very narrow)
                //    Eye-tilt: left eye rotates by -eyeTiltDeg, right by +eyeTiltDeg,
                //    so they rotate inward on ERROR/WARNING (angry brow effect)
                // ════════════════════════════════════════════════════════════
                val eyeHalfW = pw(1.49f)   // half of 2.97
                val eyeHalfH = ph(3.99f)   // half of 7.97
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
                //    Computed vertices in 108-space from SVG analysis:
                //      Left  vertex: (50.90, 44.29)  [top of left]
                //      Right vertex: (56.97, 47.28)  [point of arrow]
                //      Left  vertex: (50.90, 51.34)  [bottom of left]
                //    Centre: (53.94, 47.28)
                //    For SUCCESS state: scale up from centre via playPulse
                // ════════════════════════════════════════════════════════════
                val triCx = px(53.94f)
                val triCy = py(47.28f)

                // Scale the three vertices relative to the centroid.
                // Raw offsets in 108-space: left-x=-3.04, right-x=+3.03, half-h=3.53
                val triLx = pw(3.04f) * playPulse   // left-side half-width
                val triRx = pw(3.03f) * playPulse   // right-side half-width
                val triH  = ph(3.53f) * playPulse   // half-height

                val playPath = Path().apply {
                    moveTo(triCx - triLx, triCy - triH)   // top-left
                    lineTo(triCx + triRx, triCy)           // right tip
                    lineTo(triCx - triLx, triCy + triH)   // bottom-left
                    close()
                }
                drawPath(path = playPath, color = playWhite)

                // ════════════════════════════════════════════════════════════
                // 7. CAPTION LINES (the "mouth" / text indicator area)
                //    Exact coords from SVG (no additional transform):
                //      Line 1 — white short: x=37.34  y=57.19  w=18.91  h=2.02  rx=1.01
                //      Line 2 — white full:  x=37.51  y=60.83  w=31.96  h=2.02  rx=1.01
                //      Line 3 — white med:   x=40.18  y=64.51  w=25.02  h=1.84  rx=0.92
                //      Line 4 — amber short: x=58.42  y=56.88  w=10.37  h=2.02  rx=1.01
                //
                //    For WARNING/ERROR: captionJitter applies a small oscillating
                //    x-offset (lines alternate direction) to give a "nervous" look.
                // ════════════════════════════════════════════════════════════
                val lineH1CR = CornerRadius(ph(1.01f), ph(1.01f))
                val lineH3CR = CornerRadius(ph(0.92f), ph(0.92f))
                val j = pw(captionJitter)   // jitter in screen pixels

                // Amber accent line (top-right position — sits above text block)
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

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Semantic accent colour for each dialog type, sourced from the app theme. */
private fun DialogType.accentColor(): Color = when (this) {
    DialogType.INFO    -> AccentAmber
    DialogType.WARNING -> AccentAmber
    DialogType.ERROR   -> AccentRose
    DialogType.SUCCESS -> AccentAmber
}

@Composable
private fun primaryButtonColors(accent: Color) = ButtonDefaults.buttonColors(
    containerColor         = accent,
    contentColor           = Color.White,
    disabledContainerColor = accent.copy(alpha = 0.30f),
    disabledContentColor   = Color.White.copy(alpha = 0.38f)
)
