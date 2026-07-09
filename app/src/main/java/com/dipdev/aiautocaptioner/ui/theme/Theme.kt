package com.dipdev.aiautocaptioner.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dipdev.aiautocaptioner.data.repository.AppTheme

val LocalGlassmorphismEnabled = staticCompositionLocalOf { true }
val LocalAppTheme = staticCompositionLocalOf { AppTheme.TRUE_BLACK }

@Composable
fun AutoCaptionerTheme(
    appTheme: AppTheme = AppTheme.TRUE_BLACK,
    glassmorphismEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val backgroundColors = Triple(TrueBlackBackground, TrueBlackSurface, TrueBlackSurfaceVariant)

    val (bgColor, surfaceColor, surfaceVariantColor) = backgroundColors

    // Primary will just be AccentAmber as a fallback, but the UI should use semantic colors directly
    val colorScheme = darkColorScheme(
        primary            = AccentAmber,
        onPrimary          = Color.White,
        primaryContainer   = AccentAmber.copy(alpha = 0.15f),
        onPrimaryContainer = AccentAmber,
        secondary          = surfaceVariantColor,
        onSecondary        = TextPrimary,
        tertiary           = AccentViolet,
        onTertiary         = Color.White,
        background         = bgColor,
        onBackground       = TextPrimary,
        surface            = surfaceColor,
        onSurface          = TextPrimary,
        surfaceVariant     = surfaceVariantColor,
        onSurfaceVariant   = TextSecondary,
        outline            = OutlineColor,
        error              = AccentRose,
        onError            = Color.White,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }

    CompositionLocalProvider(
        LocalGlassmorphismEnabled provides glassmorphismEnabled,
        LocalAppTheme provides appTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}

@Composable
fun ScreenThemeProvider(
    accentColor: Color,
    content: @Composable () -> Unit
) {
    val currentColorScheme = MaterialTheme.colorScheme
    val newColorScheme = currentColorScheme.copy(
        primary = accentColor,
        primaryContainer = accentColor.copy(alpha = 0.15f),
        onPrimaryContainer = accentColor,
    )
    CompositionLocalProvider(LocalAccentColor provides accentColor) {
        MaterialTheme(
            colorScheme = newColorScheme,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}