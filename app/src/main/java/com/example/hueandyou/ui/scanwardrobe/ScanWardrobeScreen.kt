package com.example.hueandyou.ui.scanwardrobe

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.data.history.ClothingCategory
import com.example.hueandyou.ui.common.CameraCaptureStep
import com.example.hueandyou.ui.common.ColorChipRow
import com.example.hueandyou.ui.common.ColorCircle
import com.example.hueandyou.ui.common.PhotoResultSection
import com.example.hueandyou.ui.common.clothingCategoryLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanWardrobeScreen(
    category: ClothingCategory,
    onNavigateBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ScanWardrobeViewModel = viewModel(
        factory = ScanWardrobeViewModel.factory(LocalContext.current, category)
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val step = uiState.step
    var showDiscardDialog by remember { mutableStateOf(false) }

    LaunchedEffect(step) {
        if (step == ScanWardrobeStep.Saved) onSaved()
    }

    // Leaving with scanned items asks first; from the viewfinder, back goes to the review list.
    val onBack: () -> Unit = {
        when {
            step is ScanWardrobeStep.Photo ->
                if (step.fromReview) viewModel.showReview() else viewModel.retake()
            uiState.items.isEmpty() -> onNavigateBack()
            step == ScanWardrobeStep.Review -> showDiscardDialog = true
            step == ScanWardrobeStep.Viewfinder -> viewModel.showReview()
        }
    }
    BackHandler(enabled = step != ScanWardrobeStep.Saving && step != ScanWardrobeStep.Saved, onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            R.string.scan_wardrobe_title,
                            stringResource(scanCategoryTitle(uiState.category)),
                            uiState.items.size,
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (step == ScanWardrobeStep.Review || step == ScanWardrobeStep.Saving) {
                        TextButton(
                            onClick = viewModel::save,
                            enabled = step == ScanWardrobeStep.Review && uiState.items.isNotEmpty(),
                        ) {
                            Text(stringResource(R.string.scan_wardrobe_save))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (step == ScanWardrobeStep.Review) {
                FloatingActionButton(onClick = viewModel::continueScanning) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.scan_wardrobe_add_content_description))
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (step) {
                ScanWardrobeStep.Viewfinder -> CameraCaptureStep(
                    onPhotoUri = { uri -> viewModel.onPhotoPicked(context.contentResolver, uri) },
                    hint = stringResource(R.string.scan_wardrobe_hint),
                )
                ScanWardrobeStep.Processing, ScanWardrobeStep.Saving, ScanWardrobeStep.Saved -> LoadingStep()
                is ScanWardrobeStep.Photo -> uiState.currentItem?.let { item ->
                    PhotoStep(
                        item = item,
                        onPickColor = viewModel::pickColor,
                        onNextItem = viewModel::nextItem,
                        onRetake = viewModel::retake,
                        onDone = viewModel::showReview,
                    )
                }
                ScanWardrobeStep.Review -> ReviewStep(
                    items = uiState.items,
                    onRename = viewModel::rename,
                    onRemove = viewModel::remove,
                    onOpen = viewModel::reopen,
                )
            }
        }
    }

    if (showDiscardDialog) {
        val count = uiState.items.size
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(pluralStringResource(R.plurals.scan_wardrobe_discard_title, count, count)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onNavigateBack()
                }) {
                    Text(stringResource(R.string.scan_wardrobe_discard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.scan_wardrobe_keep_scanning))
                }
            },
        )
    }
}

@Composable
private fun LoadingStep() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Large photo, its top-3 chips, and the three ways on. No marker, no verdict. */
@Composable
private fun PhotoStep(
    item: ScannedItem,
    onPickColor: (Int) -> Unit,
    onNextItem: () -> Unit,
    onRetake: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhotoResultSection(
            photo = item.photo,
            maxHeightFraction = 1f,
            showMarker = false,
            modifier = Modifier.weight(1f),
        )
        ColorChipRow(
            chipColorsArgb = item.chipColorsArgb,
            currentArgb = item.argb,
            onPick = onPickColor,
            modifier = Modifier.padding(top = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            TextButton(onClick = onRetake) { Text(stringResource(R.string.scan_wardrobe_retake)) }
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.scan_wardrobe_done)) }
            Button(onClick = onNextItem) { Text(stringResource(R.string.scan_wardrobe_next_item)) }
        }
    }
}

@Composable
private fun ReviewStep(
    items: List<ScannedItem>,
    onRename: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    onOpen: (Long) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.scan_wardrobe_review_empty),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
        )
        return
    }
    // Bottom padding keeps the last row clear of the FAB.
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp), modifier = Modifier.fillMaxSize()) {
        items(items, key = { it.key }) { item ->
            ReviewRow(
                item = item,
                onRename = { onRename(item.key, it) },
                onRemove = { onRemove(item.key) },
                onOpen = { onOpen(item.key) },
            )
        }
    }
}

/** Swiping either way removes the item, like the trailing ✕. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewRow(item: ScannedItem, onRename: (String) -> Unit, onRemove: () -> Unit, onOpen: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
            )
        },
        onDismiss = { onRemove() },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        ) {
            val thumbnail = remember(item.photo) { item.photo.asImageBitmap() }
            Image(
                bitmap = thumbnail,
                contentDescription = stringResource(R.string.scan_wardrobe_open_item_content_description, item.name),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpen),
            )
            ColorCircle(argb = item.argb, size = 24.dp)
            NameField(name = item.name, onNameChange = onRename, modifier = Modifier.weight(1f))
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.scan_wardrobe_remove_content_description, item.name),
                )
            }
        }
    }
}

/**
 * Selects the whole name on focus, so typing replaces the default. [name] is only read once: this
 * field is the only place it changes.
 */
@Composable
private fun NameField(name: String, onNameChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var value by remember { mutableStateOf(TextFieldValue(name)) }
    // The tap that focuses the field also places the cursor; that first selection-only change is ignored.
    var selectAllPending by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (selectAllPending && new.text == value.text) {
                selectAllPending = false
                return@OutlinedTextField
            }
            selectAllPending = false
            value = new
            if (new.text != name) onNameChange(new.text)
        },
        label = { Text(stringResource(R.string.scan_wardrobe_name_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Done,
        ),
        modifier = modifier.onFocusChanged { focus ->
            if (focus.isFocused) {
                value = value.copy(selection = TextRange(0, value.text.length))
                selectAllPending = true
            }
        },
    )
}

/** "What are you scanning?": a category is required before the flow starts. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScanCategorySheet(onStart: (ClothingCategory) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var category by rememberSaveable { mutableStateOf<ClothingCategory?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.scan_wardrobe_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ClothingCategory.entries.forEach { option ->
                    FilterChip(
                        selected = option == category,
                        onClick = { category = option },
                        label = { Text(stringResource(clothingCategoryLabel(option))) },
                    )
                }
            }
            Button(
                onClick = {
                    val chosen = category ?: return@Button
                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                        onDismiss()
                        onStart(chosen)
                    }
                },
                enabled = category != null,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.scan_wardrobe_start))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@StringRes
private fun scanCategoryTitle(category: ClothingCategory): Int = when (category) {
    ClothingCategory.TOP -> R.string.scan_wardrobe_category_tops
    ClothingCategory.BOTTOM -> R.string.scan_wardrobe_category_bottoms
    ClothingCategory.ONE_PIECE -> R.string.scan_wardrobe_category_one_pieces
    ClothingCategory.OUTERWEAR -> R.string.scan_wardrobe_category_outerwear
    ClothingCategory.SHOES -> R.string.scan_wardrobe_category_shoes
    ClothingCategory.BELT -> R.string.scan_wardrobe_category_belts
}
