package com.example.hueandyou.colorspace

/** A color in Google's HCT space: hue in degrees, chroma, and L*-equivalent tone. */
data class Hct(val hue: Double, val chroma: Double, val tone: Double)
