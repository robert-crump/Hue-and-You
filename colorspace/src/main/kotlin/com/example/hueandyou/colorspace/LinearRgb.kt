package com.example.hueandyou.colorspace

/**
 * Gamma-decoded (linear-light) RGB. Components are on a 0-100 scale so that they line up
 * with CIE Y (relative luminance), matching the XYZ/Lab math this type feeds into.
 */
data class LinearRgb(val r: Double, val g: Double, val b: Double)
