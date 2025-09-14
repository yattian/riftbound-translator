package com.example.riftboundtranslator

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
private lateinit var cardTextRecognizer: CardTextRecognizer
class TranslateFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var progressBar: ProgressBar
    private var imageCapture: ImageCapture? = null
    private lateinit var imageMatcher: ImageMatcher

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
        cardTextRecognizer = CardTextRecognizer()
        previewView = view.findViewById(R.id.camera_preview)
        captureButton = view.findViewById(R.id.capture_button)
        progressBar = view.findViewById(R.id.progress_bar)

        imageMatcher = ImageMatcher(requireContext())

        captureButton.setOnClickListener {
            captureAndMatch()
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
        captureButton.text = "Processing..."

        // Capture image to memory
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()

                    // Show captured image briefly (freeze effect)
                    showCapturedPreview(bitmap)

                    // Process image in background
                    lifecycleScope.launch {
                        processImage(bitmap)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    progressBar.visibility = View.GONE
                    captureButton.isEnabled = true
                    captureButton.text = "Capture"
                    Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showCapturedPreview(bitmap: Bitmap) {
        // Create an ImageView to show the captured image
        val imageView = ImageView(context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // Add it temporarily over the camera preview
        (view as ViewGroup).addView(imageView, ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        // Remove after processing
        lifecycleScope.launch {
            kotlinx.coroutines.delay(2000) // Show for 2 seconds
            withContext(Dispatchers.Main) {
                (view as ViewGroup).removeView(imageView)
            }
        }
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
            captureButton.text = "Capture"

            if (detectedCardId != null) {
                // Try to find matching English card
                findAndShowEnglishCard(detectedCardId)
            } else {
                Toast.makeText(
                    context,
                    "Couldn't read card ID. Make sure:\n• Bottom of card is visible\n• Good lighting\n• Clear focus",
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

    private fun showResultDialog(matchedFileName: String, confidence: String) {
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
            .setTitle(confidence)
            .setView(dialogView)
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}