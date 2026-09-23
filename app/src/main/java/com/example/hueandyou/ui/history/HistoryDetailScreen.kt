package com.example.hueandyou.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.hueandyou.ui.common.HarmonyResultBody
import com.example.hueandyou.ui.common.PaletteResultBody

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    entryId: Long,
    onNavigateBack: () -> Unit,
    viewModel: HistoryDetailViewModel = viewModel(
        factory = HistoryDetailViewModel.factory(LocalContext.current, entryId)
    ),
) {
    val entry by viewModel.entry.collectAsState()

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
                }
            )
        }
    ) { innerPadding ->
        val current = entry ?: return@Scaffold
        var name by rememberSaveable(current.id) { mutableStateOf(current.name) }

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
                    viewModel.rename(it)
                },
                label = { Text(stringResource(R.string.history_entry_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            when (current.type) {
                HistoryEntryType.CLOTHING -> PaletteResultBody(argb = current.calibratedArgb, score = current.score)
                HistoryEntryType.OBJECT -> HarmonyResultBody(
                    inputColorsArgb = current.inputColorsArgb,
                    wheel = requireNotNull(current.wheel),
                    balance = requireNotNull(current.balance),
                )
            }
        }
    }
}
