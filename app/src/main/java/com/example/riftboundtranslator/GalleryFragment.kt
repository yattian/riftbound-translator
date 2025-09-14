package com.example.riftboundtranslator

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import java.io.IOException

class GalleryFragment : Fragment() {

    private lateinit var setSpinner: Spinner
    private lateinit var numberInput: EditText
    private lateinit var englishButton: Button
    private lateinit var chineseButton: Button
    private lateinit var cardView: CardView
    private lateinit var cardImage: ImageView
    private lateinit var errorText: TextView

    private var currentLanguage = "english"
    private var lastLoadedCardId: String? = null

    private lateinit var findButton: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize views
        setSpinner = view.findViewById(R.id.set_spinner)
        numberInput = view.findViewById(R.id.number_input)
        englishButton = view.findViewById(R.id.english_button)
        chineseButton = view.findViewById(R.id.chinese_button)
        cardView = view.findViewById(R.id.card_view)
        cardImage = view.findViewById(R.id.card_image)
        errorText = view.findViewById(R.id.error_text)

        findButton = view.findViewById(R.id.find_button)

        // Set up find button click
        findButton.setOnClickListener {
            findCard()
        }

        // Set up spinner
        setupSpinner()

        // Set up language buttons
        setupLanguageButtons()

        // Set English as default selected
        selectLanguage("english")
    }

    private fun setupSpinner() {
        val sets = arrayOf("Origins (OGN)", "Proving Grounds (OGS)")
        val setAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sets)
        setSpinner.adapter = setAdapter
    }

    private fun setupLanguageButtons() {
        englishButton.setOnClickListener {
            selectLanguage("english")
            // If a card is already displayed, reload it in the new language
            if (cardView.visibility == View.VISIBLE && lastLoadedCardId != null) {
                findCard()
            }
        }

        chineseButton.setOnClickListener {
            selectLanguage("chinese")
            // If a card is already displayed, reload it in the new language
            if (cardView.visibility == View.VISIBLE && lastLoadedCardId != null) {
                findCard()
            }
        }
    }

    private fun selectLanguage(language: String) {
        currentLanguage = language

        // Update button appearances
        if (language == "english") {
            englishButton.setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark, null))
            englishButton.setTextColor(resources.getColor(android.R.color.white, null))
            chineseButton.setBackgroundColor(resources.getColor(android.R.color.transparent, null))
            chineseButton.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        } else {
            chineseButton.setBackgroundColor(resources.getColor(android.R.color.holo_blue_dark, null))
            chineseButton.setTextColor(resources.getColor(android.R.color.white, null))
            englishButton.setBackgroundColor(resources.getColor(android.R.color.transparent, null))
            englishButton.setTextColor(resources.getColor(android.R.color.darker_gray, null))
        }
    }

    private fun findCard() {
        // Hide error
        errorText.visibility = View.GONE

        // Get inputs
        val selectedSet = when (setSpinner.selectedItemPosition) {
            0 -> "OGN"
            1 -> "OGS"
            else -> "OGN"
        }

        val numberText = numberInput.text.toString().trim()
        if (numberText.isEmpty()) {
            // Don't show error if just switching languages with no input
            if (lastLoadedCardId == null) {
                showError("Please enter a card number")
            }
            return
        }

        // Handle special characters
        val processedNumber = if (numberText.endsWith("*")) {
            numberText.dropLast(1) + "s"
        } else {
            numberText
        }

        // Extract number part and suffix part
        val numberPart = processedNumber.takeWhile { it.isDigit() }
        val suffixPart = processedNumber.dropWhile { it.isDigit() }

        // Pad the number part with zeros
        val paddedNumber = numberPart.padStart(3, '0')

        // Combine padded number with suffix
        val finalNumber = paddedNumber + suffixPart

        // Construct card ID
        val cardId = "$selectedSet-$finalNumber"
        lastLoadedCardId = cardId

        val folderName = "${currentLanguage}_cards"

        // Try to load the card
        loadCard(folderName, cardId)
    }

    private fun loadCard(folderName: String, cardId: String) {
        try {
            // Try different file extensions
            val extensions = listOf(".png", ".jpg", ".PNG", ".JPG")
            var loaded = false

            for (ext in extensions) {
                try {
                    val fileName = "$folderName/$cardId$ext"
                    val inputStream = requireContext().assets.open(fileName)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    cardImage.setImageBitmap(bitmap)
                    inputStream.close()

                    cardView.visibility = View.VISIBLE
                    loaded = true

                    // Hide keyboard when card is found
                    hideKeyboard()

                    break
                } catch (e: IOException) {
                    // Try next extension
                }
            }

            if (!loaded) {
                showError("Card not found: $cardId")
                cardView.visibility = View.GONE
            }

        } catch (e: Exception) {
            e.printStackTrace()
            showError("Error loading card")
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(numberInput.windowToken, 0)
    }
}