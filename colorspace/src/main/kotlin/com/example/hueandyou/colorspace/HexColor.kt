package com.example.hueandyou.colorspace

private val HEX_COLOR_PATTERN = Regex("^#?[0-9A-Fa-f]{6}$")

/** Parses `#RRGGBB` or `RRGGBB` (case-insensitive) into an opaque ARGB int, or null if invalid. */
fun parseHexColor(hex: String): Int? {
    if (!HEX_COLOR_PATTERN.matches(hex)) return null
    val rgb = hex.removePrefix("#").toInt(16)
    return (0xFF shl 24) or rgb
}

/** Formats the RGB channels of an ARGB int (alpha ignored) as `#RRGGBB`. */
fun formatHexColor(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
