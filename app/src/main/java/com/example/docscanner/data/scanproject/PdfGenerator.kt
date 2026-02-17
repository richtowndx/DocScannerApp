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
            AppLog.i(LogTag.PDF_SERVICE, "Starting PDF generation for project: $projectName")

            // 获取项目图片（按页码排序）
            val imageFiles = scanProjectService.getProjectImagesSorted(projectName)
            AppLog.i(LogTag.PDF_SERVICE, "Found ${imageFiles.size} images for project")

            if (imageFiles.isEmpty()) {
                AppLog.e(LogTag.PDF_SERVICE, "No images found for project: $projectName")
                return@withContext Result.failure(IllegalArgumentException("项目没有图片"))
            }

            val totalImages = imageFiles.size
            callback?.onProgress(0, totalImages, "准备生成PDF...")

            // 创建PDF文档
            val pdfDocument = PdfDocument()
            var currentPageIndex = 0

            for ((index, imageFile) in imageFiles.withIndex()) {
                try {
                    // 检查文件是否存在
                    if (!imageFile.exists()) {
                        AppLog.w(LogTag.PDF_SERVICE, "Image file not found: ${imageFile.absolutePath}")
                        continue
                    }

                    callback?.onProgress(index + 1, totalImages, "处理图片 ${index + 1}/$totalImages")

                    // 加载图片
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap == null) {
                        AppLog.w(LogTag.PDF_SERVICE, "Failed to decode image: ${imageFile.absolutePath}")
                        continue
                    }

                    AppLog.d(LogTag.PDF_SERVICE, "Processing image: ${imageFile.name}, size: ${bitmap.width}x${bitmap.height}")

                    // 创建PDF页面（使用图片实际尺寸）
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width,
                        bitmap.height,
                        currentPageIndex + 1
                    ).create()

                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas

                    // 绘制图片到页面
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                    pdfDocument.finishPage(page)

                    currentPageIndex++
                    AppLog.d(LogTag.PDF_SERVICE, "Added page ${currentPageIndex}: ${imageFile.name}")

                    // 回收bitmap
                    bitmap.recycle()
                } catch (e: Exception) {
                    AppLog.e(LogTag.PDF_SERVICE, "Failed to add image to PDF: ${imageFile.name}", e)
                }
            }

            if (currentPageIndex == 0) {
                pdfDocument.close()
                AppLog.e(LogTag.PDF_SERVICE, "No valid images to generate PDF")
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

            AppLog.i(LogTag.PDF_SERVICE, "PDF generated successfully: ${pdfFile.absolutePath}, size: ${pdfFile.length()} bytes")
            callback?.onComplete(pdfFile.absolutePath)

            Result.success(pdfFile.absolutePath)
        } catch (e: Exception) {
            AppLog.e(LogTag.PDF_SERVICE, "Failed to generate PDF for project: $projectName", e)
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
