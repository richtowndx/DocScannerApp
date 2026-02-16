package com.example.docscanner.scanner

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri

/**
 * 扫描结果数据类
 */
data class ScanResult(
    val originalImage: Uri,
    val croppedImage: Uri,
    val processedImage: Uri? = null,  // 灰度/锐化后的图像
    val corners: List<PointF>? = null,  // 检测到的四个角点
    val confidence: Float = 0f  // 边框检测置信度
)

/**
 * 图像处理选项
 */
data class ProcessOptions(
    val enableGrayscale: Boolean = false,
    val enableSharpen: Boolean = false,
    val enableAutoEnhance: Boolean = true,
    val brightness: Float = 1.0f,  // 0.5 - 2.0
    val contrast: Float = 1.0f     // 0.5 - 2.0
)

/**
 * 扫描引擎类型
 */
enum class ScannerEngine {
    ML_KIT,          // Google ML Kit Document Scanner
    OPENCV_CUSTOM,   // 自定义OpenCV实现
    SMART_CROPPER    // SmartCropper库
}

/**
 * 扫描配置
 */
data class ScannerConfig(
    val engine: ScannerEngine = ScannerEngine.ML_KIT,
    val allowGalleryImport: Boolean = true,
    val pageLimit: Int = 10,
    val resultFormat: ResultFormat = ResultFormat.JPEG
)

enum class ResultFormat {
    JPEG, PDF
}

/**
 * 扫描回调
 */
interface ScanCallback {
    fun onSuccess(result: ScanResult)
    fun onError(error: Throwable)
    fun onCancelled()
}
