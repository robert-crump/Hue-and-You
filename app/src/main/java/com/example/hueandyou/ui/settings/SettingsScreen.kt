package com.example.hueandyou.ui.settings

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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.ui.profiles.ProfilesViewModel

@Composable
fun SettingsScreen(
    scrollToProfiles: Boolean = false,
    onOpenProfile: (Long) -> Unit,
    viewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.factory(LocalContext.current))
) {
    val profiles by viewModel.profiles.collectAsState()
    val listState = rememberLazyListState()
    val defaultProfileName = stringResource(R.string.profile_default_name)

    LaunchedEffect(scrollToProfiles) {
        if (scrollToProfiles) {
            listState.animateScrollToItem(0)
        }
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
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
                    viewModel.createProfile(defaultProfileName) { id -> onOpenProfile(id) }
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
                ProfileCard(profile = profile, onClick = { onOpenProfile(profile.id) })
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: Profile, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
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
