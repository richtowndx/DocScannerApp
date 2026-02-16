package com.example.docscanner.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 百度OCR适配器
 * 高精度中文识别，但需要网络和API Key
 *
 * 注意：需要添加依赖：
 * implementation("com.baidu.aip:java-sdk:4.16.17")
 *
 * 并在 OCRConfig 中配置 API Key 和 Secret Key
 */
class BaiduOCRAdapter : OCRAdapter {

    companion object {
        private const val TAG = "BaiduOCR"
    }

    override val engineType: OCREngine = OCREngine.BAIDU

    override suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        // 需要网络和API Key
        false  // 默认不可用，需要用户配置
    }

    override suspend fun initialize(): Boolean {
        return false  // 需要用户配置API Key
    }

    override suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig
    ): OCRResult {
        // 百度OCR API调用（需要配置API Key）
        /*
        val client = AipOcr(APP_ID, API_KEY, SECRET_KEY)
        val options = HashMap<String, String>()
        options["language_type"] = "CHN_ENG"

        val bytes = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val result = client.basicGeneral(bytes.toByteArray(), options)

        // 解析结果...
        */

        // 备用：使用ML Kit
        val mlKitAdapter = MLKitOCRAdapter()
        mlKitAdapter.initialize()
        return mlKitAdapter.recognizeText(bitmap, config)
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

/**
 * 华为HMS ML Kit OCR适配器
 */
class HuaweiOCRAdapter : OCRAdapter {

    companion object {
        private const val TAG = "HuaweiOCR"
    }

    override val engineType: OCREngine = OCREngine.HUAWEI

    override suspend fun isAvailable(): Boolean = false

    override suspend fun initialize(): Boolean = false

    override suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig
    ): OCRResult {
        // 备用：使用ML Kit
        val mlKitAdapter = MLKitOCRAdapter()
        mlKitAdapter.initialize()
        return mlKitAdapter.recognizeText(bitmap, config)
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
