package com.example.docscanner.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Tesseract OCR 适配器
 * 开源OCR引擎，支持多语言，完全离线
 *
 * 特点:
 * - 语言数据内置在APK中，首次使用自动释放
 * - 支持中文简体和英文
 * - 完全离线运行
 */
class TesseractOCRAdapter(private val context: Context) : OCRAdapter {

    companion object {
        private const val TAG = "TesseractOCR"
        private const val TESS_SUBDIR = "tesseract"
        private const val TESS_DATA_SUBDIR = "tessdata"
        private const val DEFAULT_LANGUAGE = "chi_sim+eng"

        // 内置的语言数据文件
        private val BUILTIN_LANGUAGES = listOf("chi_sim", "eng")

        // 支持的语言代码映射
        val SUPPORTED_LANGUAGES = mapOf(
            "chi_sim" to "中文简体",
            "chi_tra" to "中文繁体",
            "eng" to "英文",
            "jpn" to "日文",
            "kor" to "韩文"
        )
    }

    override val engineType: OCREngine = OCREngine.TESSERACT

    private var tesseract: TessBaseAPI? = null
    private var isInitialized = false
    private var currentLanguage: String = DEFAULT_LANGUAGE

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 首先尝试从assets释放语言数据
            extractLanguageDataIfNeeded()

            val tessDataDir = getTessDataDir()
            val exists = tessDataDir.exists() && tessDataDir.isDirectory
            val hasLanguageData = checkLanguageDataAvailable(tessDataDir)
            AppLog.i(LogTag.OCR_TESSERACT, "Tesseract available: $exists, hasLanguageData: $hasLanguageData")
            hasLanguageData
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_TESSERACT, "Tesseract not available: ${e.message}")
            false
        }
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized && tesseract != null) {
                return@withContext true
            }

            // 首先确保语言数据已释放
            extractLanguageDataIfNeeded()

            val tessDataPath = getTessDataPath()
            val tessDataDir = File(tessDataPath)

            // 检查并创建目录
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
                AppLog.i(LogTag.OCR_TESSERACT, "Created tesseract directory: $tessDataPath")
            }

            // 确定要使用的语言
            val availableLanguages = getAvailableLanguages()
            if (availableLanguages.isEmpty()) {
                AppLog.e(LogTag.OCR_TESSERACT, "No language data available")
                return@withContext false
            }

            // 优先使用中英文，如果没有则使用第一个可用的语言
            currentLanguage = when {
                availableLanguages.contains("chi_sim") && availableLanguages.contains("eng") -> "chi_sim+eng"
                availableLanguages.contains("chi_sim") -> "chi_sim"
                availableLanguages.contains("eng") -> "eng"
                else -> availableLanguages.first()
            }

            AppLog.i(LogTag.OCR_TESSERACT, "Initializing Tesseract with language: $currentLanguage")

            tesseract = TessBaseAPI()
            val success = tesseract!!.init(tessDataPath, currentLanguage)

            if (success) {
                // 配置Tesseract参数
                tesseract!!.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)

                isInitialized = true
                AppLog.i(LogTag.OCR_TESSERACT, "Tesseract initialized successfully")
            }

            success
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_TESSERACT, "Failed to initialize Tesseract: ${e.message}", e)
            false
        }
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig
    ): OCRResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()

        try {
            if (!isInitialized || tesseract == null) {
                val initSuccess = initialize()
                if (!initSuccess) {
                    AppLog.e(LogTag.OCR_TESSERACT, "Tesseract not initialized, falling back to ML Kit")
                    return@withContext fallbackToMLKit(bitmap, config, startTime)
                }
            }

            // 预处理图像
            val processedBitmap = preprocessImage(bitmap)

            // 设置图像并识别
            tesseract!!.setImage(processedBitmap)
            val fullText = tesseract!!.utF8Text ?: ""

            // 获取识别结果详情
            val textBlocks = mutableListOf<TextBlock>()

            // 解析完整文本为块
            val lines = fullText.split("\n")
            for ((index, line) in lines.withIndex()) {
                if (line.isNotBlank()) {
                    textBlocks.add(
                        TextBlock(
                            text = line,
                            boundingBox = Rect(0, index * 20, 100, (index + 1) * 20),
                            confidence = 0.9f,
                            blockType = BlockType.PARAGRAPH
                        )
                    )
                }
            }

            val processingTime = System.currentTimeMillis() - startTime
            AppLog.i(LogTag.OCR_TESSERACT, "OCR completed in ${processingTime}ms, found ${textBlocks.size} blocks")

            OCRResult(
                textBlocks = textBlocks,
                fullText = fullText,
                markdownText = if (config.enableMarkdown) formatAsMarkdown(fullText) else fullText,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_TESSERACT, "OCR failed: ${e.message}", e)
            fallbackToMLKit(bitmap, config, startTime)
        }
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
     * 释放资源
     */
    fun release() {
        try {
            tesseract?.stop()
            tesseract = null
            isInitialized = false
            AppLog.i(LogTag.OCR_TESSERACT, "Tesseract released")
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_TESSERACT, "Error releasing Tesseract: ${e.message}")
        }
    }

    /**
     * 从assets释放语言数据到应用私有目录
     */
    private fun extractLanguageDataIfNeeded() {
        try {
            val tessDataDir = getTessDataDir()

            // 创建目录
            if (!tessDataDir.exists()) {
                tessDataDir.mkdirs()
                AppLog.i(LogTag.OCR_TESSERACT, "Created tessdata directory: ${tessDataDir.absolutePath}")
            }

            // 检查并释放每个语言文件
            for (lang in BUILTIN_LANGUAGES) {
                val targetFile = File(tessDataDir, "$lang.traineddata")

                if (!targetFile.exists()) {
                    AppLog.i(LogTag.OCR_TESSERACT, "Extracting language data: $lang")
                    extractFileFromAssets("tessdata/$lang.traineddata", targetFile)
                } else {
                    AppLog.d(LogTag.OCR_TESSERACT, "Language data already exists: $lang")
                }
            }

            AppLog.i(LogTag.OCR_TESSERACT, "Language data extraction completed")
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_TESSERACT, "Failed to extract language data: ${e.message}", e)
        }
    }

    /**
     * 从assets释放单个文件
     */
    private fun extractFileFromAssets(assetPath: String, targetFile: File) {
        try {
            val inputStream = context.assets.open(assetPath)
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            AppLog.i(LogTag.OCR_TESSERACT, "Extracted: ${targetFile.absolutePath} (${targetFile.length() / 1024 / 1024}MB)")
        } catch (e: IOException) {
            AppLog.e(LogTag.OCR_TESSERACT, "Failed to extract $assetPath: ${e.message}")
            throw e
        }
    }

    /**
     * 获取TessData路径
     */
    private fun getTessDataPath(): String {
        // 使用应用外部存储目录
        val dir = context.getExternalFilesDir(null)
        return File(dir, TESS_SUBDIR).absolutePath
    }

    /**
     * 获取TessData目录
     */
    private fun getTessDataDir(): File {
        return File(getTessDataPath(), TESS_DATA_SUBDIR)
    }

    /**
     * 检查是否有可用的语言数据
     */
    private fun checkLanguageDataAvailable(tessDataDir: File): Boolean {
        if (!tessDataDir.exists() || !tessDataDir.isDirectory) {
            return false
        }

        val files = tessDataDir.listFiles { file ->
            file.name.endsWith(".traineddata")
        }

        return files != null && files.isNotEmpty()
    }

    /**
     * 获取可用的语言列表
     */
    private fun getAvailableLanguages(): List<String> {
        val tessDataDir = getTessDataDir()
        if (!tessDataDir.exists()) return emptyList()

        val files = tessDataDir.listFiles { file ->
            file.name.endsWith(".traineddata")
        } ?: return emptyList()

        return files.map { it.name.replace(".traineddata", "") }
    }

    /**
     * 图像预处理（优化识别效果）
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // 如果图片太大，进行缩放以提高识别速度
        val maxSize = 2048
        val width = bitmap.width
        val height = bitmap.height

        if (width > maxSize || height > maxSize) {
            val scale = maxSize.toFloat() / maxOf(width, height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }

        return bitmap
    }

    /**
     * 格式化为Markdown
     */
    private fun formatAsMarkdown(fullText: String): String {
        val lines = fullText.split("\n")
        val markdown = StringBuilder()

        for (line in lines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                markdown.append("\n")
                continue
            }

            // 简单的格式检测
            when {
                // 检测可能的标题（短行且不含标点）
                trimmedLine.length < 30 && !trimmedLine.contains(Regex("[。，、；：]")) -> {
                    markdown.append("## $trimmedLine\n\n")
                }
                // 检测列表项
                trimmedLine.startsWith("•") || trimmedLine.startsWith("-") || trimmedLine.startsWith("*") -> {
                    markdown.append("$trimmedLine\n")
                }
                // 检测编号列表
                trimmedLine.matches(Regex("^\\d+[.、].*")) -> {
                    markdown.append("$trimmedLine\n")
                }
                // 普通段落
                else -> {
                    markdown.append("$trimmedLine\n\n")
                }
            }
        }

        return markdown.toString().trim()
    }

    /**
     * 回退到ML Kit
     */
    private suspend fun fallbackToMLKit(
        bitmap: Bitmap,
        config: OCRConfig,
        startTime: Long
    ): OCRResult {
        val mlKitAdapter = MLKitOCRAdapter()
        mlKitAdapter.initialize()
        val result = mlKitAdapter.recognizeText(bitmap, config)
        val processingTime = System.currentTimeMillis() - startTime

        return OCRResult(
            textBlocks = result.textBlocks,
            fullText = result.fullText,
            markdownText = result.markdownText,
            processingTimeMs = processingTime
        )
    }
}
