package com.example.hueandyou.ui.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.formatHexColor
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.MoveDirection
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.ui.common.ColorCircle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToImportPalette: () -> Unit,
    viewModel: ProfileEditorViewModel = viewModel(
        factory = ProfileEditorViewModel.factory(LocalContext.current, profileId)
    )
) {
    val profile by viewModel.profile.collectAsState()
    var name by rememberSaveable(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var addColorKind by remember { mutableStateOf<ColorKind?>(null) }

    val currentProfile = profile
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.profile_editor_delete_content_description)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (currentProfile == null) {
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    viewModel.renameProfile(it)
                },
                label = { Text(stringResource(R.string.profile_editor_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            )

            ColorSection(
                title = stringResource(R.string.profile_editor_best_colors_title),
                colors = currentProfile.bestColors,
                onAddClick = { addColorKind = ColorKind.BEST },
                onRemoveColor = { viewModel.removeColor(it) },
                onMoveColor = { colorId, direction -> viewModel.moveColor(colorId, direction) }
            )

            ColorSection(
                title = stringResource(R.string.profile_editor_avoid_colors_title),
                colors = currentProfile.avoidColors,
                onAddClick = { addColorKind = ColorKind.AVOID },
                onRemoveColor = { viewModel.removeColor(it) },
                onMoveColor = { colorId, direction -> viewModel.moveColor(colorId, direction) }
            )

            TextButton(
                onClick = onNavigateToImportPalette,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(stringResource(R.string.profile_editor_import_palette))
            }
        }
    }

    addColorKind?.let { kind ->
        AddColorDialog(
            kind = kind,
            onDismiss = { addColorKind = null },
            onAdd = { hex, invalidHexMessage -> viewModel.addColor(kind, hex, invalidHexMessage) }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_profile_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_profile_dialog_body,
                        currentProfile?.name.orEmpty()
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteProfile(onNavigateBack)
                }) {
                    Text(stringResource(R.string.delete_profile_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun ColorSection(
    title: String,
    colors: List<PaletteColor>,
    onAddClick: () -> Unit,
    onRemoveColor: (Long) -> Unit,
    onMoveColor: (colorId: Long, direction: MoveDirection) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAddClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.profile_editor_add_color_content_description)
                )
            }
        }
        if (colors.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_editor_no_colors),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                colors.forEachIndexed { index, color ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ColorCircle(argb = color.argb, size = 24.dp)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { onMoveColor(color.id, MoveDirection.UP) },
                            enabled = index > 0
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = stringResource(
                                    R.string.profile_editor_move_up_content_description
                                )
                            )
                        }
                        IconButton(
                            onClick = { onMoveColor(color.id, MoveDirection.DOWN) },
                            enabled = index < colors.lastIndex
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = stringResource(
                                    R.string.profile_editor_move_down_content_description
                                )
                            )
                        }
                        IconButton(onClick = { onRemoveColor(color.id) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.profile_editor_remove_color_content_description
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class ColorEntryMode { HEX, PICKER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddColorDialog(
    kind: ColorKind,
    onDismiss: () -> Unit,
    onAdd: (hex: String, invalidHexMessage: String) -> String?
) {
    var mode by rememberSaveable { mutableStateOf(ColorEntryMode.HEX) }
    var hex by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var hue by rememberSaveable { mutableStateOf(0f) }
    var saturation by rememberSaveable { mutableStateOf(1f) }
    var brightness by rememberSaveable { mutableStateOf(1f) }
    val invalidHexMessage = stringResource(R.string.add_color_dialog_invalid_hex)

    val pickedArgb = remember(hue, saturation, brightness) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))
    }
    val pickedHex = remember(pickedArgb) { formatHexColor(pickedArgb) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (kind == ColorKind.BEST) {
                        R.string.add_color_dialog_title_best
                    } else {
                        R.string.add_color_dialog_title_avoid
                    }
                )
            )
        },
        text = {
            Column {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = mode == ColorEntryMode.HEX,
                        onClick = { mode = ColorEntryMode.HEX },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text(stringResource(R.string.add_color_dialog_mode_hex))
                    }
                    SegmentedButton(
                        selected = mode == ColorEntryMode.PICKER,
                        onClick = { mode = ColorEntryMode.PICKER },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text(stringResource(R.string.add_color_dialog_mode_picker))
                    }
                }

                if (mode == ColorEntryMode.HEX) {
                    OutlinedTextField(
                        value = hex,
                        onValueChange = {
                            hex = it
                            error = null
                        },
                        label = { Text(stringResource(R.string.add_color_dialog_hex_label)) },
                        isError = error != null,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(pickedArgb))
                            )
                            Text(text = pickedHex, modifier = Modifier.padding(start = 12.dp))
                        }
                        Text(
                            text = stringResource(R.string.add_color_dialog_hue_label),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)
                        Text(
                            text = stringResource(R.string.add_color_dialog_saturation_label),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)
                        Text(
                            text = stringResource(R.string.add_color_dialog_brightness_label),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = 0f..1f)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val hexToAdd = if (mode == ColorEntryMode.PICKER) pickedHex else hex
                val result = onAdd(hexToAdd, invalidHexMessage)
                if (result == null) {
                    onDismiss()
                } else {
                    error = result
                }
            }) {
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
