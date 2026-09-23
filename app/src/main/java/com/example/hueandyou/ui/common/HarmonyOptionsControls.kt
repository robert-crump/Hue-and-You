package com.example.hueandyou.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel

/**
 * Wheel dropdown and Faithful/Softened toggle shared by the live Match Colors for an Object
 * result step and the History detail screen's OBJECT branch - both recompute [HarmonyResultBody]
 * immediately from the selection via [wheel]/[balance], and persist it separately (as the last
 * used wheel/balance on the History entry) through [onWheelChange]/[onBalanceChange].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HarmonyOptionsControls(
    wheel: HarmonyWheel,
    balance: HarmonyBalance,
    onWheelChange: (HarmonyWheel) -> Unit,
    onBalanceChange: (HarmonyBalance) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = stringResource(harmonyWheelLabel(wheel)),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.harmony_wheel_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                HarmonyWheel.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(stringResource(harmonyWheelLabel(option))) },
                        onClick = {
                            expanded = false
                            onWheelChange(option)
                        }
                    )
                }
            }
        }

        SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 16.dp).fillMaxWidth()) {
            HarmonyBalance.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = balance == option,
                    onClick = { onBalanceChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = HarmonyBalance.entries.size),
                ) {
                    Text(stringResource(harmonyBalanceLabel(option)))
                }
            }
        }
    }
}

internal fun harmonyWheelLabel(wheel: HarmonyWheel): Int = when (wheel) {
    HarmonyWheel.TRADITIONAL -> R.string.harmony_wheel_traditional
    HarmonyWheel.SCREEN -> R.string.harmony_wheel_screen
    HarmonyWheel.PERCEPTUAL -> R.string.harmony_wheel_perceptual
}

internal fun harmonyBalanceLabel(balance: HarmonyBalance): Int = when (balance) {
    HarmonyBalance.FAITHFUL -> R.string.harmony_balance_faithful
    HarmonyBalance.SOFTENED -> R.string.harmony_balance_softened
}
