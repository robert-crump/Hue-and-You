package com.example.hueandyou.ui.history

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.data.history.HistoryEntry
import com.example.hueandyou.data.history.HistoryEntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(
    onOpenEntry: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoActionLabel = stringResource(R.string.history_entry_deleted_undo_action)
    val deletedMessage = uiState.pendingDeletion?.let {
        stringResource(R.string.history_entry_deleted_message, it.entryName)
    }

    LaunchedEffect(uiState.pendingDeletion?.entryId, deletedMessage) {
        val pending = uiState.pendingDeletion ?: return@LaunchedEffect
        val message = deletedMessage ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = undoActionLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoDelete(pending.entryId)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (uiState.isEmpty) {
                HistoryEmptyState()
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.entries, key = { it.id }) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            onClick = { onOpenEntry(entry.id) },
                            onDeleteClick = { viewModel.deleteEntry(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEntryRow(entry: HistoryEntry, onClick: () -> Unit, onDeleteClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HistoryThumbnail(entry.thumbnailPath)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp)
        ) {
            Text(text = entry.name, style = MaterialTheme.typography.titleMedium)
            val subtitle = listOfNotNull(historyEntryTypeLabel(entry.type), entry.profileName)
                .joinToString(" · ")
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            Text(text = formatHistoryTimestamp(entry.createdAt), style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onDeleteClick) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.history_entry_delete_content_description)
            )
        }
    }
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

@Composable
private fun historyEntryTypeLabel(type: HistoryEntryType): String = stringResource(
    when (type) {
        HistoryEntryType.CLOTHING -> R.string.history_entry_type_clothing
        HistoryEntryType.OBJECT -> R.string.history_entry_type_object
    }
)

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
