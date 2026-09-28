package com.example.hueandyou.ui.seasonanalysis

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.Season
import com.example.hueandyou.colorspace.SeasonMatch
import com.example.hueandyou.ui.common.CameraCaptureStep
import com.example.hueandyou.ui.common.ColorCircle

private const val MAX_ZOOM = 8f
private const val GRID_COLUMNS = 5
private val GRID_GAP = 8.dp
private val MAX_CIRCLE_SIZE = 48.dp
private val MARKER_RADIUS = 10.dp
/** How close to the marker a touch must land to drag it rather than pan the photo. */
private val MARKER_GRAB_RADIUS = 32.dp

private fun featureLabelRes(feature: SeasonFeature): Int = when (feature) {
    SeasonFeature.SKIN -> R.string.season_feature_skin
    SeasonFeature.HAIR -> R.string.season_feature_hair
    SeasonFeature.EYES -> R.string.season_feature_eyes
}

private fun featurePromptRes(feature: SeasonFeature): Int = when (feature) {
    SeasonFeature.SKIN -> R.string.season_analysis_prompt_skin
    SeasonFeature.HAIR -> R.string.season_analysis_prompt_hair
    SeasonFeature.EYES -> R.string.season_analysis_prompt_eyes
}

private fun featureCameraHintRes(feature: SeasonFeature): Int = when (feature) {
    SeasonFeature.SKIN -> R.string.season_analysis_camera_hint_skin
    SeasonFeature.HAIR -> R.string.season_analysis_camera_hint_hair
    SeasonFeature.EYES -> R.string.season_analysis_camera_hint_eyes
}

/** The step the flow is on, or null outside the three photo steps. */
private val SeasonAnalysisUiState.stepFeature: SeasonFeature?
    get() = when (this) {
        is SeasonAnalysisUiState.Capturing -> feature
        is SeasonAnalysisUiState.LoadingPhoto -> feature
        is SeasonAnalysisUiState.Placing -> feature
        else -> null
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonAnalysisScreen(
    onNavigateBack: () -> Unit,
    onProfileCreated: (profileId: Long) -> Unit,
    viewModel: SeasonAnalysisViewModel = viewModel(factory = SeasonAnalysisViewModel.factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val photo by viewModel.photo.collectAsState()
    val context = LocalContext.current

    (uiState as? SeasonAnalysisUiState.Done)?.let { done ->
        LaunchedEffect(done) { onProfileCreated(done.profileId) }
    }

    BackHandler(
        enabled = when (val state = uiState) {
            is SeasonAnalysisUiState.Capturing -> state.feature != SeasonFeature.SKIN
            is SeasonAnalysisUiState.Placing, is SeasonAnalysisUiState.ShowingResult -> true
            else -> false
        }
    ) {
        viewModel.back()
    }

    val stepFeature = uiState.stepFeature
    val title = if (stepFeature != null) {
        stringResource(
            R.string.season_analysis_step_title,
            stringResource(featureLabelRes(stepFeature)),
            stepFeature.ordinal + 1,
            SeasonFeature.entries.size,
        )
    } else {
        stringResource(R.string.season_analysis_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { if (!viewModel.back()) onNavigateBack() }) {
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
                // Keyed so each step gets a fresh viewfinder (and fresh WB/exposure sliders).
                is SeasonAnalysisUiState.Capturing -> key(state.feature) {
                    CameraCaptureStep(
                        onPhotoUri = { uri -> viewModel.onPhotoPicked(context.contentResolver, uri) },
                        hint = stringResource(featureCameraHintRes(state.feature)),
                        showCenterBox = false,
                    )
                }
                is SeasonAnalysisUiState.Placing -> photo?.let { bitmap ->
                    PlacingStep(
                        bitmap = bitmap,
                        state = state,
                        onMoveMarker = viewModel::moveMarker,
                        onRetake = viewModel::retake,
                        onPhotoPicked = { uri -> viewModel.onPhotoPicked(context.contentResolver, uri) },
                        onNext = viewModel::next,
                    )
                }
                is SeasonAnalysisUiState.ShowingResult -> ResultStep(
                    state = state,
                    onSelectSeason = viewModel::selectSeason,
                    onCreateProfile = viewModel::createProfile,
                )
                is SeasonAnalysisUiState.LoadingPhoto,
                is SeasonAnalysisUiState.Saving,
                is SeasonAnalysisUiState.Done -> LoadingStep()
            }
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
private fun PlacingStep(
    bitmap: Bitmap,
    state: SeasonAnalysisUiState.Placing,
    onMoveMarker: (x: Int, y: Int) -> Unit,
    onRetake: () -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onNext: () -> Unit,
) {
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPhotoPicked(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(featurePromptRes(state.feature)),
            style = MaterialTheme.typography.bodyLarge
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onRetake) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.season_analysis_retake))
            }
            OutlinedButton(
                onClick = {
                    pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.season_analysis_choose_photo))
            }
        }
        ZoomablePickPhoto(
            bitmap = bitmap,
            marker = state.marker,
            markerLabel = stringResource(featureLabelRes(state.feature)),
            onMoveMarker = onMoveMarker,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepProgress(
                current = state.feature,
                colors = state.picks.mapValues { it.value.argb } + (state.feature to state.marker.argb),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onNext) {
                Text(
                    stringResource(
                        if (state.feature.next() == null) R.string.season_analysis_see_result
                        else R.string.season_analysis_next
                    )
                )
            }
        }
    }
}

/**
 * One labeled swatch per step: finished steps show their color, the [current] one is larger and
 * shows the marker's live color, later ones are an empty ring.
 */
@Composable
private fun StepProgress(current: SeasonFeature, colors: Map<SeasonFeature, Int>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        SeasonFeature.entries.forEach { feature ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FeatureSwatch(argb = colors[feature], size = if (feature == current) 40.dp else 24.dp)
                Text(
                    text = stringResource(featureLabelRes(feature)),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (feature == current) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** A color dot: the picked color, or an empty ring until there is one. */
@Composable
private fun FeatureSwatch(argb: Int?, size: Dp) {
    val modifier = Modifier
        .size(size)
        .clip(CircleShape)
    if (argb != null) {
        Box(modifier.background(Color(argb)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
    } else {
        Box(modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
    }
}

/**
 * The photo, pinch-zoomable and pannable, with a labeled [marker]. Dragging the marker or tapping
 * the photo moves it; positions are reported in bitmap pixels whatever the zoom, which only helps aim.
 */
@Composable
private fun ZoomablePickPhoto(
    bitmap: Bitmap,
    marker: FeaturePick,
    markerLabel: String,
    onMoveMarker: (x: Int, y: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val currentOnMoveMarker by rememberUpdatedState(onMoveMarker)
    val currentMarker by rememberUpdatedState(marker)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = Color.White,
        fontSize = 12.sp,
        shadow = Shadow(color = Color.Black, blurRadius = 4f)
    )

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height
        val displayHeight = minOf(maxHeight, maxWidth / aspectRatio)
        val displayWidth = displayHeight * aspectRatio

        Box(
            modifier = Modifier
                .size(displayWidth, displayHeight)
                .clipToBounds()
                .pointerInput(bitmap) {
                    val grabRadius = MARKER_GRAB_RADIUS.toPx()
                    val moveMarkerTo = { position: Offset ->
                        val (x, y) = screenToBitmap(position, scale, offset, size, bitmap)
                        currentOnMoveMarker(x, y)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val markerPosition = bitmapToScreen(currentMarker.x, currentMarker.y, scale, offset, size, bitmap)
                        if ((down.position - markerPosition).getDistance() <= grabRadius) {
                            // Grabbed the marker: it follows the finger until release.
                            drag(down.id) { change ->
                                moveMarkerTo(change.position)
                                change.consume()
                            }
                            return@awaitEachGesture
                        }
                        // Otherwise pinch-zoom/pan, or a tap if the fingers never moved past touch slop.
                        var transforming = false
                        var travel = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            val pan = event.calculatePan()
                            val zoom = event.calculateZoom()
                            if (!transforming) {
                                travel += pan
                                transforming = event.changes.size > 1 ||
                                    travel.getDistance() > viewConfiguration.touchSlop
                            }
                            // The release event has no pressed pointers, so no centroid (NaN) -
                            // applying it would blank the photo.
                            val centroid = event.calculateCentroid()
                            if (transforming && centroid != Offset.Unspecified && (zoom != 1f || pan != Offset.Zero)) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                // Keep the point under the fingers in place while zooming.
                                val newOffset = centroid - center - (centroid - center - offset) * (newScale / scale) + pan
                                scale = newScale
                                offset = clampOffset(newOffset, newScale, size)
                            }
                            if (transforming) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        if (!transforming) moveMarkerTo(down.position)
                    }
                }
        ) {
            Image(
                bitmap = imageBitmap,
                contentDescription = stringResource(R.string.season_analysis_photo_content_description),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            // Drawn outside the zoomed layer so markers and labels keep their size.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val radius = MARKER_RADIUS.toPx()
                val canvasSize = IntSize(size.width.toInt(), size.height.toInt())
                val position = bitmapToScreen(marker.x, marker.y, scale, offset, canvasSize, bitmap)
                drawCircle(Color.Black.copy(alpha = 0.6f), radius, position, style = Stroke(width = 6f))
                drawCircle(Color.White, radius, position, style = Stroke(width = 3f))
                val label = textMeasurer.measure(markerLabel, labelStyle)
                drawText(
                    label,
                    topLeft = Offset(position.x + radius + 4f, position.y - label.size.height / 2f)
                )
            }
        }
    }
}

/** Where bitmap pixel ([x], [y]) appears in a [size] box showing [bitmap] zoomed by [scale] and panned by [offset]. */
private fun bitmapToScreen(x: Int, y: Int, scale: Float, offset: Offset, size: IntSize, bitmap: Bitmap): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    val local = Offset(x * size.width.toFloat() / bitmap.width, y * size.height.toFloat() / bitmap.height)
    return center + offset + (local - center) * scale
}

/** The inverse of [bitmapToScreen]: the bitmap pixel under [position]. */
private fun screenToBitmap(position: Offset, scale: Float, offset: Offset, size: IntSize, bitmap: Bitmap): Pair<Int, Int> {
    val center = Offset(size.width / 2f, size.height / 2f)
    val local = (position - center - offset) / scale + center
    return (local.x * bitmap.width / size.width).toInt() to (local.y * bitmap.height / size.height).toInt()
}

/** Keeps the zoomed photo covering its box: it may pan by at most the overflow on each side. */
private fun clampOffset(offset: Offset, scale: Float, size: IntSize): Offset {
    val maxX = (scale - 1f) * size.width / 2f
    val maxY = (scale - 1f) * size.height / 2f
    return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
}

@Composable
private fun ResultStep(
    state: SeasonAnalysisUiState.ShowingResult,
    onSelectSeason: (Season) -> Unit,
    onCreateProfile: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            SeasonFeature.entries.forEach { feature ->
                state.picks[feature]?.let { pick ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        ColorCircle(
                            argb = pick.argb,
                            size = 32.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        )
                        Text(
                            text = stringResource(featureLabelRes(feature)),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            state.topMatches.forEach { match ->
                SeasonMatchRow(
                    match = match,
                    selected = match.season == state.selected,
                    onClick = { onSelectSeason(match.season) }
                )
            }
        }
        PaletteSection(title = stringResource(R.string.season_analysis_best_title), colors = state.bestColors)
        PaletteSection(title = stringResource(R.string.season_analysis_avoid_title), colors = state.avoidColors)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onCreateProfile, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.season_analysis_create_profile))
        }
    }
}

@Composable
private fun SeasonMatchRow(match: SeasonMatch, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(seasonNameRes(match.season)),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.season_analysis_match, match.matchPercent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { match.matchPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(4.dp),
                drawStopIndicator = {}
            )
        }
    }
}

/** A titled 5-column circle grid, laid out like the Profile editor's. */
@Composable
private fun PaletteSection(title: String, colors: List<Int>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
    )
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val circleSize: Dp = minOf(maxWidth / GRID_COLUMNS - GRID_GAP, MAX_CIRCLE_SIZE)
        Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
            colors.chunked(GRID_COLUMNS).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { argb ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            ColorCircle(argb = argb, size = circleSize)
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
