package com.example.hueandyou.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.hueandyou.R
import com.example.hueandyou.data.history.ClothingCategory

/**
 * The "In my wardrobe" switch and the optional category chips of a clothing item. Tapping the
 * selected chip again clears the category.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WardrobeDetailsEditor(
    inWardrobe: Boolean,
    onInWardrobeChange: (Boolean) -> Unit,
    category: ClothingCategory?,
    onCategoryChange: (ClothingCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = inWardrobe, role = Role.Switch, onValueChange = onInWardrobeChange),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.wardrobe_switch_label), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = inWardrobe, onCheckedChange = null)
        }
        Text(stringResource(R.string.wardrobe_category_label), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClothingCategory.entries.forEach { option ->
                val selected = option == category
                FilterChip(
                    selected = selected,
                    onClick = { onCategoryChange(if (selected) null else option) },
                    label = { Text(stringResource(clothingCategoryLabel(option))) },
                )
            }
        }
    }
}

@StringRes
fun clothingCategoryLabel(category: ClothingCategory): Int = when (category) {
    ClothingCategory.TOP -> R.string.clothing_category_top
    ClothingCategory.BOTTOM -> R.string.clothing_category_bottom
    ClothingCategory.ONE_PIECE -> R.string.clothing_category_one_piece
    ClothingCategory.OUTERWEAR -> R.string.clothing_category_outerwear
    ClothingCategory.SHOES -> R.string.clothing_category_shoes
    ClothingCategory.BELT -> R.string.clothing_category_belt
}
