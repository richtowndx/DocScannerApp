package com.richtowndx.docscannerapp.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.richtowndx.docscannerapp.ml.MLKitOCREngine
import com.richtowndx.docscannerapp.ml.OCREngineType
import com.richtowndx.docscannerapp.ml.TesseractOCREngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for OCR functionality
 */
@HiltViewModel
class OCRViewModel @Inject constructor(
    private val mlKitEngine: MLKitOCREngine,
    private val tesseractEngine: TesseractOCREngine
) : ViewModel() {
    
    private val _ocrText = MutableStateFlow("")
    val ocrText: StateFlow<String> = _ocrText
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing
    
    private val _selectedEngine = MutableStateFlow(OCREngineType.ML_KIT)
    val selectedEngine: StateFlow<OCREngineType> = _selectedEngine
    
    fun selectEngine(engineType: OCREngineType) {
        _selectedEngine.value = engineType
    }
    
    fun performOCR(bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessing.value = true
            
            val engine = when (_selectedEngine.value) {
                OCREngineType.ML_KIT -> mlKitEngine
                OCREngineType.TESSERACT -> tesseractEngine
            }
            
            val result = engine.recognizeText(bitmap)
            _ocrText.value = result
            _isProcessing.value = false
        }
    }
    
    fun clearOCRText() {
        _ocrText.value = ""
    }
}
