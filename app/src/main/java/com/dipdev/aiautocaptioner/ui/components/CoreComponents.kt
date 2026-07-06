package com.dipdev.aiautocaptioner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.theme.LocalGlassmorphismEnabled

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    color: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val glassmorphismEnabled = LocalGlassmorphismEnabled.current
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f

    if (isLightTheme || !glassmorphismEnabled) {
        // In light themes or when glassmorphism is disabled, render as a plain card
        Card(
            modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            content = { content() }
        )
    } else {
        val baseBgColor = if (color != Color.Unspecified) color else MaterialTheme.colorScheme.surface
        val bgColor = baseBgColor.copy(alpha = 0.5f)
        val borderColor = Color.White.copy(alpha = 0.1f)

        var baseModifier = modifier
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)

        if (onClick != null) {
            baseModifier = baseModifier.clickable { onClick() }
        }

        Box(modifier = baseModifier) {
            content()
        }
    }
}

@Composable
fun GradientPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    paddingValues: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
) {
    val primary = MaterialTheme.colorScheme.primary
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            primary.copy(alpha = 0.8f),
            primary
        )
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (enabled) gradient
                else Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.colorScheme.outlineVariant
                    )
                )
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(paddingValues),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun EmptyState(
    title: String = "No Projects Yet",
    subtitle: String = "Import a video to add karaoke captions",
    buttonText: String = "Import Video",
    onAction: () -> Unit
) {
    val composition by com.airbnb.lottie.compose.rememberLottieComposition(
        com.airbnb.lottie.compose.LottieCompositionSpec.RawRes(com.dipdev.aiautocaptioner.R.raw.nothing)
    )

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top
    ) {
        com.airbnb.lottie.compose.LottieAnimation(
            composition = composition,
            iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
        GradientPrimaryButton(
            onClick = onAction,
            text = buttonText
        )
    }
}

@Composable
fun ModelStat(label: String, value: String) {
    androidx.compose.foundation.layout.Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun FullScreenStateContainer(
    graphicContent: @Composable () -> Unit,
    textContent: @Composable () -> Unit,
    actionContent: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
    ) {
        graphicContent()
        textContent()
        actionContent()
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SimpleAppScaffold(
    title: String,
    onNavigateBack: () -> Unit,
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            androidx.compose.material3.TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(Color.Transparent),
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            content()
        }
    }
}
