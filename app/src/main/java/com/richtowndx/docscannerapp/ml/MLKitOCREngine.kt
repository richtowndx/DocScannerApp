package com.richtowndx.docscannerapp.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit OCR Engine implementation
 * Supports Chinese and Latin text recognition
 */
@Singleton
class MLKitOCREngine @Inject constructor() : OCREngine {
    
    private val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    
    override suspend fun recognizeText(bitmap: Bitmap): String {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = recognizer.process(image).await()
            result.text
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    override fun getEngineName(): String = "ML Kit"
    
    override fun isReady(): Boolean = true
}
