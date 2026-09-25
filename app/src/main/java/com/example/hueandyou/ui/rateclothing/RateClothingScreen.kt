package com.example.hueandyou.ui.rateclothing

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.ui.common.PaletteResultBody
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateClothingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToProfileSettings: () -> Unit,
    viewModel: RateClothingViewModel = viewModel(factory = RateClothingViewModel.factory(LocalContext.current)),
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
                title = { Text(stringResource(R.string.rate_clothing_title)) },
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
                is RateClothingUiState.LoadingProfiles -> LoadingStep()
                is RateClothingUiState.NoProfile -> NoProfileDialog(
                    onCancel = onNavigateBack,
                    onGoToSettings = onNavigateToProfileSettings,
                )
                is RateClothingUiState.SelectingProfile -> SelectingProfileStep(
                    profiles = state.profiles,
                    onSelect = viewModel::selectProfile,
                )
                is RateClothingUiState.PickingPhoto -> PickPhotoStep(
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
                is RateClothingUiState.LoadingPhoto -> LoadingStep()
                is RateClothingUiState.ExtractingColors -> LoadingStep()
                is RateClothingUiState.ShowingResult -> ResultStep(
                    argb = state.argb,
                    score = state.score,
                    entryId = state.historyEntryId,
                    name = state.historyEntryName,
                    onRenameChange = viewModel::renameResult,
                )
            }
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
private fun SelectingProfileStep(profiles: List<Profile>, onSelect: (Profile) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.rate_clothing_select_profile_title),
            style = MaterialTheme.typography.bodyLarge
        )
        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(profiles, key = { it.id }) { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(profile) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
                }
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

@Composable
private fun ResultStep(
    argb: Int,
    score: PaletteScore,
    entryId: Long,
    name: String,
    onRenameChange: (String) -> Unit,
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
        PaletteResultBody(argb = argb, score = score)
    }
}
