package com.dipdev.aiautocaptioner.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.dipdev.aiautocaptioner.data.repository.AppTheme

val LocalGlassmorphismEnabled = staticCompositionLocalOf { true }
val LocalAppTheme = staticCompositionLocalOf { AppTheme.EMERALD }

@Composable
fun AutoCaptionerTheme(
    appTheme: AppTheme = AppTheme.EMERALD,
    glassmorphismEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val primaryColor = when (appTheme) {
        AppTheme.EMERALD -> EmeraldPrimary
        AppTheme.FLAT_GREEN -> FlatGreenPrimary
        AppTheme.PURPLE -> PurplePrimary
        AppTheme.BLUE -> BluePrimary
    }

    val colorScheme = darkColorScheme(
        primary = primaryColor,
        onPrimary = PremiumBackground,
        secondary = PremiumSurfaceVariant,
        onSecondary = TextPrimary,
        tertiary = primaryColor,
        onTertiary = PremiumBackground,
        background = PremiumBackground,
        onBackground = TextPrimary,
        surface = PremiumSurface,
        onSurface = TextPrimary,
        surfaceVariant = PremiumSurfaceVariant,
        onSurfaceVariant = TextPrimary
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
        LocalAppTheme provides appTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}