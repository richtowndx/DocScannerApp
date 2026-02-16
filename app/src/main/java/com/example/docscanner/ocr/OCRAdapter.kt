package com.example.docscanner.ocr

import android.graphics.Bitmap

/**
 * OCR 统一适配接口
 * 支持多种OCR引擎的统一调用
 */
interface OCRAdapter {

    /**
     * 获取OCR引擎类型
     */
    val engineType: OCREngine

    /**
     * 检查引擎是否可用
     */
    suspend fun isAvailable(): Boolean

    /**
     * 初始化引擎（加载模型等）
     */
    suspend fun initialize(): Boolean

    /**
     * 识别图像中的文字
     */
    suspend fun recognizeText(bitmap: Bitmap, config: OCRConfig): OCRResult

    /**
     * 识别图像中的文字（带回调）
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        config: OCRConfig,
        callback: OCRCallback
    )
}

/**
 * OCR工厂 - 根据引擎类型创建对应的适配器
 */
object OCRFactory {

    private val adapters = mutableMapOf<OCREngine, OCRAdapter>()

    /**
     * 获取OCR适配器（需要Context的引擎会使用applicationContext）
     */
    fun getAdapter(engine: OCREngine, context: android.content.Context? = null): OCRAdapter {
        return adapters.getOrPut(engine) {
            when (engine) {
                OCREngine.ML_KIT -> MLKitOCRAdapter()
                OCREngine.TESSERACT -> {
                    if (context != null) {
                        TesseractOCRAdapter(context.applicationContext)
                    } else {
                        // 如果没有context，返回一个空实现的适配器
                        object : OCRAdapter {
                            override val engineType: OCREngine = OCREngine.TESSERACT
                            override suspend fun isAvailable() = false
                            override suspend fun initialize() = false
                            override suspend fun recognizeText(bitmap: android.graphics.Bitmap, config: OCRConfig): OCRResult {
                                val mlKit = MLKitOCRAdapter()
                                mlKit.initialize()
                                return mlKit.recognizeText(bitmap, config)
                            }
                            override suspend fun recognizeText(bitmap: android.graphics.Bitmap, config: OCRConfig, callback: OCRCallback) {
                                callback.onError(IllegalStateException("Context required for Tesseract"))
                            }
                        }
                    }
                }
                OCREngine.BAIDU -> BaiduOCRAdapter()
                OCREngine.GLM_OCR -> GLMOCRAdapter()
                OCREngine.SILICONFLOW -> SiliconFlowOCRAdapter()
            }
        }
    }

    /**
     * 清除缓存的适配器（在配置更改时使用）
     */
    fun clearCache() {
        adapters.clear()
    }

    /**
     * 获取最佳可用引擎
     */
    suspend fun getBestAvailableEngine(context: android.content.Context? = null): OCREngine {
        for (engine in OCREngine.values()) {
            val adapter = getAdapter(engine, context)
            if (adapter.isAvailable()) {
                return engine
            }
        }
        return OCREngine.ML_KIT
    }
}
