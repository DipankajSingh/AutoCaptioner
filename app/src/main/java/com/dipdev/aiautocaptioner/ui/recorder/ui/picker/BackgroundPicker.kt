package com.dipdev.aiautocaptioner.ui.recorder.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.ui.recorder.model.BackgroundState

private val presetGradients = listOf(
    BackgroundState.Gradient(listOf(Color(0xFF4158D0.toInt()), Color(0xFFC850C0.toInt()), Color(0xFFFFCC70.toInt()))),
    BackgroundState.Gradient(listOf(Color(0xFF0093E9.toInt()), Color(0xFF80D0C7.toInt()))),
    BackgroundState.Gradient(listOf(Color(0xFFFBAB7E.toInt()), Color(0xFFF7CE68.toInt()))),
    BackgroundState.Gradient(listOf(Color(0xFF8EC5FC.toInt()), Color(0xFFE0C3FC.toInt()))),
    BackgroundState.Gradient(listOf(Color(0xFFD9AF69.toInt()), Color(0xFFD4A76A.toInt()), Color(0xFFBFC9C3.toInt()))),
    BackgroundState.Gradient(listOf(Color(0xFF0575E6.toInt()), Color(0xFF00FEA5.toInt()))),
    BackgroundState.Gradient(listOf(Color(0xFFFC466B.toInt()), Color(0xFF3F5EFB.toInt()))),
    BackgroundState.Gradient(listOf(Color(0xFF11998e.toInt()), Color(0xFF38ef7d.toInt()))),
)

private val solidColors = listOf(
    BackgroundState.SolidColor(Color(0xFF000000)),
    BackgroundState.SolidColor(Color(0xFF1A1A2E)),
    BackgroundState.SolidColor(Color(0xFF16213E)),
    BackgroundState.SolidColor(Color(0xFF533483)),
    BackgroundState.SolidColor(Color(0xFFE94560)),
    BackgroundState.SolidColor(Color(0xFF0F3460)),
    BackgroundState.SolidColor(Color(0xFF53354A)),
    BackgroundState.SolidColor(Color(0xFF2B2E4A)),
    BackgroundState.SolidColor(Color(0xFF903749)),
    BackgroundState.SolidColor(Color(0xFFFC466B.toInt())),
    BackgroundState.SolidColor(Color(0xFFEAEAEA)),
    BackgroundState.SolidColor(Color(0xFFFFFFFF)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    currentBackground: BackgroundState,
    onDismissRequest: () -> Unit,
    onBackgroundSelected: (BackgroundState) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF1A1A1A)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Canvas Background",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "Gradients",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(presetGradients) { gradient ->
                    val isSelected = currentBackground == gradient
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(gradient.colors))
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onBackgroundSelected(gradient) }
                    )
                }
            }

            Text(
                text = "Solid Colors",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(solidColors) { solid ->
                    val isSelected = currentBackground == solid
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(solid.color)
                            .border(
                                width = if (isSelected) 3.dp else 0.dp,
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onBackgroundSelected(solid) }
                    )
                }
            }
        }
    }
}
