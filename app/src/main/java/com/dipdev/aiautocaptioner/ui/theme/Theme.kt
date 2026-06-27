package com.dipdev.aiautocaptioner.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dipdev.aiautocaptioner.data.repository.AppTheme

val LocalGlassmorphismEnabled = staticCompositionLocalOf { true }
val LocalAppTheme = staticCompositionLocalOf { AppTheme.EMERALD }

@Composable
fun AutoCaptionerTheme(
    appTheme: AppTheme = AppTheme.EMERALD,
    useLightTheme: Boolean = false,
    glassmorphismEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val primaryColor = when (appTheme) {
        AppTheme.EMERALD    -> if (useLightTheme) EmeraldPrimaryDark else EmeraldPrimary
        AppTheme.FLAT_GREEN -> if (useLightTheme) FlatGreenDark else FlatGreenPrimary
        AppTheme.PURPLE     -> if (useLightTheme) PurpleDark else PurplePrimary
        AppTheme.BLUE       -> if (useLightTheme) BlueDark else BluePrimary
    }

    val colorScheme = if (useLightTheme) {
        lightColorScheme(
            primary            = primaryColor,
            onPrimary          = Color.White,
            primaryContainer   = primaryColor.copy(alpha = 0.12f),
            onPrimaryContainer = primaryColor,
            secondary          = LightSurfaceVariant,
            onSecondary        = LightTextPrimary,
            tertiary           = primaryColor,
            onTertiary         = Color.White,
            background         = LightBackground,
            onBackground       = LightTextPrimary,
            surface            = LightSurface,
            onSurface          = LightTextPrimary,
            surfaceVariant     = LightSurfaceVariant,
            onSurfaceVariant   = LightTextSecondary,
            outline            = LightOutline,
            error              = AccentRose,
            onError            = Color.White,
        )
    } else {
        darkColorScheme(
            primary            = primaryColor,
            onPrimary          = PremiumBackground,
            primaryContainer   = primaryColor.copy(alpha = 0.15f),
            onPrimaryContainer = primaryColor,
            secondary          = PremiumSurfaceVariant,
            onSecondary        = TextPrimary,
            tertiary           = primaryColor,
            onTertiary         = PremiumBackground,
            background         = PremiumBackground,
            onBackground       = TextPrimary,
            surface            = PremiumSurface,
            onSurface          = TextPrimary,
            surfaceVariant     = PremiumSurfaceVariant,
            onSurfaceVariant   = TextSecondary,
            outline            = Color(0xFF3F3F46),
            error              = AccentRose,
            onError            = Color.White,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = useLightTheme
            controller.isAppearanceLightNavigationBars = useLightTheme
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