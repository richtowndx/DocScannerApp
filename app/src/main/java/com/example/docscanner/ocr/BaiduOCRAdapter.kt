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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

/**
 * 百度OCR适配器
 * 高精度中文识别，需要网络连接和API Key配置
 *
 * 使用说明:
 * 1. 在百度智能云注册账号并开通文字识别服务
 * 2. 创建应用获取 API Key 和 Secret Key
 * 3. 在设置中配置 API Key 和 Secret Key
 *
 * API文档: https://cloud.baidu.com/doc/OCR/index.html
 */
class BaiduOCRAdapter : OCRAdapter {

    companion object {
        private const val TAG = "BaiduOCR"

        // API端点
        private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
        private const val GENERAL_BASIC_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic"
        private const val ACCURATE_BASIC_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic"

        // API配置（需要在设置中配置）
        var apiKey: String? = null
        var secretKey: String? = null

        // Access Token缓存
        private var accessToken: String? = null
        private var tokenExpireTime: Long = 0
    }

    override val engineType: OCREngine = OCREngine.BAIDU

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        // 检查API Key是否配置
        val hasConfig = !apiKey.isNullOrBlank() && !secretKey.isNullOrBlank()
        AppLog.i(LogTag.OCR_BAIDU, "Baidu OCR available: $hasConfig (API Key configured: ${!apiKey.isNullOrBlank()})")
        hasConfig
    }

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isNullOrBlank() || secretKey.isNullOrBlank()) {
                AppLog.e(LogTag.OCR_BAIDU, "API Key or Secret Key not configured")
                return@withContext false
            }

            // 获取Access Token
            val token = getAccessToken()
            if (token.isNullOrBlank()) {
                AppLog.e(LogTag.OCR_BAIDU, "Failed to get access token")
                return@withContext false
            }

            AppLog.i(LogTag.OCR_BAIDU, "Baidu OCR initialized successfully")
            true
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_BAIDU, "Failed to initialize Baidu OCR: ${e.message}", e)
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
                AppLog.w(LogTag.OCR_BAIDU, "Baidu OCR not configured, falling back to ML Kit")
                return@withContext fallbackToMLKit(bitmap, config, startTime)
            }

            // 获取Access Token
            val token = getAccessToken()
            if (token.isNullOrBlank()) {
                AppLog.e(LogTag.OCR_BAIDU, "Failed to get access token, falling back to ML Kit")
                return@withContext fallbackToMLKit(bitmap, config, startTime)
            }

            // 将图片转换为Base64
            val imageBase64 = bitmapToBase64(bitmap)

            // 调用百度OCR API（使用高精度接口）
            val url = "$ACCURATE_BASIC_URL?access_token=$token"
            val params = buildString {
                append("image=").append(URLEncoder.encode(imageBase64, "UTF-8"))
                append("&detect_direction=true")
                append("&paragraph=true")
            }

            val response = sendPostRequest(url, params)
            val result = parseBaiduResponse(response)

            val processingTime = System.currentTimeMillis() - startTime
            AppLog.i(LogTag.OCR_BAIDU, "OCR completed in ${processingTime}ms")

            result.copy(processingTimeMs = processingTime)
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_BAIDU, "OCR failed: ${e.message}", e)
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
     * 获取Access Token
     */
    private fun getAccessToken(): String? {
        // 检查缓存的token是否有效
        if (!accessToken.isNullOrBlank() && System.currentTimeMillis() < tokenExpireTime) {
            return accessToken
        }

        try {
            val url = buildString {
                append(TOKEN_URL)
                append("?grant_type=client_credentials")
                append("&client_id=").append(apiKey)
                append("&client_secret=").append(secretKey)
            }

            val response = sendGetRequest(url)
            val json = JSONObject(response)

            if (json.has("error")) {
                AppLog.e(LogTag.OCR_BAIDU, "Token error: ${json.getString("error_description")}")
                return null
            }

            accessToken = json.getString("access_token")
            val expiresIn = json.optLong("expires_in", 86400)
            tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000 // 提前5分钟过期

            AppLog.i(LogTag.OCR_BAIDU, "Access token obtained, expires in ${expiresIn}s")
            return accessToken
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_BAIDU, "Failed to get access token: ${e.message}", e)
            return null
        }
    }

    /**
     * 将Bitmap转换为Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // 压缩图片以减少传输数据量
        val maxSize = 4096
        val scale = if (bitmap.width > maxSize || bitmap.height > maxSize) {
            maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        } else 1f

        val scaledBitmap = if (scale < 1f) {
            val newWidth = (bitmap.width * scale).toInt()
            val newHeight = (bitmap.height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else bitmap

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * 发送GET请求
     */
    private fun sendGetRequest(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 30000
        connection.readTimeout = 30000

        return try {
            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("HTTP $responseCode: $errorText")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 发送POST请求
     */
    private fun sendPostRequest(urlString: String, params: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpsURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 60000
        connection.readTimeout = 60000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        return try {
            connection.outputStream.write(params.toByteArray(Charsets.UTF_8))
            connection.outputStream.flush()

            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val errorText = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("HTTP $responseCode: $errorText")
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 解析百度OCR响应
     */
    private fun parseBaiduResponse(response: String): OCRResult {
        val json = JSONObject(response)

        // 检查错误
        if (json.has("error_code")) {
            val errorCode = json.getInt("error_code")
            val errorMsg = json.optString("error_msg", "Unknown error")
            throw Exception("Baidu OCR Error ($errorCode): $errorMsg")
        }

        val wordsResult = json.optJSONArray("words_result") ?: return OCRResult(
            textBlocks = emptyList(),
            fullText = "",
            markdownText = "",
            processingTimeMs = 0
        )

        val textBlocks = mutableListOf<TextBlock>()
        val fullText = StringBuilder()

        for (i in 0 until wordsResult.length()) {
            val item = wordsResult.getJSONObject(i)
            val words = item.optString("words", "")

            if (words.isNotBlank()) {
                fullText.append(words).append("\n")

                // 尝试获取位置信息（如果有）
                val location = item.optJSONObject("location")
                val boundingBox = if (location != null) {
                    Rect(
                        location.optInt("left", 0),
                        location.optInt("top", 0),
                        location.optInt("left", 0) + location.optInt("width", 0),
                        location.optInt("top", 0) + location.optInt("height", 0)
                    )
                } else {
                    Rect()
                }

                textBlocks.add(
                    TextBlock(
                        text = words,
                        boundingBox = boundingBox,
                        confidence = 1.0f, // 百度API不返回置信度
                        blockType = BlockType.PARAGRAPH
                    )
                )
            }
        }

        return OCRResult(
            textBlocks = textBlocks,
            fullText = fullText.toString().trim(),
            markdownText = fullText.toString().trim(),
            processingTimeMs = 0
        )
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

    /**
     * 配置API Key
     */
    fun configure(apiKey: String, secretKey: String) {
        BaiduOCRAdapter.apiKey = apiKey
        BaiduOCRAdapter.secretKey = secretKey
        // 清除缓存的token，强制重新获取
        accessToken = null
        tokenExpireTime = 0
        AppLog.i(LogTag.OCR_BAIDU, "Baidu OCR configured with new API Key")
    }
}
