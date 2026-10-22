// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.dzid26.teslable.ble.hasBlePermissions
import com.dzid26.teslable.ble.isLocationEnabled
import com.dzid26.teslable.ble.missingOnboardingPermissions
import com.dzid26.teslable.ble.onboardingPermissions
import com.dzid26.teslable.ble.shouldOpenAppSettings

/**
 * Owns the permission wizard and hands the current state to [content], keeping
 * launcher plumbing out of the screens. [refreshKey] must change when the
 * activity resumes from a system settings screen; [onGranted] runs once all
 * permissions are present so the caller can start scanning and connecting.
 */
@Composable
internal fun PermissionWizardHost(
    refreshKey: Int,
    onGranted: () -> Unit,
    content: @Composable (
        permissionsGranted: Boolean,
        locationServicesEnabled: Boolean,
        requestPermissions: () -> Unit,
    ) -> Unit,
) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasBlePermissions(context)) }
    var locationServicesEnabled by remember { mutableStateOf(isLocationEnabled(context)) }
    var wizardOpen by remember { mutableStateOf(false) }
    var introDone by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var deniedForever by remember { mutableStateOf(false) }

    val appSettingsIntent =
        remember(context) {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            permissionsGranted = hasBlePermissions(context)
            denied = results.isDenial()
            deniedForever = denied && shouldOpenAppSettings(context)
            if (permissionsGranted && (results.isOptionalOnly() || missingOnboardingPermissions(context).isEmpty())) {
                wizardOpen = false
                locationServicesEnabled = isLocationEnabled(context)
                onGranted()
            }
        }

    LaunchedEffect(refreshKey) {
        permissionsGranted = hasBlePermissions(context)
        locationServicesEnabled = isLocationEnabled(context)
        if (wizardOpen && missingOnboardingPermissions(context).isEmpty()) {
            wizardOpen = false
            onGranted()
        }
    }

    // Leaving after the required permissions were granted (only notifications
    // skipped) must still start scanning, since the wizard's own finish never ran.
    fun dismissWizard() {
        wizardOpen = false
        if (hasBlePermissions(context)) {
            locationServicesEnabled = isLocationEnabled(context)
            onGranted()
        }
    }

    val requestPermissions: () -> Unit = {
        if (permissionsGranted) {
            onGranted()
        } else {
            // The full-screen wizard is the guided path; the cars-list card
            // remains the fallback entry point whenever a permission is missing.
            wizardOpen = true
            introDone = false
            denied = false
            deniedForever = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content(permissionsGranted, locationServicesEnabled, requestPermissions)

        if (wizardOpen && !introDone) {
            PermissionWizardGreeting(
                onContinue = { introDone = true },
                onDismiss = ::dismissWizard,
            )
        } else if (wizardOpen) {
            PermissionWizardSteps(
                denied = denied,
                deniedForever = deniedForever,
                onAllow = { permissions ->
                    if (deniedForever) {
                        context.startActivity(appSettingsIntent)
                    } else {
                        launcher.launch(permissions.toTypedArray())
                    }
                },
                onDismiss = ::dismissWizard,
            )
        }
    }
}

/** The page for the first onboarding permission that is still missing, if any. */
@Composable
private fun PermissionWizardSteps(
    denied: Boolean,
    deniedForever: Boolean,
    onAllow: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val allSteps = permissionSteps(onboardingPermissions())
    val missing = missingOnboardingPermissions(context)
    val nextStep = allSteps.firstOrNull { step -> step.permissions.any { it in missing } }
    if (nextStep != null) {
        PermissionWizardPage(
            step = nextStep,
            index = allSteps.indexOf(nextStep),
            total = allSteps.size,
            denied = denied,
            deniedForever = deniedForever,
            onAllow = { onAllow(nextStep.permissions) },
            onDismiss = onDismiss,
        )
    }
}

// Notifications are optional: answering that page, either way, ends onboarding.
private fun Map<String, Boolean>.isOptionalOnly() = keys == setOf(Manifest.permission.POST_NOTIFICATIONS)

private fun Map<String, Boolean>.isDenial() = !isOptionalOnly() && values.any { !it }
