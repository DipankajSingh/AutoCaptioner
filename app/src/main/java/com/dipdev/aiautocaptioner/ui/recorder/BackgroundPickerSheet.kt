package com.dipdev.aiautocaptioner.ui.recorder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import android.net.Uri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundPickerSheet(
    onDismissRequest: () -> Unit,
    onBackgroundSelected: (BackgroundState) -> Unit
) {
    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                onBackgroundSelected(BackgroundState.ImageBitmap(bitmap))
            }
        }
        onDismissRequest()
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Select Background", style = MaterialTheme.typography.titleLarge)

            // Solid Colors
            Text("Solid Colors", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val colors = listOf(Color.Black, Color.White, Color.Red, Color.Green, Color.Blue, Color(0xFF6200EE))
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable {
                                onBackgroundSelected(BackgroundState.SolidColor(color))
                                onDismissRequest()
                            }
                    )
                }
            }

            // Built-in Gradients
            Text("Gradients", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val gradients = listOf(
                    listOf(Color.Red, Color.Yellow),
                    listOf(Color.Blue, Color.Cyan),
                    listOf(Color.Magenta, Color.Blue),
                    listOf(Color(0xFFff9a9e), Color(0xFFfecfef)),
                    listOf(Color(0xFFa18cd1), Color(0xFFfbc2eb))
                )
                gradients.forEach { gradientColors ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(gradientColors))
                            .clickable {
                                onBackgroundSelected(BackgroundState.Gradient(gradientColors))
                                onDismissRequest()
                            }
                    )
                }
            }

            // Media Import
            Text("Media", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ElevatedButton(onClick = {
                    imagePickerLauncher.launch("image/*")
                }) {
                    Icon(Icons.Default.Image, contentDescription = "Gallery")
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery")
                }

                ElevatedButton(
                    onClick = { /* Coming Soon */ },
                    enabled = false
                ) {
                    Icon(Icons.Default.VideoFile, contentDescription = "Video")
                    Spacer(Modifier.width(8.dp))
                    Text("Video (Soon)")
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
