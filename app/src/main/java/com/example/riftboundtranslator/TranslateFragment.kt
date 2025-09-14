package com.example.riftboundtranslator

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class TranslateFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var detectedTextView: TextView
    private lateinit var manualEntryButton: Button
    private var imageCapture: ImageCapture? = null
    private lateinit var cardTextRecognizer: CardTextRecognizer

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_translate, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.camera_preview)
        captureButton = view.findViewById(R.id.capture_button)
        progressBar = view.findViewById(R.id.progress_bar)
        detectedTextView = view.findViewById(R.id.detected_text)
        manualEntryButton = view.findViewById(R.id.manual_entry_button)

        cardTextRecognizer = CardTextRecognizer()

        captureButton.setOnClickListener {
            captureAndMatch()
        }

        manualEntryButton.setOnClickListener {
            showManualEntryDialog()
        }

        checkCameraPermission()
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Image capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun captureAndMatch() {
        val imageCapture = imageCapture ?: return

        progressBar.visibility = View.VISIBLE
        captureButton.isEnabled = false

        // Capture image to memory
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()

                    // Process image in background
                    lifecycleScope.launch {
                        processImage(bitmap)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    progressBar.visibility = View.GONE
                    captureButton.isEnabled = true
                    Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        // Rotate if needed
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private suspend fun processImage(bitmap: Bitmap) {
        // Try to read card ID from the image
        val croppedBitmap = cardTextRecognizer.cropToBottomArea(bitmap)
        val detectedCardId = withContext(Dispatchers.IO) {
            cardTextRecognizer.findCardId(croppedBitmap)
        }

        withContext(Dispatchers.Main) {
            progressBar.visibility = View.GONE
            captureButton.isEnabled = true

            if (detectedCardId != null) {
                detectedTextView.text = "Found: $detectedCardId"
                detectedTextView.visibility = View.VISIBLE

                // Try to find matching English card
                findAndShowEnglishCard(detectedCardId)

                // Hide detected text after 3 seconds
                lifecycleScope.launch {
                    delay(3000)
                    detectedTextView.visibility = View.GONE
                }
            } else {
                // Shorter message that won't cut off
                Toast.makeText(
                    context,
                    "No card ID found.\nTry better lighting or Manual Entry",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun findAndShowEnglishCard(cardId: String) {
        try {
            // List all English cards and find match
            val englishCards = requireContext().assets.list("english_cards") ?: return

            // Try different filename formats
            val possibleNames = listOf(
                "$cardId.png",
                "$cardId.jpg",
                "${cardId.uppercase()}.png",
                "${cardId.uppercase()}.jpg",
                "${cardId.lowercase()}.png",
                "${cardId.lowercase()}.jpg"
            )

            var matchedFile: String? = null
            for (fileName in englishCards) {
                // Check if this file matches our card ID
                val fileWithoutExtension = fileName.substringBeforeLast(".")
                if (fileWithoutExtension.equals(cardId, ignoreCase = true) ||
                    possibleNames.any { it.equals(fileName, ignoreCase = true) }) {
                    matchedFile = fileName
                    break
                }
            }

            if (matchedFile != null) {
                showResultDialog(matchedFile, "Card found: $cardId")
            } else {
                Toast.makeText(
                    context,
                    "Card ID detected: $cardId\nBut no matching English card found",
                    Toast.LENGTH_LONG
                ).show()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error finding card", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showResultDialog(matchedFileName: String, title: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_card_result, null)
        val resultImage = dialogView.findViewById<ImageView>(R.id.result_image)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)

        try {
            // Load English version
            val inputStream = requireContext().assets.open("english_cards/$matchedFileName")
            val bitmap = BitmapFactory.decodeStream(inputStream)
            resultImage.setImageBitmap(bitmap)
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showManualEntryDialog() {
        // Create a container layout with padding
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 16) // Left, Top, Right, Bottom padding
        }

        val input = EditText(context).apply {
            hint = "Enter card ID (e.g. OGN-083)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setSingleLine(true)
        }

        container.addView(input)

        AlertDialog.Builder(requireContext())
            .setTitle("Manual Card Entry")
            .setMessage("Enter the card ID from the bottom left of the card")
            .setView(container) // Use container instead of input directly
            .setPositiveButton("Find") { _, _ ->
                val cardId = input.text.toString().trim().uppercase()
                if (cardId.isNotEmpty()) {
                    findAndShowEnglishCard(cardId)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()

        // Show keyboard automatically
        input.requestFocus()
    }
}