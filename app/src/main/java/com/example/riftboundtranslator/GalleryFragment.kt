package com.example.riftboundtranslator

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import java.io.IOException
import android.content.Context
import android.view.inputmethod.InputMethodManager

class GalleryFragment : Fragment() {

    private lateinit var setSpinner: Spinner
    private lateinit var numberInput: EditText
    private lateinit var languageSpinner: Spinner
    private lateinit var findButton: Button
    private lateinit var cardView: CardView
    private lateinit var cardImage: ImageView
    private lateinit var errorText: TextView

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
        languageSpinner = view.findViewById(R.id.language_spinner)
        findButton = view.findViewById(R.id.find_button)
        cardView = view.findViewById(R.id.card_view)
        cardImage = view.findViewById(R.id.card_image)
        errorText = view.findViewById(R.id.error_text)

        // Set up spinners
        setupSpinners()

        // Set up button click
        findButton.setOnClickListener {
            findCard()
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(numberInput.windowToken, 0)
    }

    private fun setupSpinners() {
        // Set spinner
        val sets = arrayOf("Origins (OGN)", "Proving Grounds (OGS)")
        val setAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, sets)
        setSpinner.adapter = setAdapter

        // Language spinner
        val languages = arrayOf("English", "Chinese")
        val languageAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, languages)
        languageSpinner.adapter = languageAdapter
    }

    private fun findCard() {
        // Hide previous results
        cardView.visibility = View.GONE
        errorText.visibility = View.GONE

        // Get inputs
        val selectedSet = when (setSpinner.selectedItemPosition) {
            0 -> "OGN"
            1 -> "OGS"
            else -> "OGN"
        }

        val numberText = numberInput.text.toString().trim()
        if (numberText.isEmpty()) {
            showError("Please enter a card number")
            return
        }

        // Handle special characters
        val processedNumber = if (numberText.endsWith("*")) {
            // Replace * with s (e.g., 299* becomes 299s)
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

        val language = when (languageSpinner.selectedItemPosition) {
            0 -> "english"
            1 -> "chinese"
            else -> "english"
        }

        // Construct filename
        val cardId = "$selectedSet-$finalNumber"
        val folderName = "${language}_cards"

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
}