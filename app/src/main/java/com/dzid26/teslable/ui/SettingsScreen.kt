// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.BuildConfig
import com.dzid26.teslable.EnergyPriceStore
import com.dzid26.teslable.R
import com.dzid26.teslable.ble.PairingKeyStore
import com.dzid26.teslable.ble.Vehicle
import java.text.NumberFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    keyStore: PairingKeyStore,
    vehicles: List<Vehicle>,
    onClearPairingCache: () -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    // Injectable for the Paparazzi snapshots: the version line would otherwise
    // differ on every commit (it names the commit), flagging Settings as
    // changed on every pull request. Production uses the real version.
    versionName: String = BuildConfig.VERSION_NAME,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val priceStore = remember { EnergyPriceStore(context) }

    var backupEnabled by remember { mutableStateOf(keyStore.isBackupEnabled()) }
    // Android 11 and below always leave pairing_key.xml out of backups
    // (backup_rules.xml), so turning backup on would only drop Keystore encryption.
    val backupSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scope = rememberCoroutineScope()
    // One key per car; read them when Settings opens (pairing happens elsewhere).
    // Key IDs come from the stored public keys, so no private key is decrypted.
    val vehicleKeys =
        remember(vehicles) {
            vehicles.mapNotNull { vehicle ->
                keyStore
                    .keyId(vehicle.bleName)
                    ?.toHex()
                    ?.let { id -> vehicle.title to id }
            }
        }
    var showEnableDialog by remember { mutableStateOf(false) }
    var showDisableDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    // Re-saving every key is Keystore work, so it runs off the main thread;
    // the switch then shows the mode that was actually stored.
    fun changeBackup(enabled: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { keyStore.setBackupEnabled(enabled) } }
            backupEnabled = keyStore.isBackupEnabled()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(16.dp),
        ) {
            VehicleKeysCard(
                backupEnabled = backupEnabled,
                backupSupported = backupSupported,
                onToggleBackup = { checked ->
                    if (checked) {
                        showEnableDialog = backupSupported
                    } else {
                        showDisableDialog = true
                    }
                },
            )
            Spacer(Modifier.height(16.dp))
            EnergyPriceCard(priceStore = priceStore)
            Spacer(Modifier.height(16.dp))
            PairingCard(
                keys = vehicleKeys,
                onClearCache = { showClearCacheDialog = true },
            )
            Spacer(Modifier.height(16.dp))
            AboutCard(onOpenAbout = onOpenAbout, versionName = versionName)
        }
    }

    if (showEnableDialog) {
        EnableBackupDialog(
            onConfirm = {
                showEnableDialog = false
                changeBackup(true)
            },
            onDismiss = { showEnableDialog = false },
        )
    }

    if (showDisableDialog) {
        DisableBackupDialog(
            onConfirm = {
                showDisableDialog = false
                changeBackup(false)
            },
            onDismiss = { showDisableDialog = false },
        )
    }

    if (showClearCacheDialog) {
        ClearPairingCacheDialog(
            onConfirm = {
                showClearCacheDialog = false
                onClearPairingCache()
            },
            onDismiss = { showClearCacheDialog = false },
        )
    }
}

@Composable
private fun VehicleKeysCard(
    backupEnabled: Boolean,
    backupSupported: Boolean,
    onToggleBackup: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_vehicle_keys),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = backupEnabled && backupSupported,
                    onCheckedChange = onToggleBackup,
                    enabled = backupSupported,
                )
            }
            if (!backupSupported) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_backup_needs_12),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    if (backupEnabled && backupSupported) {
                        stringResource(R.string.settings_backup_state_on)
                    } else if (backupEnabled) {
                        stringResource(R.string.settings_backup_state_unencrypted)
                    } else {
                        stringResource(R.string.settings_backup_state_off)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (backupEnabled && backupSupported) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text =
                        stringResource(R.string.settings_backup_schedule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The electricity price that turns a charge session's energy into a cost on the car view. */
@Composable
private fun EnergyPriceCard(priceStore: EnergyPriceStore) {
    val currency =
        remember {
            NumberFormat
                .getCurrencyInstance()
                .currency
                ?.symbol
                .orEmpty()
        }
    var text by remember { mutableStateOf(priceStore.pricePerKwh()?.toString().orEmpty()) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_energy_cost),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    text = input
                    // A comma decimal is common; a half-typed or empty value clears the price.
                    priceStore.setPricePerKwh(input.replace(',', '.').toFloatOrNull())
                },
                label = { Text(stringResource(R.string.settings_price_per_kwh, currency)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_energy_cost_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PairingCard(
    keys: List<Pair<String, String>>,
    onClearCache: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_pairing),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            if (keys.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_keys),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                keys.forEach { (title, id) ->
                    Text(
                        text = "$title · ${id.take(8)}…",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text =
                    stringResource(R.string.settings_pairing_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClearCache,
                enabled = keys.isNotEmpty(),
            ) {
                Text(stringResource(R.string.settings_clear_cache_button))
            }
        }
    }
}

@Composable
private fun AboutCard(
    onOpenAbout: () -> Unit,
    versionName: String,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenAbout),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_about),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_version, versionName),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_about_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EnableBackupDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_enable_backup_title)) },
        text = {
            Text(
                stringResource(R.string.settings_enable_backup_body),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_enable_backup_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun DisableBackupDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_disable_backup_title)) },
        text = {
            Text(
                stringResource(R.string.settings_disable_backup_body),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_disable_backup_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ClearPairingCacheDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_clear_cache_title)) },
        text = {
            Text(
                stringResource(R.string.settings_clear_cache_body),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.settings_clear_cache_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private fun ByteArray.toHex(): String = joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
