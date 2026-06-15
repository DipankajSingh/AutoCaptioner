package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.GradientPrimaryButton

@Composable
fun DoneStateView(
    segmentCount: Int,
    onNavigateToStyleEditor: () -> Unit,
    onNavigateToCaptionEditor: () -> Unit,
    onRegenerate: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val composition by rememberLottieComposition(
            LottieCompositionSpec.RawRes(R.raw.loading_checklist)
        )
        val lottieProgress by animateLottieCompositionAsState(
            composition = composition,
            iterations = 1
        )
        LottieAnimation(
            composition = composition,
            progress = { lottieProgress },
            modifier = Modifier.size(80.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Captions Generated!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        if (segmentCount > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$segmentCount segments created",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        GradientPrimaryButton(
            text = "Style Your Captions",
            onClick = onNavigateToStyleEditor,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        AppOutlinedButton(
            onClick = onNavigateToCaptionEditor,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Review & Edit", maxLines = 1) }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onRegenerate) {
            Text(
                text = "Re-generate with different settings",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
