// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ble

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Android 12+ scans with BLUETOOTH_SCAN (declared neverForLocation), so only
 * Android 11 and older still need location, and there it must be fine location.
 */
fun requiredBlePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/**
 * What the onboarding wizard walks through: the required permissions plus,
 * on Android 13+, notifications. Notifications are optional, so they are not
 * part of [requiredBlePermissions] and never block scanning.
 */
fun onboardingPermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requiredBlePermissions() + Manifest.permission.POST_NOTIFICATIONS
    } else {
        requiredBlePermissions()
    }

fun missingOnboardingPermissions(context: Context): List<String> =
    onboardingPermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

fun missingBlePermissions(context: Context): List<String> =
    requiredBlePermissions().filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

fun hasBlePermissions(context: Context): Boolean = missingBlePermissions(context).isEmpty()

fun isLocationEnabled(context: Context): Boolean {
    // Android 12+ scans without location, so the location switch is irrelevant.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return true
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        locationManager.isLocationEnabled
    } else {
        // LocationManager.isLocationEnabled requires API 28; on older versions
        // "location is on" is approximated by any provider being enabled.
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}

/**
 * True when Android will no longer show the permission dialog: every required
 * permission is denied with "don't ask again", so the app must open settings.
 */
fun shouldOpenAppSettings(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    return requiredBlePermissions().none {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}
