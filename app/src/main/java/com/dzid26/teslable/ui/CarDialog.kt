// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.dzid26.teslable.R
import com.dzid26.teslable.ble.Vehicle
import com.dzid26.teslable.core.vehicle.decodeVin
import com.dzid26.teslable.core.vehicle.epaSearchUrl
import com.dzid26.teslable.protocol.TeslaNames
import java.util.Locale

private const val GREYED_ALPHA = 0.38f

/**
 * What the owner can say about a car: its VIN and its new-car rated range. The VIN is optional here, but
 * a typed one must hash to the car's advertised name, so a typo is caught before it is stored. The range
 * is copied from the EPA page the link opens for the VIN's model and year.
 */
@Composable
internal fun CarDialog(
    bleName: String,
    initialVin: String,
    initialRangeMiles: Float?,
    onSave: (vin: String?, factoryRangeMiles: Float?) -> Unit,
    onDismiss: () -> Unit,
) {
    var vinInput by remember { mutableStateOf(initialVin) }
    var rangeInput by remember { mutableStateOf(initialRangeMiles?.let { "%.0f".format(Locale.US, it) }.orEmpty()) }
    val normalized = Vehicle.normalizeVin(vinInput)
    val expected =
        if (normalized.length == Vehicle.VIN_LENGTH) {
            runCatching { TeslaNames.bleName(normalized) }.getOrNull()
        } else {
            null
        }
    val mismatch = normalized.length == Vehicle.VIN_LENGTH && expected != null && expected != bleName
    val vinValid = normalized.length == Vehicle.VIN_LENGTH && expected == bleName
    val range = rangeInput.replace(',', '.').toFloatOrNull()?.takeIf { it > 0f }
    val rangeValid = rangeInput.isBlank() || range != null
    val epaUrl = if (vinValid) decodeVin(normalized)?.let(::epaSearchUrl) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.car_edit_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = vinInput,
                    onValueChange = { vinInput = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.vin_label)) },
                    isError = mismatch,
                    supportingText = { Text(stringResource(vinHint(mismatch, vinValid))) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rangeInput,
                    onValueChange = { rangeInput = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    label = { Text(stringResource(R.string.car_range_label)) },
                    isError = !rangeValid,
                    supportingText = { Text(stringResource(R.string.car_range_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                EpaLookupButton(epaUrl)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(normalized.takeIf { it.isNotEmpty() }, range) },
                enabled = (normalized.isEmpty() || vinValid) && rangeValid,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** The EPA link, greyed until the VIN gives a model and year; a tap while grey says what is missing. */
@Composable
private fun EpaLookupButton(epaUrl: String?) {
    val context = LocalContext.current
    val colors =
        if (epaUrl != null) {
            ButtonDefaults.textButtonColors()
        } else {
            ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = GREYED_ALPHA),
            )
        }
    TextButton(
        onClick = {
            if (epaUrl != null) {
                openUrl(context, epaUrl)
            } else {
                Toast.makeText(context, R.string.car_range_needs_vin, Toast.LENGTH_SHORT).show()
            }
        },
        colors = colors,
    ) {
        Text(stringResource(R.string.car_range_lookup))
    }
}

private fun vinHint(
    mismatch: Boolean,
    valid: Boolean,
): Int =
    when {
        mismatch -> R.string.vin_mismatch
        valid -> R.string.vin_saved
        else -> R.string.vin_hint
    }
