package com.dipdev.aiautocaptioner.ui.recorder.permission

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun PermissionGate(
    requiredPermissions: List<String>,
    onAllGranted: @Composable () -> Unit,
    onBlocked: @Composable (cameraGranted: Boolean, micGranted: Boolean, cameraPermanentlyDenied: Boolean, micPermanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var permissionsState by remember { mutableStateOf(requiredPermissions.associateWith { isGranted(context, it) }) }
    var permanentlyDenied by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var hasRequestedPermissions by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasRequestedPermissions = true
        grants.forEach { (perm, granted) ->
            permissionsState = permissionsState + (perm to granted)
            if (!granted) {
                val activity = context as? Activity
                permanentlyDenied = permanentlyDenied + (perm to (activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)))
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionsState = requiredPermissions.associateWith { isGranted(context, it) }
                if (hasRequestedPermissions) {
                    val activity = context as? Activity
                    if (activity != null) {
                        permanentlyDenied = requiredPermissions.associateWith { perm ->
                            !isGranted(context, perm) && !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
                        }
                    } else {
                        permanentlyDenied = emptyMap()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allGranted = permissionsState.values.all { it }

    if (allGranted) {
        onAllGranted()
    } else {
        val cameraGranted = permissionsState[android.Manifest.permission.CAMERA] ?: false
        val micGranted = permissionsState[android.Manifest.permission.RECORD_AUDIO] ?: false
        val cameraPermDenied = permanentlyDenied[android.Manifest.permission.CAMERA] ?: false
        val micPermDenied = permanentlyDenied[android.Manifest.permission.RECORD_AUDIO] ?: false

        onBlocked(
            cameraGranted,
            micGranted,
            cameraPermDenied,
            micPermDenied,
            {
                val needed = permissionsState.filter { !it.value }.keys.toTypedArray()
                if (needed.isNotEmpty()) launcher.launch(needed)
            },
            {
                val intent = android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null)
                )
                context.startActivity(intent)
            }
        )
    }
}

private fun isGranted(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
