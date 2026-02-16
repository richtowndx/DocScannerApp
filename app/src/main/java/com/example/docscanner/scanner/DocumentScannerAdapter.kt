package com.example.docscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF

/**
 * 文档扫描器统一适配接口
 * 支持多种扫描引擎的统一调用
 */
interface DocumentScannerAdapter {

    /**
     * 获取扫描引擎类型
     */
    val engineType: ScannerEngine

    /**
     * 检查引擎是否可用
     */
    suspend fun isAvailable(): Boolean

    /**
     * 启动扫描（返回ActivityResultLauncher使用的Intent）
     */
    suspend fun prepareScannerIntent(context: Context, config: ScannerConfig): Any

    /**
     * 处理扫描结果
     */
    suspend fun processScanResult(resultData: Any): List<ScanResult>

    /**
     * 检测图像边框（不启动扫描UI）
     */
    suspend fun detectDocumentEdges(bitmap: Bitmap): List<PointF>?

    /**
     * 应用透视校正
     */
    suspend fun applyPerspectiveCorrection(
        bitmap: Bitmap,
        corners: List<PointF>
    ): Bitmap

    /**
     * 应用图像处理（灰度、锐化等）
     */
    suspend fun applyImageProcessing(
        bitmap: Bitmap,
        options: ProcessOptions
    ): Bitmap
}

/**
 * 扫描器工厂 - 根据引擎类型创建对应的适配器
 */
object ScannerFactory {

    private val adapters = mutableMapOf<ScannerEngine, DocumentScannerAdapter>()

    fun getAdapter(engine: ScannerEngine): DocumentScannerAdapter {
        return adapters.getOrPut(engine) {
            when (engine) {
                ScannerEngine.ML_KIT -> MLKitScannerAdapter()
                ScannerEngine.OPENCV_CUSTOM -> OpenCVScannerAdapter()
                ScannerEngine.SMART_CROPPER -> SmartCropperAdapter()
            }
        }
    }

    /**
     * 获取最佳可用引擎
     */
    suspend fun getBestAvailableEngine(): ScannerEngine {
        for (engine in ScannerEngine.values()) {
            if (getAdapter(engine).isAvailable()) {
                return engine
            }
        }
        return ScannerEngine.ML_KIT  // 默认
    }
}
