package com.example.hueandyou.ui.outfits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.OutfitHarmonyLevel
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.ui.common.ColorCircle
import com.example.hueandyou.ui.common.HistoryThumbnail
import com.example.hueandyou.ui.common.ProfileDropdown
import com.example.hueandyou.ui.common.ResultNameDialog
import com.example.hueandyou.ui.common.clothingCategoryLabel

/**
 * Builds or edits an outfit: the item strip on top, then both live verdicts. Saving (the tick)
 * asks for a name first, as the result screens do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitEditorScreen(
    outfitId: Long?,
    initialEntryIds: List<Long>,
    onNavigateBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: OutfitEditorViewModel = viewModel(
        factory = OutfitEditorViewModel.factory(LocalContext.current, outfitId, initialEntryIds)
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    // The strip item the move arrows act on.
    var selectedItemId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinished()
    }

    val onBack: () -> Unit = { if (uiState.hasChanges) showDiscardDialog = true else onNavigateBack() }
    BackHandler(enabled = uiState.hasChanges && !uiState.isFinished, onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        uiState.name ?: stringResource(R.string.outfit_editor_new_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    if (!uiState.isNew) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.outfit_editor_delete_content_description),
                            )
                        }
                    }
                    IconButton(onClick = { showNameDialog = true }, enabled = uiState.canSave) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = stringResource(R.string.outfit_editor_save_content_description),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp),
        ) {
            val oddOneOutId = uiState.rating?.harmony?.oddOneOutIndex?.let { uiState.items.getOrNull(it)?.id }
            ItemStrip(
                items = uiState.items,
                selectedItemId = selectedItemId,
                oddOneOutId = oddOneOutId,
                canAddMore = uiState.canAddMore,
                onSelect = { id -> selectedItemId = if (selectedItemId == id) null else id },
                onRemove = { id ->
                    if (selectedItemId == id) selectedItemId = null
                    viewModel.removeItem(id)
                },
                onAddItems = { showPicker = true },
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                MoveControls(
                    items = uiState.items,
                    selectedItemId = selectedItemId,
                    onMove = viewModel::moveItem,
                )
                Text(
                    text = stringResource(R.string.outfit_editor_item_count, uiState.items.size, OUTFIT_MAX_ITEMS),
                    style = MaterialTheme.typography.labelLarge,
                )
                val selectedProfile = uiState.selectedProfile
                if (uiState.profiles.size >= 2 && selectedProfile != null) {
                    ProfileDropdown(
                        profiles = uiState.profiles,
                        selectedProfile = selectedProfile,
                        onSelect = viewModel::switchProfile,
                    )
                }
                val rating = uiState.rating
                if (rating == null) {
                    Text(
                        text = stringResource(R.string.outfit_editor_min_items),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SuitsYouCard(rating, uiState.items)
                    HarmonyCard(rating, uiState.items)
                }
            }
        }
    }

    if (showPicker) {
        ItemPickerSheet(
            picker = uiState.picker,
            chosenIds = uiState.items.map { it.id },
            canAddMore = uiState.canAddMore,
            onToggleItem = viewModel::toggleItem,
            onWardrobeOnlyChange = viewModel::setPickerWardrobeOnly,
            onCategoryChange = viewModel::setPickerCategory,
            onDismiss = { showPicker = false },
        )
    }

    if (showNameDialog) {
        ResultNameDialog(
            title = stringResource(R.string.outfit_editor_name_dialog_title),
            defaultName = uiState.defaultName,
            onSave = { name ->
                showNameDialog = false
                viewModel.save(name)
            },
            onDismiss = { showNameDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.outfit_editor_delete_title)) },
            text = { Text(stringResource(R.string.outfit_editor_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.history_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.outfit_editor_discard_title)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) { Text(stringResource(R.string.outfit_editor_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.outfit_editor_keep_editing))
                }
            },
        )
    }
}

/** The outfit's items in order, each removable, then an "Add items" card while there's room. */
@Composable
private fun ItemStrip(
    items: List<HistoryEntry>,
    selectedItemId: Long?,
    oddOneOutId: Long?,
    canAddMore: Boolean,
    onSelect: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onAddItems: () -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items, key = { it.id }) { item ->
            StripItem(
                item = item,
                isSelected = item.id == selectedItemId,
                isOddOneOut = item.id == oddOneOutId,
                onClick = { onSelect(item.id) },
                onRemove = { onRemove(item.id) },
            )
        }
        if (canAddMore) {
            item(key = "add") {
                OutlinedCard(onClick = onAddItems, modifier = Modifier.size(STRIP_ITEM_SIZE)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            stringResource(R.string.outfit_editor_add_items),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StripItem(
    item: HistoryEntry,
    isSelected: Boolean,
    isOddOneOut: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val borderColor = when {
        isOddOneOut -> MaterialTheme.colorScheme.error
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Column(modifier = Modifier.width(STRIP_ITEM_SIZE), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(STRIP_ITEM_SIZE)
                .border(3.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(3.dp)
                .clickable(onClick = onClick),
        ) {
            HistoryThumbnail(item.thumbnailPath)
            ColorCircle(
                argb = item.calibratedArgb,
                size = 20.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.outfit_editor_remove_item_content_description, item.name),
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape),
                )
            }
        }
        Text(
            text = if (isOddOneOut) stringResource(R.string.outfit_harmony_odd_one_out_badge) else item.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (isOddOneOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Earlier / later arrows for the selected strip item, or a hint on how to pick one. */
@Composable
private fun MoveControls(items: List<HistoryEntry>, selectedItemId: Long?, onMove: (Long, Int) -> Unit) {
    if (items.size < 2) return
    val index = items.indexOfFirst { it.id == selectedItemId }
    if (index < 0) {
        Text(
            text = stringResource(R.string.outfit_editor_select_item_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val item = items[index]
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onMove(item.id, -1) }, enabled = index > 0) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.outfit_editor_move_earlier_content_description, item.name),
            )
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        IconButton(onClick = { onMove(item.id, 1) }, enabled = index < items.lastIndex) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.outfit_editor_move_later_content_description, item.name),
            )
        }
    }
}

@Composable
private fun SuitsYouCard(rating: OutfitRating, items: List<HistoryEntry>) {
    VerdictCard(
        title = stringResource(R.string.outfit_suits_you_title),
        icon = { rating.suitsYou?.let { SuitsYouIcon(it.overall, CARD_ICON_SIZE) } },
    ) {
        val summary = rating.suitsYou
        if (summary == null) {
            Text(stringResource(R.string.outfit_suits_you_no_profile), style = MaterialTheme.typography.bodyMedium)
            return@VerdictCard
        }
        val best = pluralStringResource(
            R.plurals.outfit_suits_you_best, summary.bestCount, summary.bestCount, summary.itemCount
        )
        val line = if (summary.avoidCount > 0) {
            "$best, " + pluralStringResource(R.plurals.outfit_suits_you_avoid, summary.avoidCount, summary.avoidCount)
        } else {
            best
        }
        Text(line, style = MaterialTheme.typography.bodyMedium)
        if (summary.avoidCount > 0) {
            val names = summary.avoidIndices.mapNotNull { items.getOrNull(it)?.name }.joinToString(", ")
            Text(
                stringResource(R.string.outfit_suits_you_avoid_items, names),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HarmonyCard(rating: OutfitRating, items: List<HistoryEntry>) {
    val harmony = rating.harmony
    VerdictCard(
        title = stringResource(R.string.outfit_harmony_title),
        icon = { HarmonyIcon(harmony.level, CARD_ICON_SIZE) },
    ) {
        val level = stringResource(harmonyLabel(harmony.level))
        val scheme = harmony.scheme
        Text(
            // On Clashes the scheme is what the rest would fit, so it's only named on the odd-one-out line.
            if (scheme != null && harmony.level != OutfitHarmonyLevel.CLASHES) {
                stringResource(R.string.outfit_harmony_level_scheme, level, stringResource(schemeLabel(scheme)))
            } else {
                level
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (harmony.level == OutfitHarmonyLevel.CLASHES) {
            val oddOneOut = harmony.oddOneOutIndex?.let { items.getOrNull(it) }
            Text(
                if (oddOneOut != null) {
                    stringResource(R.string.outfit_harmony_odd_one_out, oddOneOut.name)
                } else {
                    stringResource(R.string.outfit_harmony_no_odd_one_out)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (oddOneOut != null) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun VerdictCard(title: String, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            icon()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                content()
            }
        }
    }
}

/** A grid of Clothes items to tap in or out of the outfit, filtered by Wardrobe (on at first) and category. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ItemPickerSheet(
    picker: OutfitPickerState,
    chosenIds: List<Long>,
    canAddMore: Boolean,
    onToggleItem: (Long) -> Unit,
    onWardrobeOnlyChange: (Boolean) -> Unit,
    onCategoryChange: (ClothingCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.outfit_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.outfit_editor_item_count, chosenIds.size, OUTFIT_MAX_ITEMS),
                    style = MaterialTheme.typography.labelLarge,
                )
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.outfit_picker_done)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = picker.wardrobeOnly,
                    onClick = { onWardrobeOnlyChange(!picker.wardrobeOnly) },
                    label = { Text(stringResource(R.string.outfit_picker_wardrobe)) },
                )
                ClothingCategory.entries.forEach { option ->
                    val selected = option == picker.category
                    FilterChip(
                        selected = selected,
                        onClick = { onCategoryChange(if (selected) null else option) },
                        label = { Text(stringResource(clothingCategoryLabel(option))) },
                    )
                }
            }
            if (picker.items.isEmpty()) {
                Text(
                    text = stringResource(R.string.outfit_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(96.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    modifier = Modifier.height(PICKER_GRID_HEIGHT),
                ) {
                    items(picker.items, key = { it.id }) { item ->
                        val isChosen = item.id in chosenIds
                        PickerItem(
                            item = item,
                            isChosen = isChosen,
                            isEnabled = isChosen || canAddMore,
                            onClick = { onToggleItem(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerItem(item: HistoryEntry, isChosen: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .alpha(if (isEnabled) 1f else 0.4f)
            .clickable(enabled = isEnabled, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            HistoryThumbnail(item.thumbnailPath)
            ColorCircle(
                argb = item.calibratedArgb,
                size = 20.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            )
            if (isChosen) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.history_entry_selected_content_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Text(item.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val STRIP_ITEM_SIZE = 88.dp
private val CARD_ICON_SIZE = 28.dp
private val PICKER_GRID_HEIGHT = 420.dp
