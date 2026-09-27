package com.example.hueandyou.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.parseHexColor

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val HEX_LENGTH = 6

/**
 * Normalizes raw text-field input to at most six uppercase hex digits, dropping everything else
 * (including a pasted leading `#`, which the field shows as a fixed prefix instead).
 */
internal fun sanitizeHexInput(raw: String): String =
    raw.uppercase().filter { it in HEX_DIGITS }.take(HEX_LENGTH)

/**
 * The one "add a color" dialog: a `#`-prefixed hex field with a live preview circle. Add stays
 * disabled until the input is a complete six-digit hex code, so there's no error state.
 */
@Composable
internal fun HexColorDialog(
    title: String,
    onDismiss: () -> Unit,
    onAdd: (argb: Int) -> Unit,
) {
    var hex by rememberSaveable { mutableStateOf("") }
    val argb = parseHexColor(hex)
    val submit = {
        if (argb != null) {
            onAdd(argb)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = sanitizeHexInput(it) },
                    label = { Text(stringResource(R.string.add_color_dialog_hex_label)) },
                    prefix = { Text("#") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                val previewModifier = Modifier.padding(start = 16.dp)
                if (argb != null) {
                    ColorCircle(argb = argb, size = PREVIEW_SIZE, modifier = previewModifier)
                } else {
                    Box(
                        modifier = previewModifier
                            .size(PREVIEW_SIZE)
                            .border(
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                CircleShape
                            )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = argb != null) {
                Text(stringResource(R.string.dialog_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

private val PREVIEW_SIZE = 40.dp
