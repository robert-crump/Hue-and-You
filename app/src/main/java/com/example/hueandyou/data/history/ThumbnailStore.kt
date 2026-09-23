package com.example.hueandyou.data.history

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.util.UUID
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val THUMBNAIL_MAX_DIMENSION_PX = 512

/** Saves a downscaled JPEG copy of a photo to app-private storage, for History thumbnails. */
interface ThumbnailStore {
    suspend fun save(bitmap: Bitmap): String

    /** Deletes the thumbnail file at [path], if it still exists. */
    suspend fun delete(path: String)
}

class FileThumbnailStore(private val context: Context) : ThumbnailStore {
    override suspend fun save(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "history_thumbnails").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        val scaled = scaleToLongEdge(bitmap, THUMBNAIL_MAX_DIMENSION_PX)
        file.outputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        if (scaled !== bitmap) scaled.recycle()
        file.absolutePath
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        File(path).delete()
        Unit
    }
}

private fun scaleToLongEdge(bitmap: Bitmap, maxDimension: Int): Bitmap {
    val longestSide = maxOf(bitmap.width, bitmap.height)
    if (longestSide <= maxDimension) return bitmap
    val scale = maxDimension.toFloat() / longestSide
    val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
    val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, width, height, true)
}
