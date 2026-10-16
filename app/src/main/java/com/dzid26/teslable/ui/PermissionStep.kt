// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.Manifest
import androidx.annotation.StringRes
import com.dzid26.teslable.R

/** One permission the app needs, with the explanation shown before asking. */
data class PermissionStep(
    val permission: String,
    @StringRes val title: Int,
    @StringRes val why: Int,
)

/** Builds the wizard steps for the permissions this Android version needs. */
fun permissionSteps(permissions: List<String>): List<PermissionStep> =
    permissions.mapNotNull { permission ->
        when (permission) {
            Manifest.permission.BLUETOOTH_SCAN ->
                PermissionStep(
                    permission = permission,
                    title = R.string.perm_scan_title,
                    why = R.string.perm_scan_why,
                )

            Manifest.permission.BLUETOOTH_CONNECT ->
                PermissionStep(
                    permission = permission,
                    title = R.string.perm_connect_title,
                    why = R.string.perm_connect_why,
                )

            Manifest.permission.ACCESS_FINE_LOCATION ->
                PermissionStep(
                    permission = permission,
                    title = R.string.perm_location_title,
                    why = R.string.perm_location_why,
                )

            else -> null
        }
    }
