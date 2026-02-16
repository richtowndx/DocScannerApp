package com.example.docscanner.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * 智谱AI GLM-OCR 适配器
 * 云端高精度 OCR 识别，需要网络连接和 API Key 配置
 *
 * 使用说明:
 * 1. 在智谱AI开放平台注册账号: https://open.bigmodel.cn
 * 2. 获取 API Key
 * 3. 在设置中配置 API Key
 *
 * API文档: https://docs.bigmodel.cn/cn/guide/models/vlm/glm-ocr
 */
class GLMOCRAdapter : OCRAdapter {

    companion object {
        private const val TAG = "GLMOCR"

        // API 端点
        private const val API_URL = "https://open.bigmodel.cn/api/paas/v4/layout_parsing"
        private const val MODEL = "glm-ocr"

        // API Key 配置（需要在设置中配置）
        var apiKey: String? = null
    }

    override val engineType: OCREngine = OCREngine.GLM_OCR

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val hasConfig = !apiKey.isNullOrBlank()
        AppLog.i(LogTag.OCR_GLM, "GLM-OCR available: $hasConfig (API Key configured: ${!apiKey.isNullOrBlank()})")
        hasConfig
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNullOrBlank()) {
                AppLog.e(LogTag.OCR_GLM, "API Key not configured")
                return@withContext false
            }

            AppLog.i(LogTag.OCR_GLM, "GLM-OCR initialized successfully with API Key")
            true
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_GLM, "Failed to initialize GLM-OCR: ${e.message}", e)
            false
        }
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig
    ): OCRResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // 检查配置
            if (!isAvailable()) {
                AppLog.w(LogTag.OCR_GLM, "GLM-OCR not configured, falling back to ML Kit")
                return@withContext fallbackToMLKit(bitmap, config, startTime)
            }

            AppLog.i(LogTag.OCR_GLM, "Starting GLM-OCR recognition...")

            // 将图片转换为 Base64 Data URI
            val imageDataUri = bitmapToDataUri(bitmap)
            AppLog.d(LogTag.OCR_GLM, "Image converted to base64, size: ${imageDataUri.length} chars")

            // 构建请求 JSON
            val requestBody = JSONObject().apply {
                put("model", MODEL)
                put("file", imageDataUri)
            }

            AppLog.i(LogTag.OCR_GLM, "Sending request to GLM-OCR API...")

            // 发送请求
            val response = sendPostRequest(API_URL, requestBody.toString())

            // 详细记录完整响应（分段输出避免日志截断）
            AppLog.i(LogTag.OCR_GLM, "=== Full API Response ===")
            if (response.length > 2000) {
                var offset = 0
                while (offset < response.length) {
                    val end = minOf(offset + 2000, response.length)
                    AppLog.i(LogTag.OCR_GLM, "Response[$offset-$end]: ${response.substring(offset, end)}")
                    offset = end
                }
            } else {
                AppLog.i(LogTag.OCR_GLM, "Response: $response")
            }
            AppLog.i(LogTag.OCR_GLM, "=== End Response ===")

            // 解析响应
            val result = parseGLMResponse(response, config, startTime)

            val processingTime = System.currentTimeMillis() - startTime
            AppLog.i(LogTag.OCR_GLM, "GLM-OCR completed in ${processingTime}ms, found ${result.textBlocks.size} blocks")

            result.copy(processingTimeMs = processingTime)
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_GLM, "GLM-OCR failed: ${e.message}", e)
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
     * 将 Bitmap 转换为 Data URI (Base64)
     */
    private fun bitmapToDataUri(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()

        // 压缩图片以减少传输数据量
        val maxSize = 2048
        val scale = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        } else 1f

        val scaledBitmap = if (scale < 1f) {
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else bitmap

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val bytes = outputStream.toByteArray()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        AppLog.d(LogTag.OCR_GLM, "Image compressed: ${bitmap.width}x${bitmap.height} -> ${scaledBitmap.width}x${scaledBitmap.height}, base64 size: ${base64.length}")

        return "data:image/jpeg;base64,$base64"
    }

    /**
     * 发送 POST 请求
     */
    private fun sendPostRequest(urlString: String, jsonBody: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpsURLConnection

        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 60000
            connection.readTimeout = 60000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")

            AppLog.d(LogTag.OCR_GLM, "Request headers set, sending body...")

            // 发送请求体
            connection.outputStream.use { os ->
                val input = jsonBody.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            AppLog.i(LogTag.OCR_GLM, "Response code: $responseCode")

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                AppLog.e(LogTag.OCR_GLM, "HTTP Error $responseCode: $errorText")
                throw Exception("HTTP $responseCode: $errorText")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 解析 GLM-OCR 响应
     * 实际响应格式:
     * {
     *   "created": 1771243505,
     *   "data_info": {"num_pages": 1, "pages": [{"height": 2048, "width": 1398}]},
     *   "id": "...",
     *   "layout_details": [[{"bbox_2d": [...], "content": "...", "label": "..."}]]
     * }
     */
    private fun parseGLMResponse(
        response: String,
        config: OCRConfig,
        startTime: Long
    ): OCRResult {
        try {
            val json = JSONObject(response)
            AppLog.i(LogTag.OCR_GLM, "Parsing response, top-level keys: ${json.keys().asSequence().toList()}")

            // 检查错误
            if (json.has("error")) {
                val error = json.getJSONObject("error")
                val errorCode = error.optString("code", "unknown")
                val errorMsg = error.optString("message", "Unknown error")
                throw Exception("GLM-OCR Error ($errorCode): $errorMsg")
            }

            val textBlocks = mutableListOf<TextBlock>()
            val fullTextBuilder = StringBuilder()
            val markdownBuilder = StringBuilder()

            // 解析 layout_details 格式（实际API返回的格式）
            if (json.has("layout_details")) {
                AppLog.i(LogTag.OCR_GLM, "Found layout_details field, parsing...")
                val layoutDetails = json.getJSONArray("layout_details")
                AppLog.i(LogTag.OCR_GLM, "layout_details has ${layoutDetails.length()} pages")

                for (pageIndex in 0 until layoutDetails.length()) {
                    val pageBlocks = layoutDetails.getJSONArray(pageIndex)
                    AppLog.i(LogTag.OCR_GLM, "Page $pageIndex has ${pageBlocks.length()} blocks")

                    for (blockIndex in 0 until pageBlocks.length()) {
                        val block = pageBlocks.getJSONObject(blockIndex)
                        val content = block.optString("content", "")
                        val label = block.optString("label", "text")
                        val bbox = block.optJSONArray("bbox_2d")

                        AppLog.d(LogTag.OCR_GLM, "Block[$pageIndex][$blockIndex]: label=$label, content=${content.take(50)}...")

                        if (content.isNotBlank()) {
                            // 获取边界框
                            val boundingBox = if (bbox != null && bbox.length() >= 4) {
                                Rect(bbox.getInt(0), bbox.getInt(1), bbox.getInt(2), bbox.getInt(3))
                            } else {
                                Rect(0, blockIndex * 20, 100, (blockIndex + 1) * 20)
                            }

                            // 确定块类型
                            val blockType = when (label) {
                                "title", "heading" -> BlockType.TITLE
                                "subtitle" -> BlockType.SUBTITLE
                                "table" -> BlockType.TABLE
                                "list", "list_item" -> BlockType.LIST_ITEM
                                else -> BlockType.PARAGRAPH
                            }

                            textBlocks.add(
                                TextBlock(
                                    text = content,
                                    boundingBox = boundingBox,
                                    confidence = 1.0f,
                                    blockType = blockType
                                )
                            )

                            // 构建完整文本
                            fullTextBuilder.append(content).append("\n")

                            // 构建 Markdown
                            when (blockType) {
                                BlockType.TITLE -> markdownBuilder.append("# $content\n\n")
                                BlockType.SUBTITLE -> markdownBuilder.append("## $content\n\n")
                                BlockType.LIST_ITEM -> markdownBuilder.append("- $content\n")
                                else -> markdownBuilder.append("$content\n\n")
                            }
                        }
                    }
                }
            }

            // 兼容旧格式：data.layout
            if (textBlocks.isEmpty() && json.has("data")) {
                AppLog.i(LogTag.OCR_GLM, "Trying legacy data.layout format...")
                val data = json.getJSONObject("data")

                // 获取 Markdown 结果
                val markdownText = data.optString("markdown", "")

                // 解析 layout 获取文本块
                val layout = data.optJSONArray("layout")
                if (layout != null) {
                    for (i in 0 until layout.length()) {
                        val block = layout.getJSONObject(i)
                        val content = block.optString("content", "")
                        val label = block.optString("label", "text")

                        if (content.isNotBlank()) {
                            val bbox = block.optJSONArray("bbox_2d")
                            val boundingBox = if (bbox != null && bbox.length() >= 4) {
                                Rect(bbox.getInt(0), bbox.getInt(1), bbox.getInt(2), bbox.getInt(3))
                            } else {
                                Rect(0, i * 20, 100, (i + 1) * 20)
                            }

                            val blockType = when (label) {
                                "title" -> BlockType.TITLE
                                "table" -> BlockType.TABLE
                                "list" -> BlockType.LIST_ITEM
                                else -> BlockType.PARAGRAPH
                            }

                            textBlocks.add(
                                TextBlock(
                                    text = content,
                                    boundingBox = boundingBox,
                                    confidence = 1.0f,
                                    blockType = blockType
                                )
                            )
                        }
                    }
                }

                // 如果没有 layout，从 markdown 中提取
                if (textBlocks.isEmpty() && markdownText.isNotBlank()) {
                    val lines = markdownText.split("\n")
                    for ((index, line) in lines.withIndex()) {
                        if (line.isNotBlank()) {
                            textBlocks.add(
                                TextBlock(
                                    text = line,
                                    boundingBox = Rect(0, index * 20, 100, (index + 1) * 20),
                                    confidence = 1.0f,
                                    blockType = BlockType.PARAGRAPH
                                )
                            )
                        }
                    }
                }

                return OCRResult(
                    textBlocks = textBlocks,
                    fullText = markdownText.ifBlank { fullTextBuilder.toString() }.trim(),
                    markdownText = markdownText.ifBlank { markdownBuilder.toString() }.trim(),
                    processingTimeMs = System.currentTimeMillis() - startTime
                )
            }

            val processingTime = System.currentTimeMillis() - startTime
            val fullText = fullTextBuilder.toString().trim()
            val markdownText = markdownBuilder.toString().trim()

            AppLog.i(LogTag.OCR_GLM, "Parsed ${textBlocks.size} blocks, fullText length: ${fullText.length}")

            return OCRResult(
                textBlocks = textBlocks,
                fullText = fullText,
                markdownText = if (config.enableMarkdown) markdownText else fullText,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_GLM, "Failed to parse response: ${e.message}", e)
            throw e
        }
    }

    /**
     * 直接解析响应（备用方案）
     */
    private fun parseDirectResponse(json: JSONObject, config: OCRConfig, startTime: Long): OCRResult {
        // 尝试从不同字段获取结果
        val possibleFields = listOf("result", "text", "content", "output")
        var fullText = ""

        for (field in possibleFields) {
            if (json.has(field)) {
                fullText = json.optString(field, "")
                if (fullText.isNotBlank()) break
            }
        }

        // 如果是数组格式
        if (json.has("results")) {
            val results = json.getJSONArray("results")
            val sb = StringBuilder()
            for (i in 0 until results.length()) {
                sb.append(results.optString(i, "")).append("\n")
            }
            fullText = sb.toString()
        }

        val textBlocks = mutableListOf<TextBlock>()
        val lines = fullText.split("\n")
        for ((index, line) in lines.withIndex()) {
            if (line.isNotBlank()) {
                textBlocks.add(
                    TextBlock(
                        text = line,
                        boundingBox = Rect(0, index * 20, 100, (index + 1) * 20),
                        confidence = 1.0f,
                        blockType = BlockType.PARAGRAPH
                    )
                )
            }
        }

        return OCRResult(
            textBlocks = textBlocks,
            fullText = fullText.trim(),
            markdownText = if (config.enableMarkdown) fullText.trim() else fullText.trim(),
            processingTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * 回退到 ML Kit
     */
    private suspend fun fallbackToMLKit(
        bitmap: Bitmap,
        config: OCRConfig,
        startTime: Long
    ): OCRResult {
        AppLog.i(LogTag.OCR_GLM, "Falling back to ML Kit")
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

    /**
     * 配置 API Key
     */
    fun configure(apiKey: String) {
        GLMOCRAdapter.apiKey = apiKey
        AppLog.i(LogTag.OCR_GLM, "GLM-OCR configured with API Key")
    }
}
