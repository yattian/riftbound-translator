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
import androidx.core.content.ContextCompat
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

    private var currentLanguage = CardConstants.LANGUAGE_ENGLISH
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

        numberInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                findCard()
                true
            } else {
                false
            }
        }

    }

    private fun setupSpinner() {
        val setAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            CardConstants.AVAILABLE_SETS
        )
        setSpinner.adapter = setAdapter
    }

    private fun setupLanguageButtons() {
        englishButton.setOnClickListener {
            selectLanguage(CardConstants.LANGUAGE_ENGLISH)
            // If a card is already displayed, reload it in the new language
            if (cardView.visibility == View.VISIBLE && lastLoadedCardId != null) {
                findCard()
            }
        }

        chineseButton.setOnClickListener {
            selectLanguage(CardConstants.LANGUAGE_CHINESE)
            // If a card is already displayed, reload it in the new language
            if (cardView.visibility == View.VISIBLE && lastLoadedCardId != null) {
                findCard()
            }
        }
    }

    private fun selectLanguage(language: String) {
        currentLanguage = language

        // Update button appearances
        if (language == CardConstants.LANGUAGE_ENGLISH) {
            englishButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
            )
            englishButton.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.white)
            )
            chineseButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.transparent)
            )
            chineseButton.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            )
        } else {
            chineseButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
            )
            chineseButton.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.white)
            )
            englishButton.setBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.transparent)
            )
            englishButton.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
            )
        }
    }

    private fun findCard() {
        // Hide error
        errorText.visibility = View.GONE

        // Get inputs
        val selectedSet = when (setSpinner.selectedItemPosition) {
            0 -> CardConstants.SET_ORIGINS
            1 -> CardConstants.SET_PROVING_GROUNDS
            2 -> CardConstants.SET_SFD
            else -> CardConstants.SET_ORIGINS
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

        val folderName = when (currentLanguage) {
            CardConstants.LANGUAGE_ENGLISH -> CardConstants.ENGLISH_CARDS_FOLDER
            CardConstants.LANGUAGE_CHINESE -> CardConstants.CHINESE_CARDS_FOLDER
            else -> CardConstants.ENGLISH_CARDS_FOLDER
        }

        // Try to load the card
        loadCard(folderName, cardId)
    }

    private fun loadCard(folderName: String, cardId: String) {
        try {
            val maxWidth = cardImage.width.takeIf { it > 0 } ?: 1080
            val maxHeight = cardImage.height.takeIf { it > 0 } ?: 1920

            val bitmap = BitmapCache.loadCard(
                requireContext(),
                folderName,
                cardId,
                maxWidth = maxWidth,
                maxHeight = maxHeight
            )

            if (bitmap != null) {
                cardImage.setImageBitmap(bitmap)
                cardView.visibility = View.VISIBLE
                hideKeyboard()
            } else {
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