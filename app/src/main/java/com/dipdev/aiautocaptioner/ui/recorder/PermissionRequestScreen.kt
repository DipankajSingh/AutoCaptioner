package com.dipdev.aiautocaptioner.ui.recorder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.theme.AccentAmber
import com.dipdev.aiautocaptioner.ui.theme.ScreenThemeProvider
import com.dipdev.aiautocaptioner.ui.theme.TextPrimary
import com.dipdev.aiautocaptioner.ui.theme.TextSecondary
import com.dipdev.aiautocaptioner.ui.theme.TrueBlackBackground

/**
 * Full-screen permission rationale UI shown before the recorder starts.
 * Displayed when CAMERA or RECORD_AUDIO permission has not been granted.
 *
 * Themed with [AccentAmber] to visually separate it from the recorder's [AccentRose] theme.
 *
 * Handles two states:
 *  - Can ask again: shows "Allow Access" button → triggers system permission dialog.
 *  - Permanently denied: shows "Open Settings" button → deep-links to app settings.
 */
@Composable
fun PermissionRequestScreen(
    cameraGranted: Boolean,
    micGranted: Boolean,
    cameraPermanentlyDenied: Boolean,
    micPermanentlyDenied: Boolean,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    onPrivacyPolicy: () -> Unit
) {
    ScreenThemeProvider(accentColor = AccentAmber) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(TrueBlackBackground)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon(s) — show camera if not granted, mic if camera is granted but mic isn't
            val icon = if (!cameraGranted) Icons.Default.CameraAlt else Icons.Default.Mic
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentAmber,
                modifier = Modifier.size(72.dp)
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.recorder_permission_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            val bodyText = when {
                cameraPermanentlyDenied || micPermanentlyDenied ->
                    stringResource(R.string.recorder_permission_permanently_denied)
                !cameraGranted ->
                    stringResource(R.string.recorder_permission_body)
                else ->
                    stringResource(R.string.recorder_permission_denied_mic)
            }

            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )

            Spacer(Modifier.height(40.dp))

            // Primary CTA
            if (cameraPermanentlyDenied || micPermanentlyDenied) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentAmber
                    )
                ) {
                    Text(
                        text = stringResource(R.string.recorder_permission_open_settings),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Button(
                    onClick = onRequestPermissions,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentAmber
                    )
                ) {
                    Text(
                        text = stringResource(R.string.recorder_permission_allow),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.recorder_permission_dismiss))
            }

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = onPrivacyPolicy) {
                Text(
                    text = stringResource(R.string.recorder_permission_privacy),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
