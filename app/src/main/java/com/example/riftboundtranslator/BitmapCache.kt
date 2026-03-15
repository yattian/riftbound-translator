package com.example.riftboundtranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.IOException

object BitmapCache {
    private const val MAX_CACHE_SIZE_MB = 50
    private const val BYTES_PER_MB = 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE_MB * BYTES_PER_MB) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }

        override fun entryRemoved(
            evicted: Boolean,
            key: String,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (evicted && !oldValue.isRecycled) {
                oldValue.recycle()
            }
        }
    }

    fun loadCard(
        context: Context,
        folderName: String,
        cardId: String,
        maxWidth: Int = 0,
        maxHeight: Int = 0
    ): Bitmap? {
        val cacheKey = "$folderName/$cardId"

        cache.get(cacheKey)?.let { return it }

        val extensions = listOf(".webp", ".png", ".jpg", ".WEBP", ".PNG", ".JPG")

        for (ext in extensions) {
            try {
                val fileName = "$folderName/$cardId$ext"
                val bitmap = if (maxWidth > 0 && maxHeight > 0) {
                    loadDownsampledBitmap(context, fileName, maxWidth, maxHeight)
                } else {
                    context.assets.open(fileName).use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }

                if (bitmap != null) {
                    cache.put(cacheKey, bitmap)
                    return bitmap
                }
            } catch (e: IOException) {
                continue
            }
        }

        return null
    }

    private fun loadDownsampledBitmap(
        context: Context,
        fileName: String,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        context.assets.open(fileName).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
        options.inJustDecodeBounds = false

        return context.assets.open(fileName).use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight
                && halfWidth / inSampleSize >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    fun clear() {
        cache.evictAll()
    }
}
