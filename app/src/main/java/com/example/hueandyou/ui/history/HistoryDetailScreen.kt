package com.example.hueandyou.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.data.history.HistoryEntryType
import com.example.hueandyou.ui.common.ColorChipRow
import com.example.hueandyou.ui.common.HarmonyResultBody
import com.example.hueandyou.ui.common.InfoDialog
import com.example.hueandyou.ui.common.PaletteResultBody
import com.example.hueandyou.ui.common.PhotoResultSection

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
    var isRenaming by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val loaded = uiState as? HistoryDetailUiState.Loaded
    val editing = isRenaming && loaded != null

    fun startRenaming(name: String) {
        draft = TextFieldValue(name, selection = TextRange(0, name.length))
        isRenaming = true
    }

    fun commitRename() {
        if (draft.text.isBlank()) return
        viewModel.rename(draft.text)
        isRenaming = false
    }

    // While renaming, back cancels the edit instead of leaving the screen - same as the X.
    BackHandler(enabled = editing) { isRenaming = false }

    Scaffold(
        topBar = {
            if (editing) {
                RenameTopBar(
                    draft = draft,
                    onDraftChange = { draft = it },
                    onCancel = { isRenaming = false },
                    onConfirm = ::commitRename,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = loaded?.entry?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    },
                    actions = {
                        if (loaded != null) {
                            IconButton(onClick = { startRenaming(loaded.entry.name) }) {
                                Icon(
                                    Icons.Filled.Edit,
                                    contentDescription = stringResource(R.string.history_rename_content_description)
                                )
                            }
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
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is HistoryDetailUiState.Loading -> LoadingStep(innerPadding = innerPadding)
            is HistoryDetailUiState.NotFound -> Unit
            is HistoryDetailUiState.Loaded -> LoadedContent(
                state = state,
                innerPadding = innerPadding,
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

/**
 * The app bar while renaming: X (discard) on the left, the name as an in-place field styled like
 * the normal title, and a tick (save, disabled while blank) on the right. The field takes focus -
 * and with it the keyboard - as soon as the bar appears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RenameTopBar(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TopAppBar(
        title = {
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onConfirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Column {
                        innerTextField()
                        HorizontalDivider(
                            thickness = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                },
            )
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.history_rename_cancel_content_description)
                )
            }
        },
        actions = {
            IconButton(onClick = onConfirm, enabled = draft.text.isNotBlank()) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.history_rename_confirm_content_description)
                )
            }
        }
    )
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

/**
 * Same non-scrolling layout as the live result steps: the photo takes whatever height the chips
 * and the verdict/harmony body leave over.
 */
@Composable
private fun LoadedContent(
    state: HistoryDetailUiState.Loaded,
    innerPadding: PaddingValues,
    onPickCandidate: (Int) -> Unit,
) {
    val entry = state.entry

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PhotoResultSection(
            photo = state.photo,
            sampleX = entry.sampleX,
            sampleY = entry.sampleY,
            maxHeightFraction = 1f,
            modifier = Modifier.weight(1f),
        )
        ColorChipRow(
            chipColorsArgb = state.chipColorsArgb,
            currentArgb = entry.calibratedArgb,
            onPick = onPickCandidate,
            modifier = Modifier.padding(top = 16.dp),
        )
        when (entry.type) {
            HistoryEntryType.CLOTHING -> PaletteResultBody(score = entry.score, modifier = Modifier.padding(top = 16.dp))
            HistoryEntryType.OBJECT -> HarmonyResultBody(
                inputColorArgb = entry.calibratedArgb,
                wheel = requireNotNull(entry.wheel),
                balance = requireNotNull(entry.balance),
            )
        }
    }
}
