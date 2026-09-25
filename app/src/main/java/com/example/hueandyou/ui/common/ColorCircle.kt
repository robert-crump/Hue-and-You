package com.example.hueandyou.ui.common

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import com.example.hueandyou.colorspace.formatHexColor
import kotlinx.coroutines.launch

/**
 * The one color-circle composable used everywhere a color swatch appears (Object harmony
 * swatches, result-page chips, the verdict card, Profile editor's Best/Avoid list and History
 * detail). Long-pressing it always copies its hex to the clipboard with a haptic tick - the
 * system shows its own clipboard confirmation (minSdk 37), so there's no app-side toast. [onClick]
 * is optional so non-interactive circles (e.g. the verdict card) don't get a ripple.
 */
@Composable
internal fun ColorCircle(
    argb: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboard.current
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val hex = remember(argb) { formatHexColor(argb) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(argb))
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (onClick != null) LocalIndication.current else null,
                onClick = { onClick?.invoke() },
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    coroutineScope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(hex, hex)))
                    }
                },
            )
    )
}
