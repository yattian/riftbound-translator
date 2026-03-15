package com.example.riftboundtranslator

import android.content.Context
import java.io.IOException

object CardAssetIndex {
    private var englishIndex: Map<String, String>? = null
    private var chineseIndex: Map<String, String>? = null

    fun initialize(context: Context) {
        if (englishIndex == null) {
            englishIndex = buildIndex(context, CardConstants.ENGLISH_CARDS_FOLDER)
        }
        if (chineseIndex == null) {
            chineseIndex = buildIndex(context, CardConstants.CHINESE_CARDS_FOLDER)
        }
    }

    private fun buildIndex(context: Context, folderName: String): Map<String, String> {
        val index = mutableMapOf<String, String>()

        try {
            val files = context.assets.list(folderName) ?: emptyArray()

            for (fileName in files) {
                val cardId = fileName.substringBeforeLast(".")
                index[cardId.uppercase()] = fileName
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        return index
    }

    fun findCard(cardId: String, language: String): String? {
        val index = when (language) {
            CardConstants.LANGUAGE_ENGLISH -> englishIndex
            CardConstants.LANGUAGE_CHINESE -> chineseIndex
            else -> null
        } ?: return null

        return index[cardId.uppercase()]
    }
}
