// SPDX-License-Identifier: AGPL-3.0-only
package com.dzid26.teslable.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dzid26.teslable.R
import com.dzid26.teslable.core.units.EfficiencyUnit

/** One radio choice in a Settings card: the whole row selects it. */
@Composable
internal fun OptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The Settings text for an efficiency choice. */
@StringRes
internal fun efficiencyLabel(unit: EfficiencyUnit): Int =
    when (unit) {
        EfficiencyUnit.ENERGY_PER_DISTANCE -> R.string.settings_efficiency_wh
        EfficiencyUnit.ENERGY_PER_100_DISTANCE -> R.string.settings_efficiency_per_100
        EfficiencyUnit.DISTANCE_PER_ENERGY -> R.string.settings_efficiency_inverse
    }
