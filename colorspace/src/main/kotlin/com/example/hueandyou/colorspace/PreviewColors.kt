package com.example.hueandyou.colorspace

import kotlin.math.abs

/**
 * Chroma below which a color reads as a neutral: its hue is unstable, so it doesn't count toward
 * hue spread and is only shown when there aren't enough colorful ones.
 */
private const val PREVIEW_MIN_CHROMA = 16.0

/**
 * Up to [count] of [colors] that represent the palette at a glance: the most colorful one first,
 * then each next one as far in hue from those already picked as possible (ties to higher chroma).
 * If fewer than [count] colors are colorful, the rest are filled by chroma, highest first.
 */
fun previewColors(colors: List<Int>, count: Int): List<Int> {
    val byChroma = colors.distinct()
        .map { it to argbToHct(it) }
        .sortedByDescending { (_, hct) -> hct.chroma }
    val colorful = byChroma.filter { (_, hct) -> hct.chroma >= PREVIEW_MIN_CHROMA }.toMutableList()
    val picked = mutableListOf<Pair<Int, Hct>>()
    while (picked.size < count && colorful.isNotEmpty()) {
        val next = if (picked.isEmpty()) {
            colorful.first()
        } else {
            // byChroma order makes maxBy keep the higher-chroma color on a tie.
            colorful.maxBy { (_, hct) -> picked.minOf { (_, other) -> hueDistance(hct.hue, other.hue) } }
        }
        picked += next
        colorful -= next
    }
    val rest = byChroma.filter { it !in picked }.take(count - picked.size)
    return (picked + rest).map { (argb, _) -> argb }
}

private fun hueDistance(a: Double, b: Double): Double {
    val d = abs(a - b) % 360.0
    return if (d > 180.0) 360.0 - d else d
}
