package com.example.hueandyou.ui.paletteimport

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.RectRegion
import com.example.hueandyou.colorspace.formatHexColor
import com.example.hueandyou.data.profile.ColorKind
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaletteImportScreen(
    profileId: Long,
    onNavigateBack: () -> Unit,
    viewModel: PaletteImportViewModel = viewModel(
        factory = PaletteImportViewModel.factory(LocalContext.current, profileId)
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onPhotoPicked(context.contentResolver, uri)
    }

    if (uiState is PaletteImportUiState.Done) {
        LaunchedEffect(Unit) { onNavigateBack() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.palette_import_title)) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PaletteImportUiState.PickingPhoto -> PickPhotoStep(
                    onPickPhoto = {
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                is PaletteImportUiState.LoadingPhoto -> LoadingStep()
                is PaletteImportUiState.MarkingArea -> MarkingAreaStep(
                    bitmap = state.bitmap,
                    kind = state.kind,
                    rect = state.rect,
                    onRectMarked = viewModel::updateMarkedRect,
                    onConfirm = viewModel::confirmArea,
                    onSkip = viewModel::skipArea,
                )
                is PaletteImportUiState.Reviewing -> ReviewingStep(
                    bestSwatches = state.bestSwatches,
                    avoidSwatches = state.avoidSwatches,
                    onToggle = viewModel::toggleSwatch,
                    onRemove = viewModel::removeSwatch,
                    onAddManual = viewModel::addManualSwatch,
                    onConfirm = viewModel::confirmImport,
                )
                is PaletteImportUiState.Done -> LoadingStep()
            }
        }
    }
}

@Composable
private fun PickPhotoStep(onPickPhoto: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.palette_import_pick_photo_reminder),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onPickPhoto, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.palette_import_pick_photo))
        }
    }
}

@Composable
private fun LoadingStep() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MarkingAreaStep(
    bitmap: Bitmap,
    kind: ColorKind,
    rect: RectRegion,
    onRectMarked: (RectRegion) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
) {
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val currentRect by rememberUpdatedState(rect)
    val currentOnRectMarked by rememberUpdatedState(onRectMarked)
    val touchSlopPx = with(LocalDensity.current) { RECT_TOUCH_SLOP.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(
                if (kind == ColorKind.BEST) {
                    R.string.palette_import_mark_best_instruction
                } else {
                    R.string.palette_import_mark_avoid_instruction
                }
            ),
            style = MaterialTheme.typography.bodyLarge
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.palette_import_photo_content_description),
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { displaySize = it }
                    .pointerInput(bitmap) {
                        var mode: RectDragMode? = null
                        var startRect = currentRect
                        var total = Offset.Zero
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (displaySize.width == 0 || displaySize.height == 0) return@detectDragGestures
                                val scaleX = bitmap.width / displaySize.width.toFloat()
                                val scaleY = bitmap.height / displaySize.height.toFloat()
                                startRect = currentRect
                                total = Offset.Zero
                                mode = hitTestRect(
                                    rect = startRect,
                                    x = offset.x * scaleX,
                                    y = offset.y * scaleY,
                                    slopX = touchSlopPx * scaleX,
                                    slopY = touchSlopPx * scaleY,
                                )
                            },
                            onDragEnd = { mode = null },
                            onDragCancel = { mode = null },
                            onDrag = { change, dragAmount ->
                                val activeMode = mode ?: return@detectDragGestures
                                if (displaySize.width == 0 || displaySize.height == 0) return@detectDragGestures
                                change.consume()
                                total += dragAmount
                                val dx = total.x * bitmap.width / displaySize.width
                                val dy = total.y * bitmap.height / displaySize.height
                                currentOnRectMarked(
                                    dragRect(startRect, activeMode, dx, dy, bitmap.width, bitmap.height)
                                )
                            }
                        )
                    }
            )
            if (displaySize.width > 0) {
                val scaleX = displaySize.width / bitmap.width.toFloat()
                val scaleY = displaySize.height / bitmap.height.toFloat()
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val left = rect.left * scaleX
                    val top = rect.top * scaleY
                    val right = rect.right * scaleX
                    val bottom = rect.bottom * scaleY
                    val width = right - left
                    val height = bottom - top
                    for (i in 1..2) {
                        val x = left + width * i / 3f
                        val y = top + height * i / 3f
                        drawLine(Color.Black.copy(alpha = 0.5f), Offset(x, top), Offset(x, bottom), 3f)
                        drawLine(Color.White.copy(alpha = 0.8f), Offset(x, top), Offset(x, bottom), 1.5f)
                        drawLine(Color.Black.copy(alpha = 0.5f), Offset(left, y), Offset(right, y), 3f)
                        drawLine(Color.White.copy(alpha = 0.8f), Offset(left, y), Offset(right, y), 1.5f)
                    }
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 7f)
                    )
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(onClick = onConfirm) {
                Text(stringResource(R.string.palette_import_confirm_area))
            }
            Button(onClick = onSkip, modifier = Modifier.padding(start = 8.dp)) {
                Text(stringResource(R.string.palette_import_skip_area))
            }
        }
    }
}

private val RECT_TOUCH_SLOP = 28.dp
private const val MIN_RECT_SIZE_PX = 16

/** Which parts of the rectangle a drag moves: the whole rectangle, or one/two edges. */
private data class RectDragMode(
    val left: Boolean = false,
    val top: Boolean = false,
    val right: Boolean = false,
    val bottom: Boolean = false,
    val move: Boolean = false,
)

/** Coordinates and slops are in bitmap pixels. Edges win over the interior; outside does nothing. */
private fun hitTestRect(rect: RectRegion, x: Float, y: Float, slopX: Float, slopY: Float): RectDragMode? {
    val withinY = y >= rect.top - slopY && y <= rect.bottom + slopY
    val withinX = x >= rect.left - slopX && x <= rect.right + slopX
    val dl = abs(x - rect.left)
    val dr = abs(x - rect.right)
    val dt = abs(y - rect.top)
    val db = abs(y - rect.bottom)
    val nearLeft = withinY && dl <= slopX
    val nearRight = withinY && dr <= slopX
    val nearTop = withinX && dt <= slopY
    val nearBottom = withinX && db <= slopY
    val mode = RectDragMode(
        left = nearLeft && (!nearRight || dl <= dr),
        right = nearRight && (!nearLeft || dr < dl),
        top = nearTop && (!nearBottom || dt <= db),
        bottom = nearBottom && (!nearTop || db < dt),
    )
    if (mode.left || mode.right || mode.top || mode.bottom) return mode
    val inside = x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
    return if (inside) RectDragMode(move = true) else null
}

private fun dragRect(start: RectRegion, mode: RectDragMode, dx: Float, dy: Float, maxX: Int, maxY: Int): RectRegion {
    if (mode.move) {
        val mx = dx.roundToInt().coerceIn(-start.left, maxX - start.right)
        val my = dy.roundToInt().coerceIn(-start.top, maxY - start.bottom)
        return RectRegion(start.left + mx, start.top + my, start.right + mx, start.bottom + my)
    }
    val minW = MIN_RECT_SIZE_PX.coerceAtMost(maxX)
    val minH = MIN_RECT_SIZE_PX.coerceAtMost(maxY)
    var left = start.left
    var right = start.right
    var top = start.top
    var bottom = start.bottom
    if (mode.left) left = (start.left + dx).roundToInt().coerceIn(0, right - minW)
    if (mode.right) right = (start.right + dx).roundToInt().coerceIn(left + minW, maxX)
    if (mode.top) top = (start.top + dy).roundToInt().coerceIn(0, bottom - minH)
    if (mode.bottom) bottom = (start.bottom + dy).roundToInt().coerceIn(top + minH, maxY)
    return RectRegion(left, top, right, bottom)
}

@Composable
private fun ReviewingStep(
    bestSwatches: List<ImportSwatch>,
    avoidSwatches: List<ImportSwatch>,
    onToggle: (ColorKind, Int) -> Unit,
    onRemove: (ColorKind, Int) -> Unit,
    onAddManual: (ColorKind, String, String) -> String?,
    onConfirm: () -> Unit,
) {
    var addSwatchKind by remember { mutableStateOf<ColorKind?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SwatchSection(
            title = stringResource(R.string.palette_import_reviewing_best_title),
            swatches = bestSwatches,
            onToggle = { onToggle(ColorKind.BEST, it) },
            onRemove = { onRemove(ColorKind.BEST, it) },
            onAddClick = { addSwatchKind = ColorKind.BEST },
        )
        SwatchSection(
            title = stringResource(R.string.palette_import_reviewing_avoid_title),
            swatches = avoidSwatches,
            onToggle = { onToggle(ColorKind.AVOID, it) },
            onRemove = { onRemove(ColorKind.AVOID, it) },
            onAddClick = { addSwatchKind = ColorKind.AVOID },
        )
        Button(onClick = onConfirm, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.palette_import_confirm_import))
        }
    }

    addSwatchKind?.let { kind ->
        AddSwatchDialog(
            kind = kind,
            onDismiss = { addSwatchKind = null },
            onAdd = { hex, invalidHexMessage -> onAddManual(kind, hex, invalidHexMessage) }
        )
    }
}

@Composable
private fun SwatchSection(
    title: String,
    swatches: List<ImportSwatch>,
    onToggle: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
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
                    contentDescription = stringResource(R.string.palette_import_add_color_content_description)
                )
            }
        }
        if (swatches.isEmpty()) {
            Text(
                text = stringResource(R.string.palette_import_no_swatches),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                swatches.forEach { swatch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(swatch.id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = swatch.selected,
                            onCheckedChange = { onToggle(swatch.id) }
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(swatch.argb))
                        )
                        Text(
                            text = formatHexColor(swatch.argb),
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        )
                        IconButton(onClick = { onRemove(swatch.id) }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(
                                    R.string.palette_import_remove_color_content_description
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSwatchDialog(
    kind: ColorKind,
    onDismiss: () -> Unit,
    onAdd: (hex: String, invalidHexMessage: String) -> String?
) {
    var hex by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val invalidHexMessage = stringResource(R.string.add_color_dialog_invalid_hex)

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
                OutlinedTextField(
                    value = hex,
                    onValueChange = {
                        hex = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.add_color_dialog_hex_label)) },
                    isError = error != null,
                )
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val result = onAdd(hex, invalidHexMessage)
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
