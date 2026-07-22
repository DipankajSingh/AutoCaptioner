package com.dipdev.aiautocaptioner.ui.videoeditor.style.tabs

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.engine.BundledFonts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontPickerSheet(
    currentFont: String,
    onFontSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 48.dp)
        ) {
            Text(
                stringResource(R.string.font_picker_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(BundledFonts.all) { entry ->
                    FontPickerItem(
                        entry = entry,
                        isSelected = currentFont == entry.displayName,
                        onClick = {
                            onFontSelected(entry.displayName)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FontPickerItem(
    entry: BundledFonts.FontEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected)
            MaterialTheme.colorScheme.primaryContainer
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.displayName,
                fontFamily = when (entry.displayName) {
                    "System" -> FontFamily.Default
                    else -> {
                        val tf = try {
                            Typeface.createFromAsset(context.assets, entry.assetPath)
                        } catch (_: Exception) { null }
                        tf?.let { FontFamily(it) } ?: FontFamily.Default
                    }
                },
                fontWeight = if (entry.displayName == "System") FontWeight.Normal else when (entry.displayName) {
                    "Montserrat", "Rubik", "Oswald", "Bebas Neue", "Bungee", "Permanent Marker", "Pacifico" -> FontWeight.Bold
                    else -> FontWeight.Normal
                },
                fontSize = 16.sp,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )

            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        "✓",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}