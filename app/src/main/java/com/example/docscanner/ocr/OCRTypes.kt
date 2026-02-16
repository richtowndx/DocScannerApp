package com.example.docscanner.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * OCR引擎类型
 */
enum class OCREngine {
    ML_KIT,      // Google ML Kit Text Recognition
    TESSERACT,   // Tesseract OCR
    BAIDU,       // 百度OCR API
    GLM_OCR,     // 智谱AI GLM-OCR 云端API
    SILICONFLOW  // SiliconFlow 云端OCR API
}

/**
 * SiliconFlow 支持的 OCR 模型
 */
enum class SiliconFlowOCRModel(val modelId: String, val displayName: String) {
    PADDLE_OCR_VL_1_5("PaddlePaddle/PaddleOCR-VL-1.5", "PaddleOCR-VL-1.5"),
    PADDLE_OCR_VL("PaddlePaddle/PaddleOCR-VL", "PaddleOCR-VL"),
    DEEPSEEK_OCR("deepseek-ai/DeepSeek-OCR", "DeepSeek-OCR")
}

/**
 * OCR配置
 */
data class OCRConfig(
    val engine: OCREngine = OCREngine.ML_KIT,
    val language: String = "chi_sim+eng",  // 中文简体 + 英文
    val enableMarkdown: Boolean = true
)

/**
 * 识别到的文本块
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float = 0f,
    val recognizedLanguages: List<String> = emptyList(),
    val blockType: BlockType = BlockType.PARAGRAPH
)

/**
 * 文本块类型（用于Markdown格式化）
 */
enum class BlockType {
    TITLE,       // 标题 -> # heading
    SUBTITLE,    // 副标题 -> ## heading
    PARAGRAPH,   // 段落 -> plain text
    LIST_ITEM,   // 列表项 -> - item
    TABLE,       // 表格 -> | table |
    UNKNOWN
}

/**
 * OCR结果
 */
data class OCRResult(
    val textBlocks: List<TextBlock>,
    val fullText: String,
    val markdownText: String,
    val processingTimeMs: Long
)

/**
 * OCR回调
 */
interface OCRCallback {
    fun onSuccess(result: OCRResult)
    fun onError(error: Throwable)
    fun onProgress(progress: Float)
}
