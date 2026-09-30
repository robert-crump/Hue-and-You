package com.example.hueandyou.ui.outfits

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.ClothingVerdict
import com.example.hueandyou.colorspace.OutfitHarmonyLevel
import com.example.hueandyou.colorspace.OutfitScheme
import com.example.hueandyou.ui.common.verdictAccentColor
import com.example.hueandyou.ui.common.verdictIcon

/** The Suits-you roll-up's name; the icon and color are the single-item verdict's. */
@StringRes
internal fun suitsYouLabel(verdict: ClothingVerdict): Int = when (verdict) {
    ClothingVerdict.YES -> R.string.outfit_verdict_suits_you
    ClothingVerdict.NEITHER -> R.string.outfit_verdict_mixed
    ClothingVerdict.AVOID -> R.string.outfit_verdict_has_avoid
}

@StringRes
internal fun harmonyLabel(level: OutfitHarmonyLevel): Int = when (level) {
    OutfitHarmonyLevel.WORKS -> R.string.outfit_harmony_works
    OutfitHarmonyLevel.BORDERLINE -> R.string.outfit_harmony_borderline
    OutfitHarmonyLevel.CLASHES -> R.string.outfit_harmony_clashes
}

internal fun harmonyIcon(level: OutfitHarmonyLevel): ImageVector = when (level) {
    OutfitHarmonyLevel.WORKS -> Icons.Filled.CheckCircle
    OutfitHarmonyLevel.BORDERLINE -> Icons.Filled.RemoveCircle
    OutfitHarmonyLevel.CLASHES -> Icons.Filled.Cancel
}

/** Same good / in-between / bad accents as the clothing verdict. */
@Composable
internal fun harmonyAccentColor(level: OutfitHarmonyLevel): Color = verdictAccentColor(
    when (level) {
        OutfitHarmonyLevel.WORKS -> ClothingVerdict.YES
        OutfitHarmonyLevel.BORDERLINE -> ClothingVerdict.NEITHER
        OutfitHarmonyLevel.CLASHES -> ClothingVerdict.AVOID
    }
)

@StringRes
internal fun schemeLabel(scheme: OutfitScheme): Int = when (scheme) {
    OutfitScheme.NEUTRAL -> R.string.outfit_scheme_neutral
    OutfitScheme.TONAL -> R.string.outfit_scheme_tonal
    OutfitScheme.ANALOGOUS -> R.string.outfit_scheme_analogous
    OutfitScheme.COMPLEMENTARY -> R.string.outfit_scheme_complementary
}

/** The Suits-you roll-up as a bare accent-colored icon; TalkBack reads its name. */
@Composable
internal fun SuitsYouIcon(verdict: ClothingVerdict, size: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.outfit_suits_you_content_description, stringResource(suitsYouLabel(verdict)))
    Icon(
        verdictIcon(verdict),
        contentDescription = null,
        tint = verdictAccentColor(verdict),
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = description },
    )
}

/** The Works-together level as a bare accent-colored icon; TalkBack reads its name. */
@Composable
internal fun HarmonyIcon(level: OutfitHarmonyLevel, size: Dp, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.outfit_harmony_content_description, stringResource(harmonyLabel(level)))
    Icon(
        harmonyIcon(level),
        contentDescription = null,
        tint = harmonyAccentColor(level),
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { contentDescription = description },
    )
}
