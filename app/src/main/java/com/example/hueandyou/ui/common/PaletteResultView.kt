package com.example.hueandyou.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ColorMatch
import com.example.hueandyou.colorspace.ColorMatchBand
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.colorspace.formatHexColor

/**
 * The measured-color swatch, closer-to-avoid warning, nearest Best/Avoid matches and disclaimer
 * shared by the live Rate Clothing result step and the History detail screen that reopens a
 * saved snapshot of the same result.
 */
@Composable
internal fun PaletteResultBody(argb: Int, score: PaletteScore) {
    Text(
        text = stringResource(R.string.rate_clothing_measured_color_label),
        style = MaterialTheme.typography.titleMedium
    )
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(120.dp)
            .clip(CircleShape)
            .background(Color(argb))
    )
    Text(
        text = formatHexColor(argb),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 8.dp)
    )

    if (score.closerToAvoid) {
        Text(
            text = stringResource(R.string.rate_clothing_closer_to_avoid_warning),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp)
        )
    }

    score.nearestBest?.let { match ->
        ColorMatchRow(labelRes = R.string.rate_clothing_nearest_best_label, match = match)
    }
    score.nearestAvoid?.let { match ->
        ColorMatchRow(labelRes = R.string.rate_clothing_nearest_avoid_label, match = match)
    }

    Text(
        text = stringResource(R.string.rate_clothing_best_guess_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 24.dp)
    )
}

@Composable
internal fun ColorMatchRow(labelRes: Int, match: ColorMatch) {
    Column(
        modifier = Modifier
            .padding(top = 24.dp)
    ) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(match.argb))
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(text = formatHexColor(match.argb))
                Text(
                    text = "ΔE %.1f — %s".format(
                        match.deltaE,
                        stringResource(colorMatchBandLabel(match.band))
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

internal fun colorMatchBandLabel(band: ColorMatchBand): Int = when (band) {
    ColorMatchBand.MATCH -> R.string.color_match_band_match
    ColorMatchBand.CLOSE -> R.string.color_match_band_close
    ColorMatchBand.RELATED -> R.string.color_match_band_related
    ColorMatchBand.FAR -> R.string.color_match_band_far
}
