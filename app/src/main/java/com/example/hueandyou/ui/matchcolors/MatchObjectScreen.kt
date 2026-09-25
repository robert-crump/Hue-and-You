package com.example.hueandyou.ui.matchcolors

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.ui.common.CameraCaptureStep
import com.example.hueandyou.ui.common.ColorChipRow
import com.example.hueandyou.ui.common.HarmonyResultBody
import com.example.hueandyou.ui.common.InfoDialog
import com.example.hueandyou.ui.common.PhotoResultSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchObjectScreen(
    onNavigateBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: MatchObjectViewModel = viewModel(factory = MatchObjectViewModel.factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDisclaimer by remember { mutableStateOf(false) }
    val showingResult = uiState is MatchObjectUiState.ShowingResult

    // On the finished result the checkmark replaces the back arrow; system back behaves the same.
    BackHandler(enabled = showingResult, onBack = onFinished)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.match_object_title)) },
                navigationIcon = {
                    if (!showingResult) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showDisclaimer = true }) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = stringResource(R.string.harmony_disclaimer_content_description)
                        )
                    }
                    if (showingResult) {
                        IconButton(onClick = onFinished) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.result_done_content_description)
                            )
                        }
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
                is MatchObjectUiState.PickingPhoto -> CameraCaptureStep(
                    onPhotoUri = { uri -> viewModel.onPhotoPicked(context.contentResolver, uri) },
                )
                is MatchObjectUiState.LoadingPhoto -> LoadingStep()
                is MatchObjectUiState.ExtractingColors -> LoadingStep()
                is MatchObjectUiState.ShowingResult -> ResultStep(
                    photo = state.photo,
                    inputColorArgb = state.inputColorArgb,
                    chipColorsArgb = state.chipColorsArgb,
                    wheel = state.wheel,
                    balance = state.balance,
                    onPickCandidate = viewModel::pickCandidate,
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
private fun LoadingStep() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Aligns content with the app bar's back arrow on the left and info icon on the right. */
private val RESULT_HORIZONTAL_PADDING = 16.dp

@Composable
private fun ResultStep(
    photo: Bitmap,
    inputColorArgb: Int,
    chipColorsArgb: List<Int>,
    wheel: HarmonyWheel,
    balance: HarmonyBalance,
    onPickCandidate: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = RESULT_HORIZONTAL_PADDING, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PhotoResultSection(
            photo = photo,
            maxHeightFraction = 1f,
            modifier = Modifier.weight(1f),
        )
        ColorChipRow(
            chipColorsArgb = chipColorsArgb,
            currentArgb = inputColorArgb,
            onPick = onPickCandidate,
            modifier = Modifier.padding(top = 16.dp),
        )
        HarmonyResultBody(inputColorArgb = inputColorArgb, wheel = wheel, balance = balance)
    }
}
