package com.example.guidelensapp.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

class TextRecognitionManager(context: Context) {
    private val TAG = "TextRecognitionManager"
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            val text = result.text.trim()
            
            if (text.isEmpty()) {
                Log.d(TAG, "OCR completed but no text found")
                return ""
            }
            
            Log.d(TAG, "OCR Success: ${text.take(50)}...")
            return text
        } catch (e: Exception) {
            Log.e(TAG, "OCR Failed: ${e.message}")
            throw e
        }
    }
    
    fun close() {
        recognizer.close()
    }
}
