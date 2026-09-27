package com.example.hueandyou.ui.history

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.HarmonyEngine
import com.example.hueandyou.colorspace.HarmonyRelationship
import com.example.hueandyou.colorspace.argbToHct
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.ui.common.verdictLabel
import com.example.hueandyou.ui.theme.LocalSuccessColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the NavHost needs to swap its tab bar for History's contextual selection bar. */
data class HistorySelectionBarState(val count: Int, val onClose: () -> Unit, val onDelete: () -> Unit)

@Composable
fun HistoryScreen(
    type: HistoryEntryType,
    scrollToTopRequested: Boolean,
    onScrolledToTop: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSnackbarHeightChange: (Int) -> Unit,
    onSelectionChange: (HistorySelectionBarState?) -> Unit,
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
    LaunchedEffect(uiState.isSelectionMode, uiState.selectedIds.size) {
        onSelectionChange(
            if (uiState.isSelectionMode) {
                HistorySelectionBarState(
                    count = uiState.selectedIds.size,
                    onClose = viewModel::exitSelectionMode,
                    onDelete = viewModel::deleteSelected
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

    LaunchedEffect(scrollToTopRequested) {
        if (!scrollToTopRequested) return@LaunchedEffect
        listState.scrollToItem(0)
        onScrolledToTop()
    }

    Scaffold(snackbarHost = {
        SnackbarHost(snackbarHostState, modifier = Modifier.onSizeChanged { snackbarHeightPx = it.height })
    }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isEmpty) {
                HistoryEmptyState()
            } else {
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
}

@Composable
private fun HistoryEntryRow(entry: HistoryEntry, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
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
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
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
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(16.dp)
        ) {
            Text(text = entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            when (entry.type) {
                HistoryEntryType.CLOTHING -> ClothingSummaryLine(entry)
                HistoryEntryType.OBJECT -> ObjectSummaryLine(entry)
            }
            Text(text = formatHistoryTimestamp(entry.createdAt), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Chosen color circle, separator, then the verdict text in its verdict color. */
@Composable
private fun ClothingSummaryLine(entry: HistoryEntry) {
    val verdict = remember(entry.score) { ClothingVerdict.forScore(entry.score) }
    val verdictText = stringResource(verdictLabel(verdict))
    val verdictColor = when (verdict) {
        ClothingVerdict.YES -> LocalSuccessColors.current.content
        ClothingVerdict.AVOID -> MaterialTheme.colorScheme.error
        ClothingVerdict.NEITHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val description = stringResource(R.string.history_clothing_summary_content_description, verdictText)
    SummaryLine(modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        SummaryCircle(entry.calibratedArgb)
        SummarySeparator()
        Text(text = verdictText, style = MaterialTheme.typography.bodySmall, color = verdictColor)
    }
}

/**
 * Chosen color, then the complementary, analogous and tonal suggestions for it - recomputed from
 * the entry's own wheel/balance, and Tonal only for a neutral color, same as History detail.
 */
@Composable
private fun ObjectSummaryLine(entry: HistoryEntry) {
    val groups = remember(entry.calibratedArgb, entry.wheel, entry.balance) {
        val wheel = entry.wheel
        val balance = entry.balance
        if (wheel == null || balance == null) return@remember emptyList()
        val suggestions = HarmonyEngine.generate(entry.calibratedArgb, wheel, balance)
        val shown = if (HarmonyEngine.isNeutral(argbToHct(entry.calibratedArgb).chroma)) {
            listOf(HarmonyRelationship.TONAL)
        } else {
            listOf(HarmonyRelationship.COMPLEMENTARY, HarmonyRelationship.ANALOGOUS, HarmonyRelationship.TONAL)
        }
        shown.map { relationship -> suggestions.single { it.relationship == relationship }.colors }
    }
    val description = stringResource(R.string.history_object_summary_content_description)
    SummaryLine(modifier = Modifier.clearAndSetSemantics { contentDescription = description }) {
        SummaryCircle(entry.calibratedArgb)
        groups.forEach { colors ->
            SummarySeparator()
            colors.forEachIndexed { index, argb ->
                if (index > 0) Spacer(Modifier.width(3.dp))
                SummaryCircle(argb)
            }
        }
    }
}

@Composable
private fun SummaryLine(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) { content() }
}

/** Display-only swatch sized to sit inside a bodySmall line; the ring keeps white/black picks visible. */
@Composable
private fun SummaryCircle(argb: Int) {
    Box(
        Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(Color(argb))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
    )
}

@Composable
private fun SummarySeparator() {
    Text(
        text = " | ",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HistoryThumbnail(path: String) {
    val imageBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    imageBitmap?.let { bitmap ->
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        )
    }
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
