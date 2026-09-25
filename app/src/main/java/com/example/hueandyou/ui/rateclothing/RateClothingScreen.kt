package com.example.hueandyou.ui.rateclothing

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.ui.common.CameraCaptureStep
import com.example.hueandyou.ui.common.ColorChipRow
import com.example.hueandyou.ui.common.InfoDialog
import com.example.hueandyou.ui.common.PaletteResultBody
import com.example.hueandyou.ui.common.PhotoResultSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateClothingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfileSettings: () -> Unit,
    viewModel: RateClothingViewModel = viewModel(factory = RateClothingViewModel.factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDisclaimer by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rate_clothing_title)) },
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
                is RateClothingUiState.LoadingProfiles -> LoadingStep()
                is RateClothingUiState.NoProfile -> NoProfileDialog(
                    onCancel = onNavigateBack,
                    onGoToSettings = onNavigateToProfileSettings,
                )
                is RateClothingUiState.PickingPhoto -> CameraCaptureStep(
                    onPhotoUri = { uri -> viewModel.onPhotoPicked(context.contentResolver, uri) },
                )
                is RateClothingUiState.LoadingPhoto -> LoadingStep()
                is RateClothingUiState.ExtractingColors -> LoadingStep()
                is RateClothingUiState.ShowingResult -> ResultStep(
                    photo = state.photo,
                    argb = state.argb,
                    chipColorsArgb = state.chipColorsArgb,
                    sampleX = state.sampleX,
                    sampleY = state.sampleY,
                    score = state.score,
                    profiles = state.profiles,
                    selectedProfile = state.selectedProfile,
                    onPickCandidate = viewModel::pickCandidate,
                    onPickAtPoint = viewModel::pickAtPoint,
                    onSwitchProfile = viewModel::switchProfile,
                )
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
}

@Composable
private fun NoProfileDialog(onCancel: () -> Unit, onGoToSettings: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.rate_clothing_no_profile_title)) },
        text = { Text(stringResource(R.string.rate_clothing_no_profile_body)) },
        confirmButton = {
            TextButton(onClick = onGoToSettings) {
                Text(stringResource(R.string.rate_clothing_go_to_settings))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
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
    argb: Int,
    chipColorsArgb: List<Int>,
    sampleX: Double?,
    sampleY: Double?,
    score: PaletteScore,
    profiles: List<Profile>,
    selectedProfile: Profile?,
    onPickCandidate: (Int) -> Unit,
    onPickAtPoint: (x: Double, y: Double) -> Unit,
    onSwitchProfile: (Profile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = RESULT_HORIZONTAL_PADDING, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PhotoResultSection(
            photo = photo,
            sampleX = sampleX,
            sampleY = sampleY,
            maxHeightFraction = 1f,
            onTap = onPickAtPoint,
            modifier = Modifier.weight(1f),
        )
        if (profiles.size >= 2 && selectedProfile != null) {
            ProfileDropdown(
                profiles = profiles,
                selectedProfile = selectedProfile,
                onSelect = onSwitchProfile,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        ColorChipRow(
            chipColorsArgb = chipColorsArgb,
            currentArgb = argb,
            onPick = onPickCandidate,
            modifier = Modifier.padding(top = 16.dp),
        )
        PaletteResultBody(score = score, modifier = Modifier.padding(top = 16.dp))
    }
}

/** "For: <name> ▾" - tapping it opens a dropdown menu of every profile to score against. */
@Composable
private fun ProfileDropdown(
    profiles: List<Profile>,
    selectedProfile: Profile,
    onSelect: (Profile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier.clickable { expanded = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.rate_clothing_for_profile, selectedProfile.name),
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = {
                        expanded = false
                        onSelect(profile)
                    },
                )
            }
        }
    }
}
