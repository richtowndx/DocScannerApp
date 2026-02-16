package com.example.docscanner.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Base64
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * SiliconFlow OCR 适配器
 * 云端高精度 OCR 识别，支持多种 OCR 模型
 *
 * 使用说明:
 * 1. 在 SiliconFlow 平台注册账号: https://cloud.siliconflow.cn
 * 2. 获取 API Key
 * 3. 在设置中配置 API Key 和选择模型
 *
 * 支持的模型:
 * - PaddlePaddle/PaddleOCR-VL-1.5
 * - PaddlePaddle/PaddleOCR-VL
 * - deepseek-ai/DeepSeek-OCR
 *
 * API文档: https://docs.siliconflow.cn/cn/userguide/capabilities/multimodal-vision
 */
class SiliconFlowOCRAdapter : OCRAdapter {

    companion object {
        private const val TAG = "SiliconFlowOCR"

        // API 端点
        private const val API_URL = "https://api.siliconflow.cn/v1/chat/completions"

        // API Key 配置（需要在设置中配置）
        var apiKey: String? = null

        // 选择的模型
        var selectedModel: SiliconFlowOCRModel = SiliconFlowOCRModel.PADDLE_OCR_VL_1_5

        // OCR 提示词
        private val OCR_PROMPTS = mapOf(
            SiliconFlowOCRModel.PADDLE_OCR_VL_1_5 to "请识别图片中的所有文字内容，保持原有格式和布局。",
            SiliconFlowOCRModel.PADDLE_OCR_VL to "请识别图片中的所有文字内容，保持原有格式和布局。",
            SiliconFlowOCRModel.DEEPSEEK_OCR to "<image>\n<|grounding|>OCR this image and convert the document to markdown format."
        )
    }

    override val engineType: OCREngine = OCREngine.SILICONFLOW

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        val hasConfig = !apiKey.isNullOrBlank()
        AppLog.i(LogTag.OCR_SILICONFLOW, "SiliconFlow OCR available: $hasConfig (API Key configured: ${!apiKey.isNullOrBlank()}, Model: ${selectedModel.displayName})")
        hasConfig
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNullOrBlank()) {
                AppLog.e(LogTag.OCR_SILICONFLOW, "API Key not configured")
                return@withContext false
            }

            AppLog.i(LogTag.OCR_SILICONFLOW, "SiliconFlow OCR initialized successfully with API Key, Model: ${selectedModel.displayName}")
            true
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_SILICONFLOW, "Failed to initialize SiliconFlow OCR: ${e.message}", e)
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
                AppLog.w(LogTag.OCR_SILICONFLOW, "SiliconFlow OCR not configured, falling back to ML Kit")
                return@withContext fallbackToMLKit(bitmap, config, startTime)
            }

            AppLog.i(LogTag.OCR_SILICONFLOW, "Starting SiliconFlow OCR recognition with model: ${selectedModel.displayName}")

            // 将图片转换为 Base64 Data URI
            val imageDataUri = bitmapToDataUri(bitmap)
            AppLog.d(LogTag.OCR_SILICONFLOW, "Image converted to base64, size: ${imageDataUri.length} chars")

            // 构建请求 JSON
            val requestBody = buildRequestBody(imageUri = imageDataUri)
            AppLog.d(LogTag.OCR_SILICONFLOW, "Request body built, length: ${requestBody.toString().length}")

            AppLog.i(LogTag.OCR_SILICONFLOW, "Sending request to SiliconFlow API...")
            AppLog.d(LogTag.OCR_SILICONFLOW, "API URL: $API_URL")
            AppLog.d(LogTag.OCR_SILICONFLOW, "Model: ${selectedModel.modelId}")

            // 发送请求
            val response = sendPostRequest(API_URL, requestBody.toString())

            // 详细记录完整响应（分段输出避免日志截断）
            AppLog.i(LogTag.OCR_SILICONFLOW, "=== Full API Response ===")
            if (response.length > 2000) {
                var offset = 0
                while (offset < response.length) {
                    val end = minOf(offset + 2000, response.length)
                    AppLog.i(LogTag.OCR_SILICONFLOW, "Response[$offset-$end]: ${response.substring(offset, end)}")
                    offset = end
                }
            } else {
                AppLog.i(LogTag.OCR_SILICONFLOW, "Response: $response")
            }
            AppLog.i(LogTag.OCR_SILICONFLOW, "=== End Response ===")

            // 解析响应
            val result = parseSiliconFlowResponse(response, config, startTime)

            val processingTime = System.currentTimeMillis() - startTime
            AppLog.i(LogTag.OCR_SILICONFLOW, "SiliconFlow OCR completed in ${processingTime}ms, found ${result.textBlocks.size} blocks")

            result.copy(processingTimeMs = processingTime)
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_SILICONFLOW, "SiliconFlow OCR failed: ${e.message}", e)
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
     * 构建请求体
     */
    private fun buildRequestBody(imageUri: String): JSONObject {
        val prompt = OCR_PROMPTS[selectedModel] ?: "请识别图片中的所有文字内容。"

        return JSONObject().apply {
            put("model", selectedModel.modelId)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", org.json.JSONArray().apply {
                        // 图片内容
                        put(JSONObject().apply {
                            put("type", "image_url")
                            put("image_url", JSONObject().apply {
                                put("url", imageUri)
                            })
                        })
                        // 文本提示
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", prompt)
                        })
                    })
                })
            })
            put("max_tokens", 4096)
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

        AppLog.d(LogTag.OCR_SILICONFLOW, "Image compressed: ${bitmap.width}x${bitmap.height} -> ${scaledBitmap.width}x${scaledBitmap.height}, base64 size: ${base64.length}")

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
            connection.connectTimeout = 120000  // 2分钟超时，云端OCR可能需要较长时间
            connection.readTimeout = 120000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")

            AppLog.d(LogTag.OCR_SILICONFLOW, "Request headers set, Authorization: Bearer ${apiKey?.take(10)}...")

            // 发送请求体
            connection.outputStream.use { os ->
                val input = jsonBody.toByteArray(Charsets.UTF_8)
                os.write(input, 0, input.size)
            }

            val responseCode = connection.responseCode
            AppLog.i(LogTag.OCR_SILICONFLOW, "Response code: $responseCode")

            // 获取响应头中的 trace-id（用于问题追踪）
            val traceId = connection.getHeaderField("x-siliconcloud-trace-id")
            if (!traceId.isNullOrBlank()) {
                AppLog.i(LogTag.OCR_SILICONFLOW, "Request trace-id: $traceId")
            }

            if (responseCode == HttpsURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "Unknown error"
                AppLog.e(LogTag.OCR_SILICONFLOW, "HTTP Error $responseCode: $errorText")
                throw Exception("HTTP $responseCode: $errorText")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 解析 SiliconFlow API 响应
     * OpenAI 兼容格式:
     * {
     *   "id": "...",
     *   "choices": [{
     *     "message": {
     *       "role": "assistant",
     *       "content": "识别的文字内容..."
     *     },
     *     "finish_reason": "stop"
     *   }],
     *   "usage": {...}
     * }
     */
    private fun parseSiliconFlowResponse(
        response: String,
        config: OCRConfig,
        startTime: Long
    ): OCRResult {
        try {
            val json = JSONObject(response)
            AppLog.i(LogTag.OCR_SILICONFLOW, "Parsing response, top-level keys: ${json.keys().asSequence().toList()}")

            // 检查错误
            if (json.has("error")) {
                val error = json.optJSONObject("error")
                if (error != null) {
                    val errorCode = error.optString("code", "unknown")
                    val errorMsg = error.optString("message", "Unknown error")
                    throw Exception("SiliconFlow API Error ($errorCode): $errorMsg")
                } else {
                    val errorMsg = json.optString("error", "Unknown error")
                    throw Exception("SiliconFlow API Error: $errorMsg")
                }
            }

            val textBlocks = mutableListOf<TextBlock>()
            val fullTextBuilder = StringBuilder()
            val markdownBuilder = StringBuilder()

            // 解析 OpenAI 兼容格式
            val choices = json.optJSONArray("choices")
            if (choices != null && choices.length() > 0) {
                val firstChoice = choices.getJSONObject(0)
                val message = firstChoice.optJSONObject("message")
                val content = message?.optString("content", "") ?: ""

                AppLog.i(LogTag.OCR_SILICONFLOW, "Got content from API, length: ${content.length}")
                AppLog.d(LogTag.OCR_SILICONFLOW, "Content preview: ${content.take(200)}...")

                if (content.isNotBlank()) {
                    // 处理内容，按段落分割
                    val lines = content.split("\n")
                    var currentBlock = StringBuilder()
                    var lineIndex = 0

                    for (line in lines) {
                        if (line.trim().isEmpty()) {
                            // 空行表示段落结束
                            if (currentBlock.isNotEmpty()) {
                                val blockText = currentBlock.toString().trim()
                                if (blockText.isNotBlank()) {
                                    val blockType = detectBlockType(blockText)

                                    textBlocks.add(
                                        TextBlock(
                                            text = blockText,
                                            boundingBox = Rect(0, lineIndex * 20, 100, (lineIndex + 1) * 20),
                                            confidence = 1.0f,
                                            blockType = blockType
                                        )
                                    )

                                    fullTextBuilder.append(blockText).append("\n")
                                    appendMarkdown(markdownBuilder, blockText, blockType)
                                }
                                currentBlock = StringBuilder()
                            }
                        } else {
                            if (currentBlock.isNotEmpty()) {
                                currentBlock.append("\n")
                            }
                            currentBlock.append(line)
                        }
                        lineIndex++
                    }

                    // 处理最后一个块
                    if (currentBlock.isNotEmpty()) {
                        val blockText = currentBlock.toString().trim()
                        if (blockText.isNotBlank()) {
                            val blockType = detectBlockType(blockText)

                            textBlocks.add(
                                TextBlock(
                                    text = blockText,
                                    boundingBox = Rect(0, lineIndex * 20, 100, (lineIndex + 1) * 20),
                                    confidence = 1.0f,
                                    blockType = blockType
                                )
                            )

                            fullTextBuilder.append(blockText).append("\n")
                            appendMarkdown(markdownBuilder, blockText, blockType)
                        }
                    }

                    // 如果没有按段落分割出块，直接按行处理
                    if (textBlocks.isEmpty()) {
                        for ((index, line) in lines.withIndex()) {
                            if (line.isNotBlank()) {
                                val blockType = detectBlockType(line)

                                textBlocks.add(
                                    TextBlock(
                                        text = line,
                                        boundingBox = Rect(0, index * 20, 100, (index + 1) * 20),
                                        confidence = 1.0f,
                                        blockType = blockType
                                    )
                                )

                                fullTextBuilder.append(line).append("\n")
                                appendMarkdown(markdownBuilder, line, blockType)
                            }
                        }
                    }
                }
            }

            val processingTime = System.currentTimeMillis() - startTime
            val fullText = fullTextBuilder.toString().trim()
            val markdownText = markdownBuilder.toString().trim()

            AppLog.i(LogTag.OCR_SILICONFLOW, "Parsed ${textBlocks.size} blocks, fullText length: ${fullText.length}")

            return OCRResult(
                textBlocks = textBlocks,
                fullText = fullText,
                markdownText = if (config.enableMarkdown) markdownText else fullText,
                processingTimeMs = processingTime
            )
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_SILICONFLOW, "Failed to parse response: ${e.message}", e)
            throw e
        }
    }

    /**
     * 检测文本块类型
     */
    private fun detectBlockType(text: String): BlockType {
        val trimmed = text.trim()

        return when {
            trimmed.startsWith("# ") || trimmed.startsWith("## ") -> BlockType.TITLE
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> BlockType.LIST_ITEM
            trimmed.startsWith("|") && trimmed.contains("|") -> BlockType.TABLE
            trimmed.all { it.isUpperCase() || it.isWhitespace() || it in "0123456789-:/" } && trimmed.length < 50 -> BlockType.TITLE
            else -> BlockType.PARAGRAPH
        }
    }

    /**
     * 添加 Markdown 格式
     */
    private fun appendMarkdown(builder: StringBuilder, text: String, blockType: BlockType) {
        when (blockType) {
            BlockType.TITLE -> builder.append("# $text\n\n")
            BlockType.SUBTITLE -> builder.append("## $text\n\n")
            BlockType.LIST_ITEM -> builder.append("$text\n")
            else -> builder.append("$text\n\n")
        }
    }

    /**
     * 回退到 ML Kit
     */
    private suspend fun fallbackToMLKit(
        bitmap: Bitmap,
        config: OCRConfig,
        startTime: Long
    ): OCRResult {
        AppLog.i(LogTag.OCR_SILICONFLOW, "Falling back to ML Kit")
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
     * 配置 API Key 和模型
     */
    fun configure(apiKey: String, model: SiliconFlowOCRModel = selectedModel) {
        SiliconFlowOCRAdapter.apiKey = apiKey
        SiliconFlowOCRAdapter.selectedModel = model
        AppLog.i(LogTag.OCR_SILICONFLOW, "SiliconFlow OCR configured with API Key and model: ${model.displayName}")
    }

    /**
     * 设置模型
     */
    fun setModel(model: SiliconFlowOCRModel) {
        SiliconFlowOCRAdapter.selectedModel = model
        AppLog.i(LogTag.OCR_SILICONFLOW, "SiliconFlow OCR model changed to: ${model.displayName}")
    }
}
