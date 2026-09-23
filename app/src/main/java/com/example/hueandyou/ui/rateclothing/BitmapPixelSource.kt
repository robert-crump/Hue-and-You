package com.example.hueandyou.ui.rateclothing

import android.graphics.Bitmap
import com.example.hueandyou.colorspace.IntArrayPixelSource

fun Bitmap.toPixelSource(): IntArrayPixelSource {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return IntArrayPixelSource(width, height, pixels)
}
