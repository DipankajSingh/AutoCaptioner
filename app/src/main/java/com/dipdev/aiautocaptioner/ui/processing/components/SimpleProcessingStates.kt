package com.dipdev.aiautocaptioner.ui.processing.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dipdev.aiautocaptioner.R
import com.dipdev.aiautocaptioner.ui.components.AppOutlinedButton
import com.dipdev.aiautocaptioner.ui.components.AppPrimaryButton
import com.dipdev.aiautocaptioner.ui.components.FullScreenStateContainer
import com.dipdev.aiautocaptioner.ui.components.ProcessingStateHeader
import compose.icons.FeatherIcons
import compose.icons.feathericons.AlertTriangle

@Composable
fun CancelledView(onRetry: () -> Unit, onGoBack: () -> Unit) {
    FullScreenStateContainer(
        graphicContent = {},
        textContent = {
            ProcessingStateHeader(
                title = stringResource(R.string.state_cancelled),
                subtitle = stringResource(R.string.state_cancelled_desc)
            )
        },
        actionContent = {
            Spacer(modifier = Modifier.height(24.dp))
            AppPrimaryButton(onClick = onRetry) {
                Text(stringResource(R.string.state_try_again), maxLines = 1)
            }
            Spacer(modifier = Modifier.height(12.dp))
            AppOutlinedButton(onClick = onGoBack) {
                Text(stringResource(R.string.state_go_back), maxLines = 1)
            }
        }
    )
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, onGoBack: () -> Unit) {
    FullScreenStateContainer(
        graphicContent = {
            Icon(
                imageVector = FeatherIcons.AlertTriangle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(64.dp)
            )
        },
        textContent = {
            Spacer(modifier = Modifier.height(16.dp))
            ProcessingStateHeader(
                title = stringResource(R.string.state_processing_failed),
                subtitle = message
            )
        },
        actionContent = {
            Spacer(modifier = Modifier.height(24.dp))
            AppPrimaryButton(onClick = onRetry) {
                Text(stringResource(R.string.state_retry), maxLines = 1)
            }
            Spacer(modifier = Modifier.height(12.dp))
            AppOutlinedButton(onClick = onGoBack) {
                Text(stringResource(R.string.state_go_back), maxLines = 1)
            }
        }
    )
}
