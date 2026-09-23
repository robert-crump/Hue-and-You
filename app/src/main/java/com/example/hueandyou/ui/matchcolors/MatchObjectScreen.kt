package com.example.hueandyou.ui.matchcolors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ExtractedColor
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.WhiteBalanceFailureReason
import com.example.hueandyou.colorspace.WhiteBalanceResult
import com.example.hueandyou.colorspace.formatHexColor
import com.example.hueandyou.ui.common.HarmonyOptionsControls
import com.example.hueandyou.ui.common.HarmonyResultBody
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchObjectScreen(
    onNavigateBack: () -> Unit,
    viewModel: MatchObjectViewModel = viewModel(factory = MatchObjectViewModel.factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pickPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onPhotoPicked(context.contentResolver, uri)
    }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) viewModel.onPhotoPicked(context.contentResolver, uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.match_object_title)) },
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
                is MatchObjectUiState.PickingPhoto -> PickPhotoStep(
                    onTakePhoto = {
                        val uri = createCameraPhotoUri(context)
                        pendingCameraUri = uri
                        takePhotoLauncher.launch(uri)
                    },
                    onPickPhoto = {
                        pickPhotoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
                is MatchObjectUiState.LoadingPhoto -> LoadingStep()
                is MatchObjectUiState.Calibrating -> CalibratingStep(
                    bitmap = state.bitmap,
                    calibration = state.calibration,
                    onTap = viewModel::onTap,
                    onConfirm = viewModel::confirmCalibration,
                    onChooseNewPhoto = viewModel::chooseNewPhoto,
                )
                is MatchObjectUiState.SelectingColors -> SelectingColorsStep(
                    colors = state.colors,
                    selectedArgb = state.selectedArgb,
                    onToggle = viewModel::toggleColorSelection,
                    onConfirm = viewModel::confirmColorSelection,
                )
                is MatchObjectUiState.ShowingResult -> ResultStep(
                    inputColorsArgb = state.inputColorsArgb,
                    wheel = state.wheel,
                    balance = state.balance,
                    entryId = state.historyEntryId,
                    name = state.historyEntryName,
                    onRenameChange = viewModel::renameResult,
                    onWheelChange = viewModel::setWheel,
                    onBalanceChange = viewModel::setBalance,
                )
            }
        }
    }
}

@Composable
private fun PickPhotoStep(onTakePhoto: () -> Unit, onPickPhoto: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.rate_clothing_white_sheet_reminder),
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onTakePhoto, modifier = Modifier.padding(top = 24.dp)) {
            Text(stringResource(R.string.rate_clothing_take_photo))
        }
        Button(onClick = onPickPhoto, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.rate_clothing_pick_photo))
        }
    }
}

private fun createCameraPhotoUri(context: Context): Uri {
    val capturesDir = File(context.cacheDir, "camera_captures").apply { mkdirs() }
    val photoFile = File.createTempFile("capture_", ".jpg", capturesDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
}

@Composable
private fun LoadingStep() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CalibratingStep(
    bitmap: Bitmap,
    calibration: WhiteBalanceResult?,
    onTap: (x: Int, y: Int) -> Unit,
    onConfirm: () -> Unit,
    onChooseNewPhoto: () -> Unit,
) {
    var displaySize by remember { mutableStateOf(IntSize.Zero) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.rate_clothing_tap_white_sheet),
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
                contentDescription = stringResource(R.string.rate_clothing_photo_content_description),
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { displaySize = it }
                    .pointerInput(bitmap) {
                        detectTapGestures { offset ->
                            val width = displaySize.width
                            val height = displaySize.height
                            if (width == 0 || height == 0) return@detectTapGestures
                            val x = (offset.x / width * bitmap.width).roundToInt()
                                .coerceIn(0, bitmap.width - 1)
                            val y = (offset.y / height * bitmap.height).roundToInt()
                                .coerceIn(0, bitmap.height - 1)
                            onTap(x, y)
                        }
                    }
            )
            if (calibration is WhiteBalanceResult.Success && displaySize.width > 0) {
                val region = calibration.sampledRegion
                val scaleX = displaySize.width / bitmap.width.toFloat()
                val scaleY = displaySize.height / bitmap.height.toFloat()
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White,
                        radius = region.radius * scaleX,
                        center = Offset(region.centerX * scaleX, region.centerY * scaleY),
                        style = Stroke(width = 4f)
                    )
                }
            }
        }
        if (calibration is WhiteBalanceResult.Failure) {
            Text(
                text = stringResource(calibrationFailureMessage(calibration.reason)),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Row(modifier = Modifier.padding(top = 16.dp)) {
            Button(
                onClick = onConfirm,
                enabled = calibration is WhiteBalanceResult.Success,
            ) {
                Text(stringResource(R.string.rate_clothing_confirm_calibration))
            }
            Button(
                onClick = onChooseNewPhoto,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(stringResource(R.string.rate_clothing_choose_different_photo))
            }
        }
    }
}

private fun calibrationFailureMessage(reason: WhiteBalanceFailureReason): Int = when (reason) {
    WhiteBalanceFailureReason.NO_SAMPLED_PIXELS -> R.string.rate_clothing_calibration_failed
    WhiteBalanceFailureReason.CLIPPED -> R.string.rate_clothing_calibration_failed_clipped
    WhiteBalanceFailureReason.TOO_DARK -> R.string.rate_clothing_calibration_failed_too_dark
    WhiteBalanceFailureReason.NOT_WHITE -> R.string.rate_clothing_calibration_failed_not_white
}

@Composable
private fun SelectingColorsStep(
    colors: List<ExtractedColor>,
    selectedArgb: Set<Int>,
    onToggle: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.match_object_select_colors_title),
            style = MaterialTheme.typography.bodyLarge
        )
        LazyColumn(modifier = Modifier.weight(1f).padding(top = 16.dp)) {
            items(colors) { color ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(color.argb) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = color.argb in selectedArgb,
                        onCheckedChange = { onToggle(color.argb) }
                    )
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(color.argb))
                    )
                    Text(
                        text = "${formatHexColor(color.argb)} — ${(color.share * 100).roundToInt()}%",
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
        Button(
            onClick = onConfirm,
            enabled = selectedArgb.isNotEmpty(),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.match_object_continue))
        }
    }
}

@Composable
private fun ResultStep(
    inputColorsArgb: List<Int>,
    wheel: HarmonyWheel,
    balance: HarmonyBalance,
    entryId: Long,
    name: String,
    onRenameChange: (String) -> Unit,
    onWheelChange: (HarmonyWheel) -> Unit,
    onBalanceChange: (HarmonyBalance) -> Unit,
) {
    var currentName by rememberSaveable(entryId) { mutableStateOf(name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = currentName,
            onValueChange = {
                currentName = it
                onRenameChange(it)
            },
            label = { Text(stringResource(R.string.history_entry_name_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        HarmonyOptionsControls(
            wheel = wheel,
            balance = balance,
            onWheelChange = onWheelChange,
            onBalanceChange = onBalanceChange,
            modifier = Modifier.padding(top = 16.dp),
        )
        HarmonyResultBody(inputColorsArgb = inputColorsArgb, wheel = wheel, balance = balance)
    }
}
