package com.example.docscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SmartCropper 库适配器
 * 基于 https://github.com/pqpo/SmartCropper
 * 提供智能边框检测和裁剪
 *
 * 注意：需要在 build.gradle 中添加依赖：
 * implementation("com.github.pqpo:SmartCropper:v2.1.3")
 */
class SmartCropperAdapter : DocumentScannerAdapter {

    companion object {
        private const val TAG = "SmartCropper"
    }

    override val engineType: ScannerEngine = ScannerEngine.SMART_CROPPER

    override suspend fun isAvailable(): Boolean {
        return try {
            // 检查 SmartCropper 类是否可用
            // Class.forName("me.pqpo.smartcropperlib.SmartCropper")
            // 由于是可选依赖，这里返回 false，用户需要手动添加依赖
            false
        } catch (e: Exception) {
            Log.e(TAG, "SmartCropper not available: ${e.message}")
            false
        }
    }

    override suspend fun prepareScannerIntent(context: Context, config: ScannerConfig): Any {
        throw UnsupportedOperationException("SmartCropper adapter does not use Intent-based scanning")
    }

    override suspend fun processScanResult(resultData: Any): List<ScanResult> {
        throw UnsupportedOperationException("Use detectDocumentEdges and applyPerspectiveCorrection instead")
    }

    /**
     * 使用 SmartCropper 检测文档边框
     */
    override suspend fun detectDocumentEdges(bitmap: Bitmap): List<PointF>? =
        withContext(Dispatchers.Default) {
            try {
                // SmartCropper 的实际调用（需要添加依赖后取消注释）
                /*
                val points = SmartCropper.scan(bitmap)
                if (points != null && points.size == 4) {
                    return@withContext points.map { point ->
                        PointF(point.x, point.y)
                    }
                }
                */

                // 备用方案：使用 OpenCV
                val openCVAdapter = OpenCVScannerAdapter()
                if (openCVAdapter.isAvailable()) {
                    return@withContext openCVAdapter.detectDocumentEdges(bitmap)
                }

                null
            } catch (e: Exception) {
                Log.e(TAG, "SmartCropper edge detection failed: ${e.message}")
                null
            }
        }

    /**
     * 使用 SmartCropper 进行透视校正
     */
    override suspend fun applyPerspectiveCorrection(
        bitmap: Bitmap,
        corners: List<PointF>
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            // SmartCropper 的实际调用（需要添加依赖后取消注释）
            /*
            val points = corners.map { Point(it.x.toInt(), it.y.toInt()) }.toTypedArray()
            return@withContext SmartCropper.crop(bitmap, points)
            */

            // 备用方案：使用 OpenCV
            val openCVAdapter = OpenCVScannerAdapter()
            if (openCVAdapter.isAvailable()) {
                return@withContext openCVAdapter.applyPerspectiveCorrection(bitmap, corners)
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "SmartCropper perspective correction failed: ${e.message}")
            bitmap
        }
    }

    /**
     * 图像处理
     */
    override suspend fun applyImageProcessing(
        bitmap: Bitmap,
        options: ProcessOptions
    ): Bitmap = withContext(Dispatchers.Default) {
        // 使用 OpenCV 进行图像处理
        val openCVAdapter = OpenCVScannerAdapter()
        if (openCVAdapter.isAvailable()) {
            return@withContext openCVAdapter.applyImageProcessing(bitmap, options)
        }
        bitmap
    }
}
