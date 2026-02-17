package com.richtowndx.docscannerapp.ml

import android.graphics.Bitmap

/**
 * OCR Engine interface
 * Supported engines: ML_KIT (Google ML Kit - default, supports Chinese)
 *                    TESSERACT (Tesseract OCR - requires language data files)
 */
interface OCREngine {
    /**
     * Perform OCR on the given bitmap image
     * @param bitmap The image to perform OCR on
     * @return The recognized text
     */
    suspend fun recognizeText(bitmap: Bitmap): String
    
    /**
     * Get the engine name
     */
    fun getEngineName(): String
    
    /**
     * Check if the engine is ready to use
     */
    fun isReady(): Boolean
}

/**
 * OCR Engine types
 */
enum class OCREngineType {
    ML_KIT,      // Google ML Kit Text Recognition (default, supports Chinese)
    TESSERACT    // Tesseract OCR (requires language data files)
}
