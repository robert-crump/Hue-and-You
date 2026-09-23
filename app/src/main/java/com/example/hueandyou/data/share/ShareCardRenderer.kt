package com.example.hueandyou.data.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.hueandyou.R
import com.example.hueandyou.colorspace.formatHexColor
import com.example.hueandyou.data.profile.PaletteColor
import com.example.hueandyou.data.profile.Profile
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders a [Profile] into a shareable PNG palette card. */
interface ShareCardRenderer {
    /** Renders [profile] to a PNG in app cache and returns a FileProvider [Uri] for it. */
    suspend fun render(profile: Profile): Uri
}

class FileShareCardRenderer(private val context: Context) : ShareCardRenderer {

    override suspend fun render(profile: Profile): Uri = withContext(Dispatchers.IO) {
        val bitmap = drawCard(profile)
        val dir = File(context.cacheDir, "share_cards").apply { mkdirs() }
        val file = File(dir, "share_${UUID.randomUUID()}.png")
        file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        bitmap.recycle()
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun drawCard(profile: Profile): Bitmap {
        val perRow = maxOf(1, (CARD_WIDTH - 2 * PADDING + SWATCH_SPACING) / (SWATCH_SIZE + SWATCH_SPACING))

        val titlePaint = textPaint(TITLE_TEXT_SIZE, bold = true)
        val sectionPaint = textPaint(SECTION_TEXT_SIZE, bold = true)
        val hexPaint = textPaint(HEX_TEXT_SIZE, bold = false).apply { textAlign = Paint.Align.CENTER }
        val emptyPaint = textPaint(HEX_TEXT_SIZE, bold = false)
        val emptyLineHeight = emptyPaint.textHeight()

        val bestColors = profile.bestColors
        val avoidColors = profile.avoidColors
        val bestHeight = sectionContentHeight(bestColors.size, perRow, emptyLineHeight)
        val avoidHeight = sectionContentHeight(avoidColors.size, perRow, emptyLineHeight)

        val height = PADDING * 2 +
            titlePaint.textHeight() + SECTION_SPACING +
            sectionPaint.textHeight() + SECTION_ROW_SPACING + bestHeight + SECTION_SPACING +
            sectionPaint.textHeight() + SECTION_ROW_SPACING + avoidHeight

        val bitmap = Bitmap.createBitmap(CARD_WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        var y = PADDING + titlePaint.textHeight()
        canvas.drawText(profile.name, PADDING.toFloat(), y.toFloat(), titlePaint)
        y += SECTION_SPACING

        y = drawSection(
            canvas = canvas,
            title = context.getString(R.string.profile_editor_best_colors_title),
            colors = bestColors,
            startY = y,
            perRow = perRow,
            sectionPaint = sectionPaint,
            hexPaint = hexPaint,
            emptyPaint = emptyPaint,
            emptyLineHeight = emptyLineHeight,
        )
        y += SECTION_SPACING

        drawSection(
            canvas = canvas,
            title = context.getString(R.string.profile_editor_avoid_colors_title),
            colors = avoidColors,
            startY = y,
            perRow = perRow,
            sectionPaint = sectionPaint,
            hexPaint = hexPaint,
            emptyPaint = emptyPaint,
            emptyLineHeight = emptyLineHeight,
        )

        return bitmap
    }

    private fun drawSection(
        canvas: Canvas,
        title: String,
        colors: List<PaletteColor>,
        startY: Int,
        perRow: Int,
        sectionPaint: Paint,
        hexPaint: Paint,
        emptyPaint: Paint,
        emptyLineHeight: Int,
    ): Int {
        var y = startY + sectionPaint.textHeight()
        canvas.drawText(title, PADDING.toFloat(), y.toFloat(), sectionPaint)
        y += SECTION_ROW_SPACING

        if (colors.isEmpty()) {
            canvas.drawText(
                context.getString(R.string.profile_editor_no_colors),
                PADDING.toFloat(),
                (y + emptyLineHeight).toFloat(),
                emptyPaint
            )
        } else {
            val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            colors.forEachIndexed { index, color ->
                val row = index / perRow
                val col = index % perRow
                val left = PADDING + col * (SWATCH_SIZE + SWATCH_SPACING)
                val top = y + row * SWATCH_ROW_HEIGHT
                swatchPaint.color = color.argb
                canvas.drawRoundRect(
                    left.toFloat(),
                    top.toFloat(),
                    (left + SWATCH_SIZE).toFloat(),
                    (top + SWATCH_SIZE).toFloat(),
                    SWATCH_CORNER_RADIUS,
                    SWATCH_CORNER_RADIUS,
                    swatchPaint
                )
                canvas.drawText(
                    formatHexColor(color.argb),
                    left + SWATCH_SIZE / 2f,
                    (top + SWATCH_SIZE + HEX_LABEL_HEIGHT - HEX_LABEL_BOTTOM_INSET).toFloat(),
                    hexPaint
                )
            }
        }

        return y + sectionContentHeight(colors.size, perRow, emptyLineHeight)
    }

    private fun sectionContentHeight(count: Int, perRow: Int, emptyLineHeight: Int): Int {
        if (count == 0) return emptyLineHeight
        val rows = ceil(count / perRow.toFloat()).toInt()
        return rows * SWATCH_ROW_HEIGHT - SWATCH_SPACING
    }

    private fun textPaint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        color = Color.BLACK
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun Paint.textHeight(): Int = (-fontMetrics.ascent + fontMetrics.descent).toInt()

    private companion object {
        const val CARD_WIDTH = 1080
        const val PADDING = 48
        const val SWATCH_SIZE = 168
        const val SWATCH_SPACING = 32
        const val SWATCH_CORNER_RADIUS = 16f
        const val HEX_LABEL_HEIGHT = 40
        const val HEX_LABEL_BOTTOM_INSET = 8
        const val SWATCH_ROW_HEIGHT = SWATCH_SIZE + HEX_LABEL_HEIGHT + SWATCH_SPACING
        const val TITLE_TEXT_SIZE = 56f
        const val SECTION_TEXT_SIZE = 40f
        const val HEX_TEXT_SIZE = 28f
        const val SECTION_SPACING = 40
        const val SECTION_ROW_SPACING = 24
    }
}
