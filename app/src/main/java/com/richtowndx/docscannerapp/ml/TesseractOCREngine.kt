package com.richtowndx.docscannerapp.ml

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tesseract OCR Engine implementation
 * Requires language data files to be downloaded separately
 */
@Singleton
class TesseractOCREngine @Inject constructor(
    @ApplicationContext private val context: Context
) : OCREngine {
    
    private var tessBaseAPI: TessBaseAPI? = null
    private var isInitialized = false
    
    companion object {
        private const val TESSDATA_DIR = "tessdata"
        private const val LANGUAGE = "eng+chi_sim" // English + Simplified Chinese
    }
    
    init {
        initializeTesseract()
    }
    
    private fun initializeTesseract() {
        try {
            val dataPath = context.filesDir.absolutePath
            val tessDataPath = File(dataPath, TESSDATA_DIR)
            
            if (!tessDataPath.exists()) {
                tessDataPath.mkdirs()
            }
            
            // Check if language files exist
            val langFiles = tessDataPath.listFiles { file -> file.name.endsWith(".traineddata") }
            if (langFiles.isNullOrEmpty()) {
                // Language files not available, cannot initialize
                isInitialized = false
                return
            }
            
            tessBaseAPI = TessBaseAPI()
            isInitialized = tessBaseAPI?.init(dataPath, LANGUAGE) == true
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
        }
    }
    
    override suspend fun recognizeText(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        if (!isInitialized || tessBaseAPI == null) {
            return@withContext "Tesseract not initialized. Please download language data files."
        }
        
        try {
            tessBaseAPI?.setImage(bitmap)
            tessBaseAPI?.utF8Text ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    override fun getEngineName(): String = "Tesseract"
    
    override fun isReady(): Boolean = isInitialized
    
    fun cleanup() {
        tessBaseAPI?.end()
        tessBaseAPI = null
    }
}
