package com.example.hueandyou.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyEngine
import com.example.hueandyou.colorspace.HarmonyRelationship
import com.example.hueandyou.colorspace.HarmonySuggestion
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.argbToHct

private val RELATIONSHIP_SWATCH_SIZE = 64.dp

/** Swatch height plus the 12dp top padding of [HarmonySuggestionRow]. */
private val SUGGESTION_ROW_HEIGHT = RELATIONSHIP_SWATCH_SIZE + 12.dp

/**
 * Renders the harmony suggestions for [inputColorArgb], recomputed from it, [wheel] and [balance]
 * rather than from any stored/precomputed colors - shared by the live Match Colors for an Object
 * result step and the History detail screen that reopens a saved snapshot of the same input. No
 * hex codes, name field or wheel/balance controls here - those live elsewhere (chips, History's
 * own rename field, and Settings, respectively).
 */
@Composable
internal fun HarmonyResultBody(inputColorArgb: Int, wheel: HarmonyWheel, balance: HarmonyBalance) {
    val suggestions = remember(inputColorArgb, wheel, balance) {
        HarmonyEngine.generate(inputColorArgb, wheel, balance)
    }
    val isNeutral = remember(inputColorArgb) { HarmonyEngine.isNeutral(argbToHct(inputColorArgb).chroma) }

    // The body always occupies the space of all three rows, so switching between a neutral color
    // (Tonal only) and a chromatic one only fades rows in/out and never resizes the photo above.
    Column {
        ChromaticRows(isNeutral = isNeutral, suggestions = suggestions)
        HarmonySuggestionRow(suggestions.single { it.relationship == HarmonyRelationship.TONAL })
    }
}

@Composable
private fun ChromaticRows(isNeutral: Boolean, suggestions: List<HarmonySuggestion>) {
    Box(modifier = Modifier.fillMaxWidth().height(SUGGESTION_ROW_HEIGHT * 2)) {
        AnimatedVisibility(visible = isNeutral, enter = fadeIn(), exit = fadeOut()) {
            NeutralHintRow()
        }
        AnimatedVisibility(visible = !isNeutral, enter = fadeIn(), exit = fadeOut()) {
            Column {
                HarmonySuggestionRow(suggestions.single { it.relationship == HarmonyRelationship.COMPLEMENTARY })
                HarmonySuggestionRow(suggestions.single { it.relationship == HarmonyRelationship.ANALOGOUS })
            }
        }
    }
}

@Composable
private fun NeutralHintRow() {
    Text(
        text = stringResource(R.string.harmony_neutral_hint),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Composable
private fun HarmonySuggestionRow(suggestion: HarmonySuggestion) {
    var showInfo by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(harmonyRelationshipLabel(suggestion.relationship)),
                style = MaterialTheme.typography.titleSmall
            )
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = stringResource(R.string.harmony_relationship_info_content_description),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            suggestion.colors.forEach { argb -> ColorCircle(argb = argb, size = RELATIONSHIP_SWATCH_SIZE) }
        }
    }

    if (showInfo) {
        InfoDialog(
            title = stringResource(harmonyRelationshipLabel(suggestion.relationship)),
            text = stringResource(harmonyRelationshipExplanation(suggestion.relationship)),
            onDismiss = { showInfo = false },
        )
    }
}

internal fun harmonyRelationshipLabel(relationship: HarmonyRelationship): Int = when (relationship) {
    HarmonyRelationship.COMPLEMENTARY -> R.string.harmony_relationship_complementary
    HarmonyRelationship.ANALOGOUS -> R.string.harmony_relationship_analogous
    HarmonyRelationship.TONAL -> R.string.harmony_relationship_tonal
}

internal fun harmonyRelationshipExplanation(relationship: HarmonyRelationship): Int = when (relationship) {
    HarmonyRelationship.COMPLEMENTARY -> R.string.harmony_relationship_complementary_explanation
    HarmonyRelationship.ANALOGOUS -> R.string.harmony_relationship_analogous_explanation
    HarmonyRelationship.TONAL -> R.string.harmony_relationship_tonal_explanation
}
