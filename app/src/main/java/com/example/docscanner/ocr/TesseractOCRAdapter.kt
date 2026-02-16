package com.example.docscanner.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tesseract OCR 适配器
 * 开源OCR引擎，支持多语言
 *
 * 注意：需要添加依赖：
 * implementation("cz.adaptech.tesseract4android:tesseract4android:4.1.0")
 *
 * 并下载语言数据文件到：
 * /storage/emulated/0/tesseract/tessdata/
 */
class TesseractOCRAdapter : OCRAdapter {

    companion object {
        private const val TAG = "TesseractOCR"
        private const val TESS_DATA_PATH = "/storage/emulated/0/tesseract/"
    }

    override val engineType: OCREngine = OCREngine.TESSERACT

    // private var tesseract: TessBaseAPI? = null

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查Tesseract是否可用
            // 实际使用时取消注释
            /*
            val tessDataDir = File(TESS_DATA_PATH + "tessdata")
            tessDataDir.exists() && tessDataDir.isDirectory
            */
            false  // 默认不可用，需要用户配置
        } catch (e: Exception) {
            Log.e(TAG, "Tesseract not available: ${e.message}")
            false
        }
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            /*
            tesseract = TessBaseAPI()
            tesseract!!.init(TESS_DATA_PATH, "chi_sim+eng")
            */
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Tesseract: ${e.message}")
            false
        }
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig
    ): OCRResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        // Tesseract 实际调用代码（需要添加依赖后取消注释）
        /*
        if (tesseract == null) {
            initialize()
        }

        tesseract!!.setImage(bitmap)
        val fullText = tesseract!!.utf8Text

        // 获取单词级别的识别结果
        val textBlocks = mutableListOf<TextBlock>()
        val words = tesseract!!.words
        for (word in words) {
            textBlocks.add(
                TextBlock(
                    text = word.utf8Text,
                    boundingBox = word.boundingBox,
                    confidence = word.confidence / 100f,
                    blockType = BlockType.PARAGRAPH
                )
            )
        }
        */

        // 备用：使用ML Kit
        val mlKitAdapter = MLKitOCRAdapter()
        mlKitAdapter.initialize()
        val result = mlKitAdapter.recognizeText(bitmap, config)

        val processingTime = System.currentTimeMillis() - startTime

        OCRResult(
            textBlocks = result.textBlocks,
            fullText = result.fullText,
            markdownText = if (config.enableMarkdown) result.markdownText else result.fullText,
            processingTimeMs = processingTime
        )
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig,
        callback: OCRCallback
    ) {
        try {
            callback.onProgress(0f)
            val result = recognizeText(bitmap, config)
            callback.onProgress(1f)
            callback.onSuccess(result)
        } catch (e: Exception) {
            callback.onError(e)
        }
    }
}
