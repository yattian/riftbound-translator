package com.example.riftboundtranslator

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CardTextRecognizer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun findCardId(bitmap: Bitmap): String? = suspendCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                var cardId: String? = null
                var bestConfidence = 0

                // Check all text blocks
                for (block in visionText.textBlocks) {
                    // Skip if block is on the right side (likely artist info)
                    if (block.boundingBox != null &&
                        block.boundingBox!!.left > bitmap.width * 0.6) {
                        continue
                    }

                    for (line in block.lines) {
                        val text = line.text.uppercase().trim()

                        // Skip if it contains artist/copyright indicators
                        if (text.contains("STUDIO") ||
                            text.contains("©") ||
                            text.contains("C20") ||
                            text.contains("ARTIST")) {
                            continue
                        }

                        // Look for card ID patterns
                        val patterns = listOf(
                            // Full patterns
                            Regex("(OG[NS])\\s*(\\d+)\\*?/\\d+"),  // OGN 083/298 or OGS 099*/298
                            Regex("(OG[NS])[-\\s]*(\\d+)\\*?"),     // OGN-083 or OGS 083*

                            // Partial patterns (missing first letter)
                            Regex("G([NS])\\s*(\\d+)\\*?/\\d+"),    // GN 083/298 (missing O)
                            Regex("G([NS])[-\\s]*(\\d+)\\*?"),      // GN-083 (missing O)

                            // Even more partial
                            Regex("([NS])\\s*(\\d+)\\*?/\\d+"),     // N 083/298 (missing OG)
                            Regex("([NS])[-\\s]*(\\d+)\\*?"),       // N-083 (missing OG)

                            // Just numbers with context
                            Regex("(\\d{3})\\*?/\\d{3}")            // 083/298
                        )

                        for ((index, pattern) in patterns.withIndex()) {
                            val match = pattern.find(text)
                            if (match != null) {
                                val confidence = 10 - index // Higher confidence for better patterns

                                if (confidence > bestConfidence) {
                                    cardId = when (index) {
                                        0, 1 -> {
                                            // Full pattern found
                                            val prefix = match.groupValues[1]
                                            val number = match.groupValues[2].padStart(3, '0')
                                            val suffix = if (text.contains("*")) "s" else ""
                                            "$prefix-$number$suffix"
                                        }
                                        2, 3 -> {
                                            // Missing O, but has GN or GS
                                            val setType = match.groupValues[1]
                                            val number = match.groupValues[2].padStart(3, '0')
                                            val suffix = if (text.contains("*")) "s" else ""
                                            "OG$setType-$number$suffix"
                                        }
                                        4, 5 -> {
                                            // Only has N or S, assume OGN/OGS based on set
                                            val setType = match.groupValues[1]
                                            val number = match.groupValues[2].padStart(3, '0')
                                            val suffix = if (text.contains("*")) "s" else ""
                                            "OG$setType-$number$suffix"
                                        }
                                        6 -> {
                                            // Just numbers, try to guess set
                                            val number = match.groupValues[1].padStart(3, '0')
                                            val suffix = if (text.contains("*")) "s" else ""
                                            // Default to OGN if we can't determine
                                            "OGN-$number$suffix"
                                        }
                                        else -> null
                                    }
                                    bestConfidence = confidence
                                }

                                if (confidence >= 9) break // Very confident, stop looking
                            }
                        }

                        if (bestConfidence >= 9) break
                    }
                    if (bestConfidence >= 9) break
                }

                continuation.resume(cardId)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    // Focus on bottom-left area where card ID is located
    fun cropToBottomArea(bitmap: Bitmap): Bitmap {
        val height = bitmap.height
        val width = bitmap.width

        // Crop to bottom-left 40% width and bottom 25% height
        val cropX = 0
        val cropY = (height * 0.75).toInt()
        val cropWidth = (width * 0.4).toInt()
        val cropHeight = (height * 0.25).toInt()

        return try {
            Bitmap.createBitmap(
                bitmap,
                cropX,
                cropY.coerceAtLeast(0),
                cropWidth.coerceAtMost(width),
                cropHeight.coerceAtMost(height - cropY)
            )
        } catch (e: Exception) {
            // If crop fails, return original
            bitmap
        }
    }
}