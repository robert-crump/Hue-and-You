package com.example.hueandyou.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.ui.common.HistoryThumbnail
import com.example.hueandyou.ui.common.verdictAccentColor
import com.example.hueandyou.ui.common.verdictIcon
import com.example.hueandyou.ui.common.verdictLabel
import com.example.hueandyou.ui.outfits.OutfitsTab
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the NavHost needs to swap its tab bar for History's contextual selection bar.
 * [onMakeOutfit] is null except on Clothes.
 */
data class HistorySelectionBarState(
    val count: Int,
    val onClose: () -> Unit,
    val onDelete: () -> Unit,
    val onMakeOutfit: (() -> Unit)? = null,
)

@Composable
fun HistoryScreen(
    type: HistoryEntryType,
    scrollToTopRequested: Boolean,
    onScrolledToTop: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSnackbarHeightChange: (Int) -> Unit,
    onSelectionChange: (HistorySelectionBarState?) -> Unit,
    /** Clothes only: switch to Items with the Wardrobe filter, e.g. after a Scan wardrobe save. */
    showWardrobeRequested: Boolean = false,
    onShowedWardrobe: () -> Unit = {},
    /** Clothes only: whether Items (rather than Outfits) is the tab showing. */
    onItemsTabShownChange: (Boolean) -> Unit = {},
    /** Clothes only: switch to the Outfits tab, e.g. after an outfit is saved. */
    showOutfitsRequested: Boolean = false,
    onShowedOutfits: () -> Unit = {},
    /** Clothes only: opens the outfit editor pre-filled with these items, in list order. */
    onMakeOutfit: (List<Long>) -> Unit = {},
    onOpenOutfit: (Long) -> Unit = {},
    viewModel: HistoryViewModel = viewModel(key = type.name, factory = HistoryViewModel.factory(LocalContext.current, type)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSnackbarVisible by remember { mutableStateOf(false) }
    var snackbarHeightPx by remember { mutableIntStateOf(0) }
    val undoActionLabel = stringResource(R.string.history_entry_deleted_undo_action)
    val deletedMessage = uiState.pendingDeletion?.let {
        if (it.entryName != null) {
            stringResource(R.string.history_entry_deleted_message, it.entryName)
        } else {
            pluralStringResource(R.plurals.history_entries_deleted_message, it.count, it.count)
        }
    }

    BackHandler(enabled = uiState.isSelectionMode) { viewModel.exitSelectionMode() }

    // The contextual bar lives in the NavHost's Scaffold, so the selection is reported up to it.
    LaunchedEffect(uiState.isSelectionMode, uiState.selectedIds, uiState.entries) {
        onSelectionChange(
            if (uiState.isSelectionMode) {
                HistorySelectionBarState(
                    count = uiState.selectedIds.size,
                    onClose = viewModel::exitSelectionMode,
                    onDelete = viewModel::requestDeleteSelected,
                    onMakeOutfit = if (type == HistoryEntryType.CLOTHING) {
                        {
                            val selected = uiState.selectedIds
                            val ids = uiState.entries.filter { it.id in selected }.map { it.id }
                            viewModel.exitSelectionMode()
                            onMakeOutfit(ids)
                        }
                    } else {
                        null
                    }
                )
            } else {
                null
            }
        )
    }
    DisposableEffect(Unit) { onDispose { onSelectionChange(null) } }

    LaunchedEffect(uiState.pendingDeletion?.entryIds, deletedMessage) {
        val pending = uiState.pendingDeletion ?: return@LaunchedEffect
        val message = deletedMessage ?: return@LaunchedEffect
        isSnackbarVisible = true
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoActionLabel,
            duration = SnackbarDuration.Short
        )
        isSnackbarVisible = false
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete(pending)
        }
    }

    // Lets the parent lift its FAB clear of the snackbar while it is showing.
    LaunchedEffect(isSnackbarVisible, snackbarHeightPx) {
        onSnackbarHeightChange(if (isSnackbarVisible) snackbarHeightPx else 0)
    }
    DisposableEffect(Unit) { onDispose { onSnackbarHeightChange(0) } }

    // Clothes only: Items (this list) or Outfits.
    var showOutfits by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showOutfits) { onItemsTabShownChange(!showOutfits) }

    LaunchedEffect(showWardrobeRequested) {
        if (!showWardrobeRequested) return@LaunchedEffect
        showOutfits = false
        viewModel.setWardrobeFilter(WardrobeFilter.WARDROBE)
        onShowedWardrobe()
    }

    LaunchedEffect(showOutfitsRequested) {
        if (!showOutfitsRequested) return@LaunchedEffect
        viewModel.exitSelectionMode()
        showOutfits = true
        onShowedOutfits()
    }

    LaunchedEffect(scrollToTopRequested) {
        if (!scrollToTopRequested) return@LaunchedEffect
        // A new result was just saved: show it, even from the Outfits tab.
        showOutfits = false
        listState.scrollToItem(0)
        onScrolledToTop()
    }

    val entryList = @Composable {
        if (uiState.isEmpty) {
            HistoryEmptyState()
        } else {
            Column(Modifier.fillMaxSize()) {
                if (type == HistoryEntryType.CLOTHING) {
                    WardrobeFilterRow(selected = uiState.wardrobeFilter, onSelect = viewModel::setWardrobeFilter)
                }
                if (uiState.entries.isEmpty()) {
                    Text(
                        text = stringResource(R.string.clothes_filter_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    )
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            isSelected = entry.id in uiState.selectedIds,
                            onClick = {
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelected(entry.id)
                                } else {
                                    onOpenEntry(entry.id)
                                }
                            },
                            onLongClick = {
                                if (!uiState.isSelectionMode) viewModel.enterSelectionMode(entry.id)
                            }
                        )
                    }
                }
            }
        }
    }

    Scaffold(snackbarHost = {
        SnackbarHost(snackbarHostState, modifier = Modifier.onSizeChanged { snackbarHeightPx = it.height })
    }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (type == HistoryEntryType.CLOTHING) {
                Column(Modifier.fillMaxSize()) {
                    PrimaryTabRow(selectedTabIndex = if (showOutfits) 1 else 0) {
                        Tab(
                            selected = !showOutfits,
                            onClick = { showOutfits = false },
                            text = { Text(stringResource(R.string.clothes_tab_items)) }
                        )
                        Tab(
                            selected = showOutfits,
                            onClick = {
                                viewModel.exitSelectionMode()
                                showOutfits = true
                            },
                            text = { Text(stringResource(R.string.clothes_tab_outfits)) }
                        )
                    }
                    if (showOutfits) OutfitsTab(onOpenOutfit = onOpenOutfit) else entryList()
                }
            } else {
                entryList()
            }
        }
    }

    uiState.deleteConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteConfirmation,
            title = {
                Text(
                    pluralStringResource(
                        R.plurals.history_delete_confirm_title, confirmation.itemCount, confirmation.itemCount
                    )
                )
            },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.history_delete_used_in_outfits, confirmation.outfitCount, confirmation.outfitCount
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) { Text(stringResource(R.string.history_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteConfirmation) { Text(stringResource(R.string.dialog_cancel)) }
            },
        )
    }
}

/** All / Wardrobe / Not owned, above the Clothes items. */
@Composable
private fun WardrobeFilterRow(selected: WardrobeFilter, onSelect: (WardrobeFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        WardrobeFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                WardrobeFilter.ALL -> R.string.clothes_filter_all
                                WardrobeFilter.WARDROBE -> R.string.clothes_filter_wardrobe
                                WardrobeFilter.NOT_OWNED -> R.string.clothes_filter_not_owned
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun HistoryEntryRow(entry: HistoryEntry, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    ThumbnailRow(
        gap = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        thumbnail = {
            Box(Modifier.fillMaxSize()) {
                HistoryThumbnail(entry.thumbnailPath)
                if (isSelected) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = stringResource(R.string.history_entry_selected_content_description),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(18.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                    )
                }
            }
        },
        trailing = if (entry.type == HistoryEntryType.CLOTHING) {
            { ClothingVerdictIcon(entry) }
        } else {
            null
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SummaryCircle(entry.calibratedArgb, nameCircleSize())
                Spacer(Modifier.width(NAME_CIRCLE_GAP))
                Text(
                    text = entry.name,
                    style = NameStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatHistoryTimestamp(entry.createdAt),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
    }
}

/**
 * [content] in the middle, on the left a square [thumbnail] exactly as tall as [content], and on
 * the right an optional square [trailing] of the same size. Every line of [content] is
 * single-line, so its height doesn't depend on its width and can be read up front to size the
 * squares.
 */
@Composable
private fun ThumbnailRow(
    gap: Dp,
    modifier: Modifier = Modifier,
    thumbnail: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Layout(
        contents = listOf(thumbnail, trailing ?: {}, content),
        modifier = modifier
    ) { (thumbnails, trailings, contents), constraints ->
        val gapPx = gap.roundToPx()
        val body = contents.single()
        val side = body.minIntrinsicHeight(constraints.maxWidth)
        val squares = if (trailings.isEmpty()) 1 else 2
        val bodyPlaceable = body.measure(
            Constraints(maxWidth = (constraints.maxWidth - squares * (side + gapPx)).coerceAtLeast(0))
        )
        val height = bodyPlaceable.height
        val thumbnailPlaceables = thumbnails.map { it.measure(Constraints.fixed(height, height)) }
        val trailingPlaceables = trailings.map { it.measure(Constraints.fixed(height, height)) }
        layout(constraints.maxWidth, height) {
            thumbnailPlaceables.forEach { it.place(0, 0) }
            bodyPlaceable.place(height + gapPx, 0)
            trailingPlaceables.forEach { it.place(constraints.maxWidth - height, 0) }
        }
    }
}

/**
 * The clothing verdict as a bare icon in its accent color - same glyph as the Settings legend, but
 * quiet enough not to outweigh the name. TalkBack reads the verdict's name.
 */
@Composable
private fun ClothingVerdictIcon(entry: HistoryEntry) {
    val verdict = remember(entry.score) { ClothingVerdict.forScore(entry.score) }
    val description = stringResource(
        R.string.history_clothing_verdict_content_description,
        stringResource(verdictLabel(verdict))
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            verdictIcon(verdict),
            contentDescription = null,
            tint = verdictAccentColor(verdict),
            modifier = Modifier
                .size(VERDICT_ICON_SIZE)
                .clearAndSetSemantics { contentDescription = description }
        )
    }
}

private val VERDICT_ICON_SIZE = 24.dp
private val NAME_CIRCLE_GAP = 8.dp

private val NameStyle: TextStyle
    @Composable get() = MaterialTheme.typography.headlineSmall

/** As tall as the entry name's text, so the circle scales with the system font size. */
@Composable
private fun nameCircleSize(): Dp = with(LocalDensity.current) { NameStyle.fontSize.toDp() }

/**
 * Display-only swatch; the ring keeps white/black picks visible. [size] null fills the parent's
 * constraints instead.
 */
@Composable
private fun SummaryCircle(argb: Int, size: Dp? = null) {
    Box(
        (if (size != null) Modifier.size(size) else Modifier.fillMaxSize())
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

private val historyTimestampFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.getDefault())

private fun formatHistoryTimestamp(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(historyTimestampFormatter)

@Composable
private fun HistoryEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.History,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.history_empty_state_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.history_empty_state_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
