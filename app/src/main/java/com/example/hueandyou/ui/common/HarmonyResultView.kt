package com.example.hueandyou.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.HarmonyBalance
import com.example.hueandyou.colorspace.HarmonyEngine
import com.example.hueandyou.colorspace.HarmonyRelationship
import com.example.hueandyou.colorspace.HarmonySuggestion
import com.example.hueandyou.colorspace.HarmonyWheel
import com.example.hueandyou.colorspace.formatHexColor

/**
 * Renders one set of harmony suggestions per selected input color, recomputed from
 * [inputColorsArgb], [wheel] and [balance] rather than from any stored/precomputed colors - shared
 * by the live Match Colors for an Object result step and the History detail screen that reopens a
 * saved snapshot of the same inputs.
 */
@Composable
internal fun HarmonyResultBody(inputColorsArgb: List<Int>, wheel: HarmonyWheel, balance: HarmonyBalance) {
    Column {
        inputColorsArgb.forEach { inputArgb ->
            HarmonyInputColorSection(inputArgb = inputArgb, wheel = wheel, balance = balance)
        }
        Text(
            text = stringResource(R.string.rate_clothing_best_guess_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 24.dp)
        )
    }
}

@Composable
private fun HarmonyInputColorSection(inputArgb: Int, wheel: HarmonyWheel, balance: HarmonyBalance) {
    val suggestions = remember(inputArgb, wheel, balance) {
        HarmonyEngine.generate(inputArgb, wheel, balance)
    }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(argb = inputArgb, size = 48.dp)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = stringResource(R.string.match_object_input_color_label),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(text = formatHexColor(inputArgb), style = MaterialTheme.typography.bodyMedium)
            }
        }
        suggestions.forEach { suggestion ->
            HarmonySuggestionRow(suggestion)
        }
    }
}

@Composable
private fun HarmonySuggestionRow(suggestion: HarmonySuggestion) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(harmonyRelationshipLabel(suggestion.relationship)),
            style = MaterialTheme.typography.titleSmall
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            suggestion.colors.forEach { argb ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ColorSwatch(argb = argb, size = 40.dp)
                    Text(
                        text = formatHexColor(argb),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(argb: Int, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(argb))
    )
}

internal fun harmonyRelationshipLabel(relationship: HarmonyRelationship): Int = when (relationship) {
    HarmonyRelationship.COMPLEMENTARY -> R.string.harmony_relationship_complementary
    HarmonyRelationship.SPLIT_COMPLEMENTARY -> R.string.harmony_relationship_split_complementary
    HarmonyRelationship.ANALOGOUS -> R.string.harmony_relationship_analogous
    HarmonyRelationship.TRIADIC -> R.string.harmony_relationship_triadic
    HarmonyRelationship.TONAL -> R.string.harmony_relationship_tonal
}
