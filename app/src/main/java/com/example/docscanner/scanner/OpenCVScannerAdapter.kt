package com.example.docscanner.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * OpenCV 自定义扫描适配器
 * 提供完全自定义的边框检测和透视校正
 */
class OpenCVScannerAdapter : DocumentScannerAdapter {

    companion object {
        private const val TAG = "OpenCVScanner"
    }

    override val engineType: ScannerEngine = ScannerEngine.OPENCV_CUSTOM

    private var isOpenCVInitialized = false

    override suspend fun isAvailable(): Boolean {
        return try {
            if (!isOpenCVInitialized) {
                isOpenCVInitialized = OpenCVLoader.initDebug()
            }
            isOpenCVInitialized
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV not available: ${e.message}")
            false
        }
    }

    override suspend fun prepareScannerIntent(context: Context, config: ScannerConfig): Any {
        // OpenCV方案不使用Intent，直接处理图像
        throw UnsupportedOperationException("OpenCV adapter does not use Intent-based scanning")
    }

    override suspend fun processScanResult(resultData: Any): List<ScanResult> {
        // OpenCV方案直接处理Bitmap
        throw UnsupportedOperationException("Use detectDocumentEdges and applyPerspectiveCorrection instead")
    }

    /**
     * 检测文档边框
     * 使用Canny边缘检测 + 轮廓提取
     */
    override suspend fun detectDocumentEdges(bitmap: Bitmap): List<PointF>? =
        withContext(Dispatchers.Default) {
            try {
                val mat = Mat()
                Utils.bitmapToMat(bitmap, mat)

                // 1. 转换为灰度图
                val gray = Mat()
                Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

                // 2. 高斯模糊去噪
                val blurred = Mat()
                Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

                // 3. Canny边缘检测
                val edges = Mat()
                Imgproc.Canny(blurred, edges, 50.0, 150.0)

                // 4. 膨胀操作，连接断开的边缘
                val dilated = Mat()
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
                Imgproc.dilate(edges, dilated, kernel)

                // 5. 查找轮廓
                val contours = ArrayList<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(
                    dilated,
                    contours,
                    hierarchy,
                    Imgproc.RETR_EXTERNAL,
                    Imgproc.CHAIN_APPROX_SIMPLE
                )

                // 6. 找到最大的四边形轮廓
                val documentCorners = findLargestQuadrilateral(contours, mat.size())

                // 释放资源
                mat.release()
                gray.release()
                blurred.release()
                edges.release()
                dilated.release()
                hierarchy.release()
                for (contour in contours) {
                    contour.release()
                }

                documentCorners?.map { point ->
                    PointF(point.x.toFloat(), point.y.toFloat())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Edge detection failed: ${e.message}")
                null
            }
        }

    /**
     * 透视校正
     * 将倾斜的文档转换为正矩形
     */
    override suspend fun applyPerspectiveCorrection(
        bitmap: Bitmap,
        corners: List<PointF>
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            // 源点（检测到的四个角）
            val srcPoints = MatOfPoint2f(
                Point(corners[0].x.toDouble(), corners[0].y.toDouble()),
                Point(corners[1].x.toDouble(), corners[1].y.toDouble()),
                Point(corners[2].x.toDouble(), corners[2].y.toDouble()),
                Point(corners[3].x.toDouble(), corners[3].y.toDouble())
            )

            // 计算目标矩形的宽高
            val width = calculateMaxWidth(corners)
            val height = calculateMaxHeight(corners)

            // 目标点（正矩形的四个角）
            val dstPoints = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(width.toDouble(), 0.0),
                Point(width.toDouble(), height.toDouble()),
                Point(0.0, height.toDouble())
            )

            // 计算透视变换矩阵
            val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            // 应用透视变换
            val dstMat = Mat()
            Imgproc.warpPerspective(
                srcMat,
                dstMat,
                perspectiveMatrix,
                Size(width.toDouble(), height.toDouble())
            )

            // 转换为Bitmap
            val resultBitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(dstMat, resultBitmap)

            // 释放资源
            srcMat.release()
            dstMat.release()
            perspectiveMatrix.release()

            resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Perspective correction failed: ${e.message}")
            bitmap
        }
    }

    /**
     * 应用图像处理
     */
    override suspend fun applyImageProcessing(
        bitmap: Bitmap,
        options: ProcessOptions
    ): Bitmap = withContext(Dispatchers.Default) {
        try {
            val srcMat = Mat()
            Utils.bitmapToMat(bitmap, srcMat)

            var resultMat = srcMat

            if (options.enableGrayscale) {
                resultMat = applyGrayscaleOpenCV(resultMat)
            }

            if (options.enableSharpen) {
                resultMat = applySharpenOpenCV(resultMat)
            }

            if (options.enableAutoEnhance) {
                resultMat = applyAutoEnhanceOpenCV(
                    resultMat,
                    options.brightness.toDouble(),
                    options.contrast.toDouble()
                )
            }

            val resultBitmap = Bitmap.createBitmap(
                resultMat.cols(),
                resultMat.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(resultMat, resultBitmap)

            if (resultMat != srcMat) {
                resultMat.release()
            }
            srcMat.release()

            resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Image processing failed: ${e.message}")
            bitmap
        }
    }

    // ============ 私有辅助方法 ============

    private fun findLargestQuadrilateral(
        contours: List<MatOfPoint>,
        imageSize: Size
    ): List<Point>? {
        var maxArea = 0.0
        var bestContour: MatOfPoint2f? = null

        val imageArea = imageSize.width * imageSize.height

        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val area = Imgproc.contourArea(contour)

            // 跳过太小的轮廓（小于图像面积的5%）
            if (area < imageArea * 0.05) continue

            // 轮廓近似
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

            // 检查是否为四边形
            if (approx.toArray().size == 4 && area > maxArea) {
                maxArea = area
                bestContour = approx
            }

            contour2f.release()
        }

        return bestContour?.toList()?.let { orderPoints(it) }
    }

    /**
     * 按顺序排列四个角点：左上、右上、右下、左下
     */
    private fun orderPoints(points: List<Point>): List<Point> {
        val sorted = points.sortedBy { it.x + it.y }
        val topLeft = sorted[0]
        val bottomRight = sorted[3]

        val diffSorted = points.sortedBy { it.y - it.x }
        val topRight = diffSorted[0]
        val bottomLeft = diffSorted[3]

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun calculateMaxWidth(corners: List<PointF>): Int {
        val widthTop = sqrt(
            (corners[1].x - corners[0].x).toDouble().pow(2) +
                    (corners[1].y - corners[0].y).toDouble().pow(2)
        )
        val widthBottom = sqrt(
            (corners[2].x - corners[3].x).toDouble().pow(2) +
                    (corners[2].y - corners[3].y).toDouble().pow(2)
        )
        return maxOf(widthTop, widthBottom).toInt()
    }

    private fun calculateMaxHeight(corners: List<PointF>): Int {
        val heightLeft = sqrt(
            (corners[3].x - corners[0].x).toDouble().pow(2) +
                    (corners[3].y - corners[0].y).toDouble().pow(2)
        )
        val heightRight = sqrt(
            (corners[2].x - corners[1].x).toDouble().pow(2) +
                    (corners[2].y - corners[1].y).toDouble().pow(2)
        )
        return maxOf(heightLeft, heightRight).toInt()
    }

    private fun applyGrayscaleOpenCV(mat: Mat): Mat {
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        val result = Mat()
        Imgproc.cvtColor(gray, result, Imgproc.COLOR_GRAY2RGBA)
        gray.release()
        return result
    }

    private fun applySharpenOpenCV(mat: Mat): Mat {
        val kernel = Mat(3, 3, CvType.CV_32F)
        kernel.put(0, 0,
            0.0, -1.0, 0.0,
            -1.0, 5.0, -1.0,
            0.0, -1.0, 0.0
        )

        val sharpened = Mat()
        Imgproc.filter2D(mat, sharpened, -1, kernel)
        kernel.release()
        return sharpened
    }

    private fun applyAutoEnhanceOpenCV(mat: Mat, brightness: Double, contrast: Double): Mat {
        val enhanced = Mat()
        mat.convertTo(enhanced, -1, contrast, (brightness - 1.0) * 128)
        return enhanced
    }
}
