package com.example.docscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit Document Scanner 适配器
 * Google官方扫描方案，提供完整的UI流程
 */
class MLKitScannerAdapter : DocumentScannerAdapter {

    override val engineType: ScannerEngine = ScannerEngine.ML_KIT

    private var scanner: GmsDocumentScanner? = null

    override suspend fun isAvailable(): Boolean {
        return try {
            // ML Kit Document Scanner 在 Android API 21+ 可用
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun prepareScannerIntent(
        context: Context,
        config: ScannerConfig
    ): GmsDocumentScanner {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(config.allowGalleryImport)
            .setPageLimit(config.pageLimit)
            .setResultFormats(
                when (config.resultFormat) {
                    ResultFormat.JPEG -> GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                    ResultFormat.PDF -> GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                }
            )
            .setScannerMode(
                GmsDocumentScannerOptions.SCANNER_MODE_FULL
            )
            .build()

        scanner = GmsDocumentScanning.getClient(options)
        return scanner!!
    }

    override suspend fun processScanResult(resultData: Any): List<ScanResult> =
        suspendCancellableCoroutine { continuation ->
            try {
                val result = resultData as GmsDocumentScanningResult
                val scanResults = mutableListOf<ScanResult>()

                result.pages?.let { pages ->
                    for (page in pages) {
                        val imageUri = page.imageUri
                        scanResults.add(
                            ScanResult(
                                originalImage = imageUri,
                                croppedImage = imageUri,
                                processedImage = null,
                                corners = null,
                                confidence = 1.0f
                            )
                        )
                    }
                }

                continuation.resume(scanResults)
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }

    override suspend fun detectDocumentEdges(bitmap: Bitmap): List<PointF>? {
        // ML Kit Document Scanner 自动处理边框检测
        // 此方法用于自定义处理时使用
        return null
    }

    override suspend fun applyPerspectiveCorrection(
        bitmap: Bitmap,
        corners: List<PointF>
    ): Bitmap {
        // ML Kit Document Scanner 内置透视校正
        // 此方法用于自定义处理
        return bitmap
    }

    override suspend fun applyImageProcessing(
        bitmap: Bitmap,
        options: ProcessOptions
    ): Bitmap {
        var processedBitmap = bitmap

        if (options.enableGrayscale) {
            processedBitmap = applyGrayscale(processedBitmap)
        }

        if (options.enableSharpen) {
            processedBitmap = applySharpen(processedBitmap)
        }

        if (options.enableAutoEnhance) {
            processedBitmap = autoEnhance(processedBitmap, options.brightness, options.contrast)
        }

        return processedBitmap
    }

    private fun applyGrayscale(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val grayscaleBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            // 使用加权平均转换为灰度
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }

        grayscaleBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return grayscaleBitmap
    }

    private fun applySharpen(bitmap: Bitmap): Bitmap {
        // Unsharp Mask 锐化算法
        val width = bitmap.width
        val height = bitmap.height
        val sharpenedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // 简化的锐化卷积核
        val kernel = arrayOf(
            floatArrayOf(0f, -1f, 0f),
            floatArrayOf(-1f, 5f, -1f),
            floatArrayOf(0f, -1f, 0f)
        )

        // 这里简化处理，实际应使用 RenderScript 或 OpenCV
        sharpenedBitmap
        return bitmap // 简化返回
    }

    private fun autoEnhance(bitmap: Bitmap, brightness: Float, contrast: Float): Bitmap {
        // 自动增强对比度和亮度
        val width = bitmap.width
        val height = bitmap.height
        val enhancedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            var r = ((pixel shr 16) and 0xFF).toFloat()
            var g = ((pixel shr 8) and 0xFF).toFloat()
            var b = (pixel and 0xFF).toFloat()

            // 应用亮度和对比度
            r = ((r - 128) * contrast + 128) * brightness
            g = ((g - 128) * contrast + 128) * brightness
            b = ((b - 128) * contrast + 128) * brightness

            // 裁剪到有效范围
            r = r.coerceIn(0f, 255f)
            g = g.coerceIn(0f, 255f)
            b = b.coerceIn(0f, 255f)

            pixels[i] = (0xFF shl 24) or
                    (r.toInt() shl 16) or
                    (g.toInt() shl 8) or
                    b.toInt()
        }

        enhancedBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return enhancedBitmap
    }
}
