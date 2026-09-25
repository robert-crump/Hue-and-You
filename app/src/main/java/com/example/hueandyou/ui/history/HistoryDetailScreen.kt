package com.example.hueandyou.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.ui.common.ColorChipRow
import com.example.hueandyou.ui.common.HarmonyResultBody
import com.example.hueandyou.ui.common.InfoDialog
import com.example.hueandyou.ui.common.PaletteResultBody
import com.example.hueandyou.ui.common.PhotoResultSection

private const val PHOTO_MAX_HEIGHT_FRACTION = 0.35f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    entryId: Long,
    onNavigateBack: () -> Unit,
    viewModel: HistoryDetailViewModel = viewModel(
        factory = HistoryDetailViewModel.factory(LocalContext.current, entryId)
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDisclaimer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back)
                        )
                    }
                },
                actions = {
                    if (uiState is HistoryDetailUiState.Loaded) {
                        IconButton(onClick = { showDisclaimer = true }) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = stringResource(R.string.harmony_disclaimer_content_description)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is HistoryDetailUiState.Loading -> LoadingStep(innerPadding = innerPadding)
            is HistoryDetailUiState.NotFound -> Unit
            is HistoryDetailUiState.Loaded -> LoadedContent(
                state = state,
                innerPadding = innerPadding,
                onRename = viewModel::rename,
                onPickCandidate = viewModel::pickCandidate,
            )
        }

        if (showDisclaimer) {
            InfoDialog(
                title = stringResource(R.string.harmony_disclaimer_title),
                text = stringResource(R.string.rate_clothing_best_guess_disclaimer),
                onDismiss = { showDisclaimer = false },
            )
        }
    }
}

@Composable
private fun LoadingStep(innerPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun LoadedContent(
    state: HistoryDetailUiState.Loaded,
    innerPadding: PaddingValues,
    onRename: (String) -> Unit,
    onPickCandidate: (Int) -> Unit,
) {
    val entry = state.entry
    var name by rememberSaveable(entry.id) { mutableStateOf(entry.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(innerPadding)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                onRename(it)
            },
            label = { Text(stringResource(R.string.history_entry_name_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        PhotoResultSection(
            photo = state.photo,
            sampleX = entry.sampleX,
            sampleY = entry.sampleY,
            maxHeightFraction = PHOTO_MAX_HEIGHT_FRACTION,
            onTap = null,
            modifier = Modifier.padding(top = 16.dp),
        )
        ColorChipRow(
            currentArgb = entry.calibratedArgb,
            alternativesArgb = state.alternativesArgb,
            onPick = onPickCandidate,
            modifier = Modifier.padding(top = 16.dp),
        )
        when (entry.type) {
            HistoryEntryType.CLOTHING -> PaletteResultBody(argb = entry.calibratedArgb, score = entry.score)
            HistoryEntryType.OBJECT -> HarmonyResultBody(
                inputColorArgb = entry.calibratedArgb,
                wheel = requireNotNull(entry.wheel),
                balance = requireNotNull(entry.balance),
            )
        }
    }
}
