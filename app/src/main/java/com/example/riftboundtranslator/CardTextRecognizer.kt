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
                // Look for card ID pattern in the text
                var cardId: String? = null

                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val text = line.text.uppercase()

                        // Look for patterns like "OGN 083/298" or "OGN 299*/298"
                        val patterns = listOf(
                            Regex("([A-Z]+)\\s*(\\d+)\\*?/\\d+"),  // Matches "OGN 083/298" or "OGN 299*/298"
                            Regex("([A-Z]+)[-\\s]*(\\d+)\\*?")      // Matches "OGN-083" or "OGN 083*"
                        )

                        for (pattern in patterns) {
                            val match = pattern.find(text)
                            if (match != null) {
                                val prefix = match.groupValues[1]
                                val number = match.groupValues[2].padStart(3, '0')
                                val suffix = if (text.contains("*")) "s" else ""

                                cardId = "$prefix-$number$suffix"
                                break
                            }
                        }

                        if (cardId != null) break
                    }
                    if (cardId != null) break
                }

                continuation.resume(cardId)
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    // Extract just the bottom portion of the image where card ID is located
    fun cropToBottomArea(bitmap: Bitmap): Bitmap {
        val height = bitmap.height
        val width = bitmap.width

        // Crop to bottom 20% of the card where ID is usually located
        val cropY = (height * 0.8).toInt()
        val cropHeight = (height * 0.2).toInt()

        return Bitmap.createBitmap(
            bitmap,
            0,
            cropY.coerceAtLeast(0),
            width,
            cropHeight.coerceAtMost(height - cropY)
        )
    }
}