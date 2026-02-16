package com.example.docscanner.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * ML Kit Text Recognition 适配器
 * 支持中英文识别，完全离线
 */
class MLKitOCRAdapter : OCRAdapter {

    companion object {
        private const val TAG = "MLKitOCR"
    }

    override val engineType: OCREngine = OCREngine.ML_KIT

    private var recognizer: TextRecognizer? = null

    override suspend fun isAvailable(): Boolean {
        return try {
            true  // ML Kit 总是可用
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun initialize(): Boolean {
        return try {
            // 使用中文文本识别器（同时支持英文）
            recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            true
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_MLKIT, "Failed to initialize ML Kit OCR: ${e.message}")
            false
        }
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig
    ): OCRResult {
        val startTime = System.currentTimeMillis()

        if (recognizer == null) {
            initialize()
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer!!.process(image).await()

        val textBlocks = mutableListOf<TextBlock>()

        // 解析识别结果
        for (block in visionText.textBlocks) {
            val blockText = block.text
            val boundingBox = block.boundingBox ?: Rect()

            val blockType = determineBlockType(blockText, boundingBox)

            textBlocks.add(
                TextBlock(
                    text = blockText,
                    boundingBox = boundingBox,
                    confidence = 1.0f,  // ML Kit 不提供置信度
                    blockType = blockType
                )
            )
        }

        val processingTime = System.currentTimeMillis() - startTime
        val fullText = visionText.text
        val markdownText = if (config.enableMarkdown) {
            convertToMarkdown(textBlocks)
        } else {
            fullText
        }

        return OCRResult(
            textBlocks = textBlocks,
            fullText = fullText,
            markdownText = markdownText,
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

    /**
     * 判断文本块类型
     */
    private fun determineBlockType(text: String, boundingBox: Rect): BlockType {
        val trimmedText = text.trim()

        // 检测标题（通常较短，字体较大）
        if (trimmedText.length < 50 && boundingBox.height() > boundingBox.width() * 0.1) {
            // 可能是标题
            if (trimmedText.all { it.isUpperCase() || it.isDigit() || it.isWhitespace() }) {
                return BlockType.TITLE
            }
        }

        // 检测列表项
        if (trimmedText.startsWith("•") ||
            trimmedText.startsWith("-") ||
            trimmedText.startsWith("*") ||
            trimmedText.matches(Regex("^\\d+\\..*"))
        ) {
            return BlockType.LIST_ITEM
        }

        // 检测表格（包含多个 | 或制表符）
        if (trimmedText.count { it == '|' } >= 2) {
            return BlockType.TABLE
        }

        return BlockType.PARAGRAPH
    }

    /**
     * 将识别结果转换为Markdown格式
     */
    private fun convertToMarkdown(textBlocks: List<TextBlock>): String {
        val markdown = StringBuilder()

        for (block in textBlocks) {
            val text = block.text.trim()

            when (block.blockType) {
                BlockType.TITLE -> {
                    markdown.append("# $text\n\n")
                }
                BlockType.SUBTITLE -> {
                    markdown.append("## $text\n\n")
                }
                BlockType.LIST_ITEM -> {
                    // 格式化列表项
                    val formattedText = text
                        .replace(Regex("^[•*]\\s*"), "- ")
                        .replace(Regex("^(\\d+)\\."), "$1.")
                    markdown.append("$formattedText\n")
                }
                BlockType.TABLE -> {
                    // 格式化表格
                    markdown.append("$text\n")
                }
                BlockType.PARAGRAPH -> {
                    markdown.append("$text\n\n")
                }
                BlockType.UNKNOWN -> {
                    markdown.append("$text\n\n")
                }
            }
        }

        return markdown.toString().trim()
    }
}
