package com.example.hueandyou.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.ThumbDownAlt
import androidx.compose.material.icons.filled.ThumbUpAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.ui.theme.LocalSuccessColors

private val VERDICT_COLOR_CIRCLE_SIZE = 72.dp

/**
 * The verdict card - Yes/Avoid/Neither, plus the measured color - shared by the live Rate
 * Clothing result step and the History detail screen that reopens a saved snapshot of the same
 * result. The verdict is derived from [score] rather than stored, so History entries saved before
 * the verdict existed still get one.
 */
@Composable
internal fun PaletteResultBody(argb: Int, score: PaletteScore) {
    val verdict = remember(score) { ClothingVerdict.forScore(score) }
    val successColors = LocalSuccessColors.current

    val (containerColor, contentColor) = when (verdict) {
        ClothingVerdict.YES -> successColors.container to successColors.onContainer
        ClothingVerdict.AVOID -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ClothingVerdict.NEITHER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(verdictIcon(verdict), contentDescription = null, modifier = Modifier.size(40.dp))
            Text(
                text = stringResource(verdictLabel(verdict)),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 8.dp),
            )
            ColorCircle(
                argb = argb,
                size = VERDICT_COLOR_CIRCLE_SIZE,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

private fun verdictLabel(verdict: ClothingVerdict): Int = when (verdict) {
    ClothingVerdict.YES -> R.string.rate_clothing_verdict_yes
    ClothingVerdict.AVOID -> R.string.rate_clothing_verdict_avoid
    ClothingVerdict.NEITHER -> R.string.rate_clothing_verdict_neither
}

private fun verdictIcon(verdict: ClothingVerdict): ImageVector = when (verdict) {
    ClothingVerdict.YES -> Icons.Filled.ThumbUpAlt
    ClothingVerdict.AVOID -> Icons.Filled.ThumbDownAlt
    ClothingVerdict.NEITHER -> Icons.Filled.HorizontalRule
}
