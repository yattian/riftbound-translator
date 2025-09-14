package com.example.riftboundtranslator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.IOException
import kotlin.math.pow
import kotlin.math.sqrt

class ImageMatcher(private val context: Context) {

    data class MatchResult(
        val fileName: String,
        val similarity: Float
    )

    fun findBestMatch(capturedBitmap: Bitmap): MatchResult? {
        var bestMatch: MatchResult? = null
        var highestSimilarity = 0f

        try {
            val chineseCards = context.assets.list("chinese_cards") ?: return null

            // Focus on the artwork area (crop to center portion)
            val croppedCaptured = cropToArtwork(capturedBitmap)
            val scaledCaptured = Bitmap.createScaledBitmap(croppedCaptured, 150, 150, true)

            for (cardFile in chineseCards) {
                try {
                    val inputStream = context.assets.open("chinese_cards/$cardFile")
                    val chineseBitmap = BitmapFactory.decodeStream(inputStream)
                    val croppedChinese = cropToArtwork(chineseBitmap)
                    val scaledChinese = Bitmap.createScaledBitmap(croppedChinese, 150, 150, true)
                    inputStream.close()

                    // Calculate similarity using multiple methods
                    val colorSimilarity = compareColorHistograms(scaledCaptured, scaledChinese)
                    val structureSimilarity = compareStructure(scaledCaptured, scaledChinese)

                    val totalSimilarity = (colorSimilarity * 0.6f + structureSimilarity * 0.4f)

                    if (totalSimilarity > highestSimilarity) {
                        highestSimilarity = totalSimilarity
                        bestMatch = MatchResult(cardFile, totalSimilarity)
                    }

                    chineseBitmap.recycle()
                    croppedChinese.recycle()
                    scaledChinese.recycle()

                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }

            croppedCaptured.recycle()
            scaledCaptured.recycle()

        } catch (e: IOException) {
            e.printStackTrace()
        }

        return bestMatch
    }

    // Crop to focus on the artwork area (middle portion of the card)
    private fun cropToArtwork(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Crop to center 60% width and top 50% height (where artwork usually is)
        val cropX = (width * 0.2).toInt()
        val cropY = (height * 0.1).toInt()
        val cropWidth = (width * 0.6).toInt()
        val cropHeight = (height * 0.4).toInt()

        return Bitmap.createBitmap(
            bitmap,
            cropX.coerceAtLeast(0),
            cropY.coerceAtLeast(0),
            cropWidth.coerceAtMost(width - cropX),
            cropHeight.coerceAtMost(height - cropY)
        )
    }

    // Compare color distributions
    private fun compareColorHistograms(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        val hist1 = calculateColorHistogram(bitmap1)
        val hist2 = calculateColorHistogram(bitmap2)

        var similarity = 0f
        for (i in hist1.indices) {
            similarity += minOf(hist1[i], hist2[i])
        }

        return similarity
    }

    private fun calculateColorHistogram(bitmap: Bitmap): FloatArray {
        val histogram = FloatArray(64) // 4x4x4 RGB bins
        val pixelCount = bitmap.width * bitmap.height

        for (x in 0 until bitmap.width) {
            for (y in 0 until bitmap.height) {
                val pixel = bitmap.getPixel(x, y)

                val r = (Color.red(pixel) / 64).coerceAtMost(3)
                val g = (Color.green(pixel) / 64).coerceAtMost(3)
                val b = (Color.blue(pixel) / 64).coerceAtMost(3)

                val binIndex = r * 16 + g * 4 + b
                histogram[binIndex]++
            }
        }

        // Normalise
        for (i in histogram.indices) {
            histogram[i] /= pixelCount
        }

        return histogram
    }

    // Compare image structure using edge detection
    private fun compareStructure(bitmap1: Bitmap, bitmap2: Bitmap): Float {
        if (bitmap1.width != bitmap2.width || bitmap1.height != bitmap2.height) {
            return 0f
        }

        var matchingPixels = 0
        var totalPixels = 0

        // Sample every 4th pixel for speed
        for (x in 0 until bitmap1.width step 4) {
            for (y in 0 until bitmap1.height step 4) {
                val edge1 = isEdgePixel(bitmap1, x, y)
                val edge2 = isEdgePixel(bitmap2, x, y)

                if (edge1 == edge2) {
                    matchingPixels++
                }
                totalPixels++
            }
        }

        return matchingPixels.toFloat() / totalPixels
    }

    private fun isEdgePixel(bitmap: Bitmap, x: Int, y: Int): Boolean {
        if (x == 0 || y == 0 || x >= bitmap.width - 1 || y >= bitmap.height - 1) {
            return false
        }

        val center = bitmap.getPixel(x, y)
        val threshold = 30

        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue

                val neighbor = bitmap.getPixel(x + dx, y + dy)
                if (colorDistance(center, neighbor) > threshold) {
                    return true
                }
            }
        }

        return false
    }

    private fun colorDistance(pixel1: Int, pixel2: Int): Float {
        val r1 = Color.red(pixel1)
        val g1 = Color.green(pixel1)
        val b1 = Color.blue(pixel1)

        val r2 = Color.red(pixel2)
        val g2 = Color.green(pixel2)
        val b2 = Color.blue(pixel2)

        return sqrt(
            (r1 - r2).toFloat().pow(2) +
                    (g1 - g2).toFloat().pow(2) +
                    (b1 - b2).toFloat().pow(2)
        )
    }
}