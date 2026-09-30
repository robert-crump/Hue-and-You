package com.example.hueandyou.ui.outfits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hueandyou.R
import com.example.hueandyou.ui.common.ColorCircle

/** The Clothes Outfits tab: name, item color strip and both verdicts per outfit. */
@Composable
fun OutfitsTab(
    onOpenOutfit: (Long) -> Unit,
    viewModel: OutfitsViewModel = viewModel(factory = OutfitsViewModel.factory(LocalContext.current)),
) {
    val uiState by viewModel.uiState.collectAsState()
    when {
        uiState.isLoading -> Unit
        uiState.outfits.isEmpty() -> OutfitsEmptyState()
        // Bottom padding keeps the last row clear of the FAB.
        else -> LazyColumn(contentPadding = PaddingValues(bottom = 112.dp), modifier = Modifier.fillMaxSize()) {
            items(uiState.outfits, key = { it.id }) { outfit ->
                OutfitListRow(outfit, onClick = { onOpenOutfit(outfit.id) })
            }
        }
    }
}

@Composable
private fun OutfitListRow(outfit: OutfitRow, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = outfit.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                outfit.colorsArgb.forEach { argb ->
                    ColorCircle(
                        argb = argb,
                        size = STRIP_CIRCLE_SIZE,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    )
                }
            }
        }
        val rating = outfit.rating
        if (rating == null) {
            Text(
                text = stringResource(R.string.outfit_add_items_to_rate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rating.suitsYou?.let { SuitsYouIcon(it.overall, VERDICT_ICON_SIZE) }
                HarmonyIcon(rating.harmony.level, VERDICT_ICON_SIZE)
            }
        }
    }
}

@Composable
private fun OutfitsEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Checkroom,
            contentDescription = null,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Text(
            text = stringResource(R.string.clothes_outfits_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

private val STRIP_CIRCLE_SIZE = 20.dp
private val VERDICT_ICON_SIZE = 24.dp
