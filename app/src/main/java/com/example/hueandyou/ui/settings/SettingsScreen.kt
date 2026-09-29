package com.example.hueandyou.ui.settings

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.previewColors
import com.example.hueandyou.data.profile.Profile
import com.example.hueandyou.ui.common.VerdictBadge
import com.example.hueandyou.ui.common.harmonyBalanceDescription
import com.example.hueandyou.ui.common.harmonyBalanceLabel
import com.example.hueandyou.ui.common.harmonyWheelDescription
import com.example.hueandyou.ui.common.harmonyWheelLabel
import com.example.hueandyou.ui.common.verdictLabel
import com.example.hueandyou.ui.profiles.ProfilesViewModel
import kotlinx.coroutines.launch

/** Position of the Profiles header in the lazy list, for the "go to Settings" jump from Rate Clothing. */
private const val PROFILES_HEADER_INDEX = 3

@Composable
fun SettingsScreen(
    scrollToProfiles: Boolean = false,
    onOpenProfile: (Long) -> Unit,
    onImportNewProfile: () -> Unit,
    onFindSeason: () -> Unit,
    profilesViewModel: ProfilesViewModel = viewModel(factory = ProfilesViewModel.factory(LocalContext.current)),
    settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(LocalContext.current)),
) {
    val profiles by profilesViewModel.profiles.collectAsState()
    val defaults by settingsViewModel.defaults.collectAsState()
    val listState = rememberLazyListState()
    val defaultProfileName = stringResource(R.string.profile_default_name)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var shareCardUri by remember { mutableStateOf<Uri?>(null) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    var showWheelDialog by remember { mutableStateOf(false) }
    var showBalanceDialog by remember { mutableStateOf(false) }
    var showAddProfileSheet by remember { mutableStateOf(false) }

    val exportSuccessMessage = stringResource(R.string.settings_backup_export_success)
    val exportFailedMessage = stringResource(R.string.settings_backup_export_failed)
    val importReadFailedMessage = stringResource(R.string.settings_backup_import_read_failed)
    val importSuccessMessage = stringResource(R.string.settings_backup_import_success)

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        settingsViewModel.exportBackup { result ->
            val written = result.getOrNull()?.let { json ->
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }.isSuccess
            } == true
            showMessage(if (written) exportSuccessMessage else exportFailedMessage)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (text == null) {
            showMessage(importReadFailedMessage)
        } else {
            pendingImportJson = text
        }
    }

    LaunchedEffect(scrollToProfiles) {
        if (scrollToProfiles) {
            listState.animateScrollToItem(PROFILES_HEADER_INDEX)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "object_header") {
                SectionHeader(stringResource(R.string.settings_object_section_title))
            }
            item(key = "object_group") {
                SettingsGroup(
                    rows = listOf(
                        {
                            SettingsRow(
                                icon = Icons.Filled.Palette,
                                title = stringResource(R.string.settings_wheel_title),
                                summary = stringResource(harmonyWheelLabel(defaults.wheel)),
                                onClick = { showWheelDialog = true }
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Tune,
                                title = stringResource(R.string.settings_balance_title),
                                summary = stringResource(harmonyBalanceLabel(defaults.balance)),
                                onClick = { showBalanceDialog = true }
                            )
                        },
                    )
                )
            }
            item(key = "object_hint") {
                Text(
                    text = stringResource(R.string.settings_object_section_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            item(key = "profiles_header") {
                SectionHeader(stringResource(R.string.profiles_section_title))
            }
            item(key = "profiles_group") {
                val profileRows = profiles.map { profile ->
                    @Composable {
                        ProfileRow(
                            profile = profile,
                            onClick = { onOpenProfile(profile.id) },
                            onShare = {
                                profilesViewModel.shareCard(profile) { uri -> shareCardUri = uri }
                            }
                        )
                    }
                }
                val addRow = @Composable {
                    SettingsRow(
                        icon = Icons.Filled.Add,
                        title = stringResource(R.string.profile_add_content_description),
                        onClick = { showAddProfileSheet = true }
                    )
                }
                SettingsGroup(rows = profileRows + addRow)
            }
            item(key = "backup_header") {
                SectionHeader(stringResource(R.string.settings_backup_section_title))
            }
            item(key = "backup_group") {
                SettingsGroup(
                    rows = listOf(
                        {
                            SettingsRow(
                                icon = Icons.Filled.Upload,
                                title = stringResource(R.string.settings_backup_export),
                                summary = stringResource(R.string.settings_backup_export_summary),
                                onClick = { exportLauncher.launch("hue_and_you_backup.json") }
                            )
                        },
                        {
                            SettingsRow(
                                icon = Icons.Filled.Download,
                                title = stringResource(R.string.settings_backup_import),
                                summary = stringResource(R.string.settings_backup_import_summary),
                                onClick = { importLauncher.launch(arrayOf("application/json")) }
                            )
                        },
                    )
                )
            }
            item(key = "about_header") {
                SectionHeader(stringResource(R.string.settings_about_section_title))
            }
            item(key = "about_body") {
                AboutSection(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp))
            }
        }
    }

    if (showAddProfileSheet) {
        AddProfileSheet(
            onDismiss = { showAddProfileSheet = false },
            onFindSeason = onFindSeason,
            onImport = onImportNewProfile,
            onCustom = {
                profilesViewModel.createProfile(defaultProfileName) { id -> onOpenProfile(id) }
            }
        )
    }
    if (showWheelDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_wheel_title),
            options = HarmonyWheel.entries,
            selected = defaults.wheel,
            label = { stringResource(harmonyWheelLabel(it)) },
            description = { stringResource(harmonyWheelDescription(it)) },
            onSelect = {
                settingsViewModel.setDefaultWheel(it)
                showWheelDialog = false
            },
            onDismiss = { showWheelDialog = false }
        )
    }

    if (showBalanceDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_balance_title),
            options = HarmonyBalance.entries,
            selected = defaults.balance,
            label = { stringResource(harmonyBalanceLabel(it)) },
            description = { stringResource(harmonyBalanceDescription(it)) },
            onSelect = {
                settingsViewModel.setDefaultBalance(it)
                showBalanceDialog = false
            },
            onDismiss = { showBalanceDialog = false }
        )
    }

    pendingImportJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text(stringResource(R.string.settings_backup_import_confirm_title)) },
            text = { Text(stringResource(R.string.settings_backup_import_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportJson = null
                    settingsViewModel.importBackup(json) { result ->
                        showMessage(
                            result.fold(
                                onSuccess = { importSuccessMessage },
                                onFailure = { e -> e.message ?: importReadFailedMessage }
                            )
                        )
                    }
                }) {
                    Text(stringResource(R.string.settings_backup_import_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
    )
}

private val GroupOuterCorner = 24.dp
private val GroupInnerCorner = 4.dp

/** Rounded rows separated by a small gap; only the outer corners of the first and last row are large. */
@Composable
private fun SettingsGroup(rows: List<@Composable () -> Unit>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEachIndexed { index, row ->
            val top = if (index == 0) GroupOuterCorner else GroupInnerCorner
            val bottom = if (index == rows.lastIndex) GroupOuterCorner else GroupInnerCorner
            Surface(
                shape = RoundedCornerShape(top, top, bottom, bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                row()
            }
        }
    }
}

/** The "New profile" chooser. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProfileSheet(
    onDismiss: () -> Unit,
    onFindSeason: () -> Unit,
    onImport: () -> Unit,
    onCustom: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    fun dismissThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.profile_add_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        SettingsRow(
            icon = Icons.Filled.Face,
            title = stringResource(R.string.profile_add_season_title),
            summary = stringResource(R.string.profile_add_season_summary),
            onClick = { dismissThen(onFindSeason) }
        )
        SettingsRow(
            icon = Icons.Filled.PhotoLibrary,
            title = stringResource(R.string.profile_add_import_title),
            summary = stringResource(R.string.profile_add_import_summary),
            onClick = { dismissThen(onImport) }
        )
        SettingsRow(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.profile_add_custom_title),
            summary = stringResource(R.string.profile_add_custom_summary),
            onClick = { dismissThen(onCustom) }
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun ProfileRow(profile: Profile, onClick: () -> Unit, onShare: () -> Unit) {
    val dotColors = remember(profile.bestColors) {
        previewColors(profile.bestColors.map { it.argb }, MAX_PROFILE_DOTS)
    }
    val surface = MaterialTheme.colorScheme.surfaceContainer
    ListItem(
        headlineContent = { Text(profile.name) },
        supportingContent = {
            Text(
                if (profile.bestColors.isEmpty()) {
                    stringResource(R.string.profile_no_best_colors)
                } else {
                    pluralStringResource(
                        R.plurals.profile_best_colors_count,
                        profile.bestColors.size,
                        profile.bestColors.size
                    )
                }
            )
        },
        leadingContent = {
            // Overlapping dots; a ring in the row color separates neighbours.
            Box(modifier = Modifier.width(DOT_SIZE + DOT_STEP * (MAX_PROFILE_DOTS - 1))) {
                dotColors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .offset(x = DOT_STEP * index)
                            .size(DOT_SIZE)
                            .clip(CircleShape)
                            .background(Color(color))
                            .border(2.dp, surface, CircleShape)
                    )
                }
            }
        },
        trailingContent = {
            IconButton(onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.profile_share_content_description)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

private const val MAX_PROFILE_DOTS = 4
private val DOT_SIZE = 24.dp
private val DOT_STEP = 16.dp

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    description: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // No padding after the last option: the dialog already spaces the text from the buttons.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(12.dp))
                        Column {
                            Text(label(option), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                description(option),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun AboutSection(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(GroupOuterCorner),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.settings_about_intro), style = MaterialTheme.typography.bodyLarge)
            ClothingVerdict.entries.forEach { VerdictLine(it) }
            Text(
                stringResource(R.string.settings_about_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Same icon and colors as the verdict card on the result screens, so the legend matches what kids see there. */
@Composable
private fun VerdictLine(verdict: ClothingVerdict) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VerdictBadge(verdict, Modifier.size(36.dp))
        Text(stringResource(verdictLabel(verdict)), style = MaterialTheme.typography.titleSmall)
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
