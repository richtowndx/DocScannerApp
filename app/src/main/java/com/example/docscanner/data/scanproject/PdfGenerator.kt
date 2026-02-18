package com.example.docscanner.data.scanproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF生成服务
 * 将扫描图片合并为PDF文件
 */
class PdfGenerator private constructor(private val context: Context) {

    private val scanProjectService = ScanProjectService.getInstance(context)

    /**
     * 生成PDF的进度回调
     */
    interface ProgressCallback {
        fun onProgress(current: Int, total: Int, message: String)
        fun onComplete(outputPath: String)
        fun onError(error: Throwable)
    }

    /**
     * 为项目生成PDF
     * @param projectName 项目名称
     * @param callback 进度回调
     */
    suspend fun generatePdf(
        projectName: String,
        callback: ProgressCallback? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLog.i(LogTag.PDF_SERVICE, "==================== PDF GENERATION START ====================")
            AppLog.i(LogTag.PDF_SERVICE, "Project name: $projectName")
            AppLog.i(LogTag.PDF_SERVICE, "Thread: ${Thread.currentThread().name}")

            // 获取项目图片（按页码排序）
            val imageFiles = scanProjectService.getProjectImagesSorted(projectName)
            AppLog.i(LogTag.PDF_SERVICE, "=== getProjectImagesSorted returned ${imageFiles.size} files ===")

            // 打印所有文件信息
            imageFiles.forEachIndexed { index, file ->
                AppLog.i(LogTag.PDF_SERVICE, "ImageFile[$index]: ${file.name}, path=${file.absolutePath}, size=${file.length()} bytes")
            }

            if (imageFiles.isEmpty()) {
                AppLog.e(LogTag.PDF_SERVICE, "No images found for project: $projectName")
                return@withContext Result.failure(IllegalArgumentException("项目没有图片"))
            }

            val totalImages = imageFiles.size
            callback?.onProgress(0, totalImages, "准备生成PDF...")

            // 创建PDF文档
            val pdfDocument = PdfDocument()
            var currentPageIndex = 0
            var failedCount = 0

            AppLog.i(LogTag.PDF_SERVICE, "=== STARTING PDF PAGE GENERATION ===")

            for ((index, imageFile) in imageFiles.withIndex()) {
                AppLog.i(LogTag.PDF_SERVICE, "--- Processing image ${index + 1}/$totalImages: ${imageFile.name} ---")

                try {
                    // 检查文件是否存在
                    if (!imageFile.exists()) {
                        AppLog.w(LogTag.PDF_SERVICE, "Image file NOT EXISTS: ${imageFile.absolutePath}")
                        failedCount++
                        continue
                    }

                    val fileSize = imageFile.length()
                    AppLog.i(LogTag.PDF_SERVICE, "File exists, size: $fileSize bytes")

                    if (fileSize == 0L) {
                        AppLog.w(LogTag.PDF_SERVICE, "File is EMPTY: ${imageFile.absolutePath}")
                        failedCount++
                        continue
                    }

                    callback?.onProgress(index + 1, totalImages, "处理图片 ${index + 1}/$totalImages")

                    // 加载图片
                    AppLog.i(LogTag.PDF_SERVICE, "Decoding bitmap from: ${imageFile.absolutePath}")
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap == null) {
                        AppLog.w(LogTag.PDF_SERVICE, "FAILED to decode bitmap: ${imageFile.absolutePath}")
                        failedCount++
                        continue
                    }

                    AppLog.i(LogTag.PDF_SERVICE, "Bitmap decoded: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")

                    // 创建PDF页面（使用图片实际尺寸）
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        currentPageIndex + 1
                    ).create()

                    AppLog.i(LogTag.PDF_SERVICE, "Creating PDF page ${currentPageIndex + 1}, size: ${bitmap.width}x${bitmap.height}")

                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // 绘制图片到页面
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)

                    currentPageIndex++
                    AppLog.i(LogTag.PDF_SERVICE, "SUCCESS: Added page $currentPageIndex - ${imageFile.name}")

                    // 回收bitmap
                    bitmap.recycle()
                } catch (e: Exception) {
                    failedCount++
                    AppLog.e(LogTag.PDF_SERVICE, "EXCEPTION adding image to PDF: ${imageFile.name}", e)
                }
            }

            AppLog.i(LogTag.PDF_SERVICE, "=== PDF PAGE GENERATION COMPLETE ===")
            AppLog.i(LogTag.PDF_SERVICE, "Total images processed: $totalImages")
            AppLog.i(LogTag.PDF_SERVICE, "Pages successfully added: $currentPageIndex")
            AppLog.i(LogTag.PDF_SERVICE, "Failed images: $failedCount")

            if (currentPageIndex == 0) {
                pdfDocument.close()
                AppLog.e(LogTag.PDF_SERVICE, "No valid images to generate PDF - all $totalImages images failed")
                return@withContext Result.failure(IllegalArgumentException("没有有效的图片可以生成PDF"))
            }

            // 保存PDF文件
            val projectDir = scanProjectService.getProjectDirectory(projectName)
            val pdfFile = File(projectDir, "$projectName.pdf")

            AppLog.i(LogTag.PDF_SERVICE, "Writing PDF to: ${pdfFile.absolutePath}")

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            AppLog.i(LogTag.PDF_SERVICE, "==================== PDF GENERATION SUCCESS ====================")
            AppLog.i(LogTag.PDF_SERVICE, "Output: ${pdfFile.absolutePath}")
            AppLog.i(LogTag.PDF_SERVICE, "File size: ${pdfFile.length()} bytes")
            AppLog.i(LogTag.PDF_SERVICE, "Total pages: $currentPageIndex")
            callback?.onComplete(pdfFile.absolutePath)

            Result.success(pdfFile.absolutePath)
        } catch (e: Exception) {
            AppLog.e(LogTag.PDF_SERVICE, "==================== PDF GENERATION FAILED ====================")
            AppLog.e(LogTag.PDF_SERVICE, "Project: $projectName", e)
            callback?.onError(e)
            Result.failure(e)
        }
    }

    /**
     * 获取PDF输出路径
     */
    fun getPdfOutputPath(projectName: String): File {
        val projectDir = scanProjectService.getProjectDirectory(projectName)
        return File(projectDir, "$projectName.pdf")
    }

    /**
     * 检查PDF是否已存在
     */
    fun isPdfExists(projectName: String): Boolean {
        return getPdfOutputPath(projectName).exists()
    }

    companion object {
        @Volatile
        private var INSTANCE: PdfGenerator? = null

        fun getInstance(context: Context): PdfGenerator {
            return INSTANCE ?: synchronized(this) {
                val instance = PdfGenerator(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
