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

    fun getAdapter(engine: OCREngine): OCRAdapter {
        return adapters.getOrPut(engine) {
            when (engine) {
                OCREngine.ML_KIT -> MLKitOCRAdapter()
                OCREngine.TESSERACT -> TesseractOCRAdapter()
                OCREngine.BAIDU -> BaiduOCRAdapter()
                OCREngine.HUAWEI -> HuaweiOCRAdapter()
            }
        }
    }

    /**
     * 获取最佳可用引擎
     */
    suspend fun getBestAvailableEngine(): OCREngine {
        for (engine in OCREngine.values()) {
            val adapter = getAdapter(engine)
            if (adapter.isAvailable()) {
                return engine
            }
        }
        return OCREngine.ML_KIT
    }
}
