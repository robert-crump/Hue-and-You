package com.example.hueandyou.colorspace

/** A rectangular grid of opaque ARGB pixels, decoupled from any platform bitmap type. */
interface PixelSource {
    val width: Int
    val height: Int
    fun pixelAt(x: Int, y: Int): Int
}

/** A [PixelSource] backed by a flat, row-major ARGB array. */
class IntArrayPixelSource(
    override val width: Int,
    override val height: Int,
    private val pixels: IntArray,
) : PixelSource {
    init {
        require(pixels.size == width * height) {
            "Expected ${width * height} pixels for a ${width}x$height image, got ${pixels.size}"
        }
    }

    override fun pixelAt(x: Int, y: Int): Int = pixels[y * width + x]
}
