package com.example.hueandyou.colorspace

/** A circular region in pixel coordinates, e.g. the white-sheet sample tapped by the user. */
data class CircleRegion(val centerX: Int, val centerY: Int, val radius: Int) {
    fun contains(x: Int, y: Int): Boolean {
        val dx = (x - centerX).toLong()
        val dy = (y - centerY).toLong()
        return dx * dx + dy * dy <= radius.toLong() * radius
    }
}

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
    }
}
