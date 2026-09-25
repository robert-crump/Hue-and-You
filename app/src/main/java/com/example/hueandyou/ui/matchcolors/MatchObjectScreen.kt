package com.example.hueandyou.ui.matchcolors

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.ui.common.ColorChipRow
import com.example.hueandyou.ui.common.HarmonyResultBody
import com.example.hueandyou.ui.common.InfoDialog
import com.example.hueandyou.ui.common.PhotoResultSection
import java.io.File

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
    var showDisclaimer by remember { mutableStateOf(false) }

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
                },
                actions = {
                    IconButton(onClick = { showDisclaimer = true }) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = stringResource(R.string.harmony_disclaimer_content_description)
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
                is MatchObjectUiState.ExtractingColors -> LoadingStep()
                is MatchObjectUiState.ShowingResult -> ResultStep(
                    photo = state.photo,
                    inputColorArgb = state.inputColorArgb,
                    alternativesArgb = state.alternativesArgb,
                    sampleX = state.sampleX,
                    sampleY = state.sampleY,
                    wheel = state.wheel,
                    balance = state.balance,
                    onPickCandidate = viewModel::pickCandidate,
                    onPickAtPoint = viewModel::pickAtPoint,
                )
            }
        }
    }

    if (showDisclaimer) {
        InfoDialog(
            title = stringResource(R.string.harmony_disclaimer_title),
            text = stringResource(R.string.rate_clothing_best_guess_disclaimer),
            onDismiss = { showDisclaimer = false },
        )
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
            text = stringResource(R.string.pick_photo_reminder),
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

private const val PHOTO_MAX_HEIGHT_FRACTION = 0.35f

@Composable
private fun ResultStep(
    photo: Bitmap,
    inputColorArgb: Int,
    alternativesArgb: List<Int>,
    sampleX: Double?,
    sampleY: Double?,
    wheel: HarmonyWheel,
    balance: HarmonyBalance,
    onPickCandidate: (Int) -> Unit,
    onPickAtPoint: (x: Double, y: Double) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PhotoResultSection(
            photo = photo,
            sampleX = sampleX,
            sampleY = sampleY,
            maxHeightFraction = PHOTO_MAX_HEIGHT_FRACTION,
            onTap = onPickAtPoint,
        )
        ColorChipRow(
            currentArgb = inputColorArgb,
            alternativesArgb = alternativesArgb,
            onPick = onPickCandidate,
            modifier = Modifier.padding(top = 16.dp),
        )
        HarmonyResultBody(inputColorArgb = inputColorArgb, wheel = wheel, balance = balance)
    }
}
