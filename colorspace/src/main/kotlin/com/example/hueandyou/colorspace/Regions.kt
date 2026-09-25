package com.example.hueandyou.colorspace

/** An axis-aligned pixel region; [right] and [bottom] are exclusive, like a half-open range. */
data class RectRegion(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun clampTo(width: Int, height: Int): RectRegion = RectRegion(
        left = left.coerceIn(0, width),
        top = top.coerceIn(0, height),
        right = right.coerceIn(0, width),
        bottom = bottom.coerceIn(0, height),
    )

    companion object {
        fun fullImage(width: Int, height: Int) = RectRegion(0, 0, width, height)

        /** The box covering the middle [fraction] of [width] and [height], centered in the image. */
        fun centerBox(width: Int, height: Int, fraction: Double): RectRegion {
            val boxWidth = (width * fraction).toInt().coerceAtLeast(1)
            val boxHeight = (height * fraction).toInt().coerceAtLeast(1)
            val left = (width - boxWidth) / 2
            val top = (height - boxHeight) / 2
            return RectRegion(left, top, left + boxWidth, top + boxHeight)
        }
    }
}
