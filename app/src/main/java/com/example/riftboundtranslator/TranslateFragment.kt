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
import android.widget.TextView
import android.widget.Toast
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

        cardTextRecognizer = CardTextRecognizer()

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
        val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

        val rotationDegrees = image.imageInfo.rotationDegrees
        if (rotationDegrees == 0) {
            return originalBitmap  // No rotation needed
        }

        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())

        val rotatedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0,
            originalBitmap.width, originalBitmap.height,
            matrix, true
        )

        // CRITICAL: Recycle original if new bitmap was created
        if (rotatedBitmap != originalBitmap) {
            originalBitmap.recycle()
        }

        return rotatedBitmap
    }

    private suspend fun processImage(bitmap: Bitmap) {
        var croppedBitmap: Bitmap? = null
        try {
            // Try to read card ID from the image
            croppedBitmap = cardTextRecognizer.cropToBottomArea(bitmap)
            val detectedCardId = withContext(Dispatchers.IO) {
                cardTextRecognizer.findCardId(croppedBitmap)
            }

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                captureButton.isEnabled = true

                if (detectedCardId != null) {
                    // Show matching English card directly
                    findAndShowEnglishCard(detectedCardId)
                } else {
                    // Show error message if no card ID detected
                    Toast.makeText(
                        context,
                        "No card ID found.\nTry better lighting or use the Gallery tab",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } finally {
            // CRITICAL: Always recycle both bitmaps
            croppedBitmap?.recycle()
            bitmap.recycle()
        }
    }

    private fun findAndShowEnglishCard(cardId: String) {
        try {
            val fileName = CardAssetIndex.findCard(cardId, CardConstants.LANGUAGE_ENGLISH)

            if (fileName != null) {
                showResultDialog(fileName, "Card found: $cardId")
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
        // Hide camera preview while viewing card result
        previewView.visibility = View.GONE

        val dialogView = layoutInflater.inflate(R.layout.dialog_card_result, null)
        val resultImage = dialogView.findViewById<ImageView>(R.id.result_image)
        val closeButton = dialogView.findViewById<Button>(R.id.close_button)

        try {
            val cardId = matchedFileName.substringBeforeLast(".")
            val bitmap = BitmapCache.loadCard(
                requireContext(),
                CardConstants.ENGLISH_CARDS_FOLDER,
                cardId,
                maxWidth = 1080,
                maxHeight = 1920
            )

            if (bitmap != null) {
                resultImage.setImageBitmap(bitmap)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setOnDismissListener {
                resultImage.setImageBitmap(null)  // Cache manages lifecycle
                // Restore camera preview when dialog closes
                previewView.visibility = View.VISIBLE
            }
            .create()

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

}