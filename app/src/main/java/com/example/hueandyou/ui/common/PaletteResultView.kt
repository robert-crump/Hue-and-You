package com.example.hueandyou.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.PaletteScore
import com.example.hueandyou.ui.theme.LocalSuccessColors

/**
 * The verdict card - one row with an icon and "Great color!" / "Avoid" / "Ambiguous" - shared by
 * the live Rate Clothing result step and the History detail screen that reopens a saved snapshot
 * of the same result. The verdict is derived from [score] rather than stored, so History entries
 * saved before the verdict existed still get one.
 */
@Composable
internal fun PaletteResultBody(score: PaletteScore, modifier: Modifier = Modifier) {
    val verdict = remember(score) { ClothingVerdict.forScore(score) }
    val (containerColor, contentColor) = verdictColors(verdict)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(verdictIcon(verdict), contentDescription = null, modifier = Modifier.size(28.dp))
            Text(text = stringResource(verdictLabel(verdict)), style = MaterialTheme.typography.titleMedium)
        }
    }
}

internal fun verdictLabel(verdict: ClothingVerdict): Int = when (verdict) {
    ClothingVerdict.YES -> R.string.rate_clothing_verdict_yes
    ClothingVerdict.AVOID -> R.string.rate_clothing_verdict_avoid
    ClothingVerdict.NEITHER -> R.string.rate_clothing_verdict_neither
}

internal fun verdictIcon(verdict: ClothingVerdict): ImageVector = when (verdict) {
    ClothingVerdict.YES -> Icons.Filled.ThumbUpAlt
    ClothingVerdict.AVOID -> Icons.Filled.ThumbDownAlt
    ClothingVerdict.NEITHER -> Icons.Filled.HorizontalRule
}

/** The verdict's (container, content) colors, shared by the card, the badge and the Settings legend. */
@Composable
internal fun verdictColors(verdict: ClothingVerdict): Pair<Color, Color> {
    val successColors = LocalSuccessColors.current
    return when (verdict) {
        ClothingVerdict.YES -> successColors.container to successColors.onContainer
        ClothingVerdict.AVOID -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        ClothingVerdict.NEITHER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
}

/** The verdict's accent color, for its icon drawn bare on a surface (e.g. a History row). */
@Composable
internal fun verdictAccentColor(verdict: ClothingVerdict): Color = when (verdict) {
    ClothingVerdict.YES -> LocalSuccessColors.current.content
    ClothingVerdict.AVOID -> MaterialTheme.colorScheme.error
    ClothingVerdict.NEITHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** The verdict icon in a circle of its container color, filling whatever size [modifier] gives it. */
@Composable
internal fun VerdictBadge(verdict: ClothingVerdict, modifier: Modifier = Modifier) {
    val (containerColor, contentColor) = verdictColors(verdict)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            verdictIcon(verdict),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.fillMaxSize(VERDICT_BADGE_ICON_FRACTION)
        )
    }
}

/** Icon share of the badge: 20dp in a 36dp circle. */
private const val VERDICT_BADGE_ICON_FRACTION = 20f / 36f
