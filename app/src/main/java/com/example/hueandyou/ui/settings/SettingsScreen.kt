package com.example.hueandyou.ui.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.ui.common.HarmonyOptionsControls
import com.example.hueandyou.ui.profiles.ProfilesViewModel

@Composable
fun SettingsScreen(
    scrollToProfiles: Boolean = false,
    onOpenProfile: (Long) -> Unit,
    profilesViewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.factory(LocalContext.current)),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(LocalContext.current)),
) {
    val profiles by profilesViewModel.profiles.collectAsState()
    val defaults by settingsViewModel.defaults.collectAsState()
    val listState = rememberLazyListState()
    val defaultProfileName = stringResource(R.string.profile_default_name)
    val context = LocalContext.current
    var shareCardUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(scrollToProfiles) {
        if (scrollToProfiles) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
        item(key = "defaults_header") {
            Text(
                text = stringResource(R.string.settings_defaults_section_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item(key = "defaults_controls") {
            HarmonyOptionsControls(
                wheel = defaults.wheel,
                balance = defaults.balance,
                onWheelChange = settingsViewModel::setDefaultWheel,
                onBalanceChange = settingsViewModel::setDefaultBalance,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "profiles_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.profiles_section_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    profilesViewModel.createProfile(defaultProfileName) { id -> onOpenProfile(id) }
                }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.profile_add_content_description)
                    )
                }
            }
        }
        if (profiles.isEmpty()) {
            item(key = "profiles_empty") {
                Text(
                    text = stringResource(R.string.profiles_empty_state),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onClick = { onOpenProfile(profile.id) },
                    onShare = {
                        profilesViewModel.shareCard(profile) { uri -> shareCardUri = uri }
                    }
                )
            }
        }
    }

    shareCardUri?.let { uri ->
        SharePreviewDialog(
            uri = uri,
            onDismiss = { shareCardUri = null },
            onShare = {
                shareCardUri = null
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "image/png"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        null
                    )
                )
            }
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, onClick: () -> Unit, onShare: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = stringResource(R.string.profile_share_content_description)
                    )
                }
            }
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (profile.bestColors.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_no_best_colors),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    profile.bestColors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color(color.argb))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharePreviewDialog(uri: Uri, onDismiss: () -> Unit, onShare: () -> Unit) {
    val context = LocalContext.current
    val imageBitmap = remember(uri) {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_preview_title)) },
        text = {
            imageBitmap?.let {
                Image(
                    bitmap = it,
                    contentDescription = stringResource(R.string.share_preview_image_content_description),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onShare) {
                Text(stringResource(R.string.share_preview_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
