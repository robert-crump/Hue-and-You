package com.example.hueandyou.ui.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.formatHexColor
import com.example.hueandyou.data.profile.ColorKind
import com.example.hueandyou.data.profile.MAX_COLORS_PER_KIND
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.ui.common.ColorCircle
import com.example.hueandyou.ui.common.HexColorDialog

private const val GRID_COLUMNS = 5
private const val GRID_ROWS = MAX_COLORS_PER_KIND / GRID_COLUMNS
private const val BADGE_FRACTION = 0.4f
private val GRID_GAP = 8.dp
private val HORIZONTAL_PADDING = 16.dp
private val MIN_CIRCLE_SIZE = 32.dp
private val MAX_CIRCLE_SIZE = 56.dp
private val NAME_AREA_HEIGHT = 72.dp
private val ADD_ICON_SIZE = 32.dp

/**
 * Everything on the screen that isn't a grid row: name field (72), import button (52), two
 * section headers with their padding (2 x 56) and bottom padding (16). An estimate, not a
 * measurement - the column still scrolls as a safety net if it's off.
 */
private val FIXED_CONTENT_HEIGHT = 252.dp

/**
 * The circle size for the worst case of two full 5x5 grids, so circles don't resize as colors
 * come and go: whatever height is left for the grid rows, split over ten rows, but never wider
 * than a grid column and clamped to 32-56dp.
 */
internal fun worstCaseCircleSize(maxWidth: Dp, maxHeight: Dp): Dp {
    val rows = GRID_ROWS * 2
    val byHeight = (maxHeight - FIXED_CONTENT_HEIGHT - GRID_GAP * (rows - 2)) / rows
    val byWidth = (maxWidth - HORIZONTAL_PADDING * 2) / GRID_COLUMNS - GRID_GAP
    return minOf(byHeight, byWidth).coerceIn(MIN_CIRCLE_SIZE, MAX_CIRCLE_SIZE)
}

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
    val isEditing by viewModel.isEditing.collectAsState()
    val pendingRemovals by viewModel.pendingRemovals.collectAsState()
    val draftName by viewModel.draftName.collectAsState()
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var addColorKind by remember { mutableStateOf<ColorKind?>(null) }

    BackHandler(enabled = isEditing) { viewModel.cancelEditing() }

    val currentProfile = profile
    Scaffold(
        topBar = {
            if (isEditing) {
                TopAppBar(
                    title = { Text(stringResource(R.string.profile_editor_editing_title)) },
                    navigationIcon = {
                        IconButton(onClick = viewModel::cancelEditing) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.profile_editor_cancel_edit_content_description
                                )
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::commitEditing, enabled = draftName.isNotBlank()) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(
                                    R.string.profile_editor_confirm_edit_content_description
                                )
                            )
                        }
                    }
                )
            } else {
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
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(
                                    R.string.profile_editor_edit_content_description
                                )
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.profile_editor_delete_content_description)
                            )
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        if (currentProfile == null) {
            return@Scaffold
        }
        BoxWithConstraints(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            val circleSize = worstCaseCircleSize(maxWidth, maxHeight)
            // Designed to fit without scrolling; scrolls only for over-cap legacy lists, very
            // small screens or large font scales.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp)
            ) {
                NameArea(
                    name = currentProfile.name,
                    draftName = draftName,
                    isEditing = isEditing,
                    onDraftNameChange = viewModel::updateDraftName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = 8.dp)
                )

                if (!isEditing) {
                    FilledTonalButton(
                        onClick = onNavigateToImportPalette,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = 12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.profile_editor_import_palette))
                    }
                }

                ColorSection(
                    title = stringResource(R.string.profile_editor_best_colors_title),
                    colors = currentProfile.bestColors.filterNot { it.id in pendingRemovals },
                    circleSize = circleSize,
                    isEditing = isEditing,
                    onAddClick = { addColorKind = ColorKind.BEST },
                    onRemoveColor = viewModel::stageRemoval
                )

                ColorSection(
                    title = stringResource(R.string.profile_editor_avoid_colors_title),
                    colors = currentProfile.avoidColors.filterNot { it.id in pendingRemovals },
                    circleSize = circleSize,
                    isEditing = isEditing,
                    onAddClick = { addColorKind = ColorKind.AVOID },
                    onRemoveColor = viewModel::stageRemoval
                )
            }
        }
    }

    addColorKind?.let { kind ->
        HexColorDialog(
            title = stringResource(
                if (kind == ColorKind.BEST) {
                    R.string.add_color_dialog_title_best
                } else {
                    R.string.add_color_dialog_title_avoid
                }
            ),
            onDismiss = { addColorKind = null },
            onAdd = { argb -> viewModel.addColor(kind, argb) }
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

/**
 * The profile name as a "Name" label over the name; in edit mode a text field takes its place in
 * the same fixed-height slot so nothing below jumps.
 */
@Composable
private fun NameArea(
    name: String,
    draftName: String,
    isEditing: Boolean,
    onDraftNameChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = modifier.heightIn(min = NAME_AREA_HEIGHT),
        contentAlignment = Alignment.CenterStart
    ) {
        if (isEditing) {
            OutlinedTextField(
                value = draftName,
                onValueChange = onDraftNameChange,
                label = { Text(stringResource(R.string.profile_editor_name_label)) },
                isError = draftName.isBlank(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Column {
                Text(
                    text = stringResource(R.string.profile_editor_name_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }
    }
}

@Composable
private fun ColorSection(
    title: String,
    colors: List<PaletteColor>,
    circleSize: Dp,
    isEditing: Boolean,
    onAddClick: () -> Unit,
    onRemoveColor: (Long) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = HORIZONTAL_PADDING, vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.profile_editor_color_count, colors.size, MAX_COLORS_PER_KIND),
                style = MaterialTheme.typography.labelLarge,
                color = if (colors.size > MAX_COLORS_PER_KIND) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (!isEditing) {
                IconButton(onClick = onAddClick, enabled = colors.size < MAX_COLORS_PER_KIND) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.profile_editor_add_color_content_description),
                        modifier = Modifier.size(ADD_ICON_SIZE)
                    )
                }
            }
        }
        if (colors.isEmpty()) {
            Text(
                text = stringResource(R.string.profile_editor_no_colors),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                colors.chunked(GRID_COLUMNS).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        row.forEach { color ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ColorCell(
                                    color = color,
                                    size = circleSize,
                                    isEditing = isEditing,
                                    onRemove = { onRemoveColor(color.id) }
                                )
                            }
                        }
                        repeat(GRID_COLUMNS - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * One grid circle. In edit mode it carries a Quick-Settings-style "-" badge, and tapping anywhere
 * on the circle (the badge alone would be too small a target) stages its removal.
 */
@Composable
private fun ColorCell(
    color: PaletteColor,
    size: Dp,
    isEditing: Boolean,
    onRemove: () -> Unit
) {
    Box(modifier = Modifier.size(size)) {
        if (isEditing) {
            val description = stringResource(
                R.string.profile_editor_remove_color_content_description,
                formatHexColor(color.argb)
            )
            ColorCircle(
                argb = color.argb,
                size = size,
                modifier = Modifier.semantics { contentDescription = description },
                onClick = onRemove
            )
            val badgeSize = size * BADGE_FRACTION
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = badgeSize / 4, y = -badgeSize / 4)
                    .size(badgeSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Remove,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(badgeSize * 0.8f)
                )
            }
        } else {
            ColorCircle(argb = color.argb, size = size)
        }
    }
}
