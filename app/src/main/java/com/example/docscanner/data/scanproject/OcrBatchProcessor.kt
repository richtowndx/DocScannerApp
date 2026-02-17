package com.example.docscanner.data.scanproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.docscanner.data.config.ConfigRepository
import com.example.docscanner.ocr.OCRAdapter
import com.example.docscanner.ocr.OCRConfig
import com.example.docscanner.ocr.OCRFactory
import com.example.docscanner.ocr.OCREngine
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * OCR批处理服务
 * 支持重试机制和进度恢复
 */
class OcrBatchProcessor private constructor(private val context: Context) {

    private val repository = ScanProjectRepository.getInstance(context)
    private val configRepository = ConfigRepository.getInstance(context)
    private val scanProjectService = ScanProjectService.getInstance(context)

    // 最大重试次数
    private val MAX_RETRY_COUNT = 5

    /**
     * OCR处理进度回调
     */
    interface ProgressCallback {
        fun onProgress(current: Int, total: Int, imageName: String)
        fun onImageComplete(imageId: Long, pageNumber: Int, success: Boolean)
        fun onProjectComplete(projectName: String, successCount: Int, failedCount: Int)
        fun onError(projectName: String, error: String)
    }

    /**
     * 对项目执行OCR批处理
     * @param projectName 项目名称
     * @param callback 进度回调
     * @param forceRestart 是否强制从头开始（忽略之前的进度）
     */
    suspend fun processProject(
        projectName: String,
        callback: ProgressCallback? = null,
        forceRestart: Boolean = false
    ): Result<OcrBatchResult> = withContext(Dispatchers.IO) {
        try {
            AppLog.i(LogTag.OCR_BATCH, "Starting OCR batch processing for project: $projectName")

            repository.getProjectByName(projectName)
                ?: return@withContext Result.failure(IllegalArgumentException("项目不存在: $projectName"))

            // 更新项目状态
            repository.updateProjectStatus(projectName, ProjectStatus.OCR_PROCESSING)

            // 获取待处理的图片
            var images = if (forceRestart) {
                // 重置所有图片状态
                repository.getImagesByProject(projectName).forEach { image ->
                    repository.updateImageOcrStatus(image.id, ImageOcrStatus.PENDING)
                }
                repository.getImagesByProject(projectName)
            } else {
                repository.getPendingOrFailedImages(projectName)
            }

            if (images.isEmpty()) {
                // 检查是否所有图片都已完成
                val allImages = repository.getImagesByProject(projectName)
                val completedCount = allImages.count { it.ocrStatus == ImageOcrStatus.COMPLETED.name }

                if (completedCount == allImages.size && allImages.isNotEmpty()) {
                    repository.updateProjectStatus(projectName, ProjectStatus.OCR_COMPLETED)
                    return@withContext Result.success(
                        OcrBatchResult(
                            totalImages = allImages.size,
                            successCount = completedCount,
                            failedCount = 0
                        )
                    )
                }

                return@withContext Result.failure(IllegalArgumentException("没有待处理的图片"))
            }

            val totalImages = repository.getImageCount(projectName)
            var successCount = images.count { it.ocrStatus == ImageOcrStatus.COMPLETED.name }
            var failedCount = 0

            // 获取当前配置的OCR引擎
            val ocrEngine = configRepository.getOCREngine()
            val ocrAdapter = OCRFactory.getAdapter(ocrEngine, context)

            // 初始化OCR引擎
            val initialized = ocrAdapter.initialize()
            if (!initialized) {
                val error = "OCR引擎初始化失败: $ocrEngine"
                AppLog.e(LogTag.OCR_BATCH, error)
                callback?.onError(projectName, error)
                repository.updateProjectStatus(projectName, ProjectStatus.OCR_PAUSED)
                repository.updateOcrError(projectName, error)
                return@withContext Result.failure(IllegalStateException(error))
            }

            AppLog.i(LogTag.OCR_BATCH, "Using OCR engine: $ocrEngine")

            // 按页码排序处理
            images = images.sortedBy { it.pageNumber }

            for (image in images) {
                // 检查是否已达到最大重试次数
                if (image.ocrRetryCount >= MAX_RETRY_COUNT) {
                    AppLog.w(LogTag.OCR_BATCH, "Image ${image.pageNumber} reached max retry count")
                    failedCount++
                    continue
                }

                callback?.onProgress(
                    image.pageNumber,
                    totalImages,
                    String.format("%04d.png", image.pageNumber)
                )

                // 更新状态为处理中
                repository.updateImageOcrStatus(image.id, ImageOcrStatus.PROCESSING)

                // 执行OCR
                val result = processImage(image, ocrAdapter, ocrEngine)

                if (result.isSuccess) {
                    // 保存OCR文本
                    repository.updateImageOcrStatus(
                        image.id,
                        ImageOcrStatus.COMPLETED,
                        text = result.getOrNull()
                    )
                    successCount++
                    callback?.onImageComplete(image.id, image.pageNumber, true)
                    AppLog.i(LogTag.OCR_BATCH, "OCR completed for image ${image.pageNumber}")
                } else {
                    // 更新重试计数和错误
                    repository.incrementRetryCount(image.id)
                    repository.updateImageOcrStatus(
                        image.id,
                        ImageOcrStatus.FAILED,
                        error = result.exceptionOrNull()?.message
                    )
                    failedCount++
                    callback?.onImageComplete(image.id, image.pageNumber, false)
                    AppLog.e(
                        LogTag.OCR_BATCH,
                        "OCR failed for image ${image.pageNumber}: ${result.exceptionOrNull()?.message}"
                    )

                    // 检查是否需要暂停（连续失败）
                    val recentFailures = getRecentFailureCount(projectName)
                    if (recentFailures >= 3) {
                        val error = "连续失败${recentFailures}次，暂停OCR处理"
                        AppLog.w(LogTag.OCR_BATCH, error)
                        callback?.onError(projectName, error)
                        repository.updateProjectStatus(projectName, ProjectStatus.OCR_PAUSED)
                        repository.updateOcrError(projectName, error)
                        return@withContext Result.failure(IllegalStateException(error))
                    }
                }

                // 更新项目OCR进度
                repository.updateOcrProgress(projectName, successCount)
            }

            // 检查是否全部完成
            if (failedCount == 0) {
                repository.updateProjectStatus(projectName, ProjectStatus.OCR_COMPLETED)
                repository.updateOcrError(projectName, null)
            } else {
                repository.updateProjectStatus(projectName, ProjectStatus.OCR_PAUSED)
            }

            callback?.onProjectComplete(projectName, successCount, failedCount)
            AppLog.i(
                LogTag.OCR_BATCH,
                "OCR batch completed: $successCount success, $failedCount failed"
            )

            Result.success(OcrBatchResult(totalImages, successCount, failedCount))
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_BATCH, "OCR batch processing failed for project: $projectName", e)
            callback?.onError(projectName, e.message ?: "未知错误")
            repository.updateProjectStatus(projectName, ProjectStatus.OCR_PAUSED)
            repository.updateOcrError(projectName, e.message)
            Result.failure(e)
        }
    }

    /**
     * 处理单张图片
     */
    private suspend fun processImage(
        image: ScanImageEntity,
        ocrAdapter: OCRAdapter,
        ocrEngine: OCREngine
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查文件是否存在
            val imageFile = File(image.filePath)
            if (!imageFile.exists()) {
                AppLog.e(LogTag.OCR_BATCH, "Image file not found: ${image.filePath}")
                return@withContext Result.failure(IOException("图片文件不存在: ${image.filePath}"))
            }

            // 加载图片
            val bitmap = BitmapFactory.decodeFile(image.filePath)
            if (bitmap == null) {
                AppLog.e(LogTag.OCR_BATCH, "Failed to decode image: ${image.filePath}")
                return@withContext Result.failure(IOException("无法解码图片: ${image.filePath}"))
            }

            AppLog.d(LogTag.OCR_BATCH, "Processing image: ${image.filePath}, size: ${bitmap.width}x${bitmap.height}")

            // 重新获取OCR配置（每次都从配置中获取最新的）
            val ocrConfig = OCRConfig(
                engine = ocrEngine,
                language = "chi_sim+eng",
                enableMarkdown = true
            )

            // 执行OCR
            val ocrResult = ocrAdapter.recognizeText(bitmap, ocrConfig)

            // 回收bitmap
            bitmap.recycle()

            Result.success(ocrResult.markdownText)
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_BATCH, "Failed to process image: ${image.filePath}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取最近的失败次数
     */
    private suspend fun getRecentFailureCount(projectName: String): Int {
        val images = repository.getImagesByProject(projectName)
        return images.count { it.ocrStatus == ImageOcrStatus.FAILED.name }
    }

    /**
     * 生成Markdown文本文件
     */
    suspend fun generateMarkdownFile(projectName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val images = repository.getImagesByProject(projectName)
                .filter { it.ocrStatus == ImageOcrStatus.COMPLETED.name && !it.ocrText.isNullOrBlank() }
                .sortedBy { it.pageNumber }

            if (images.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("没有已完成的OCR文本"))
            }

            val projectDir = scanProjectService.getProjectDirectory(projectName)
            val markdownFile = File(projectDir, "$projectName.md")

            FileOutputStream(markdownFile).use { out ->
                // 写入标题
                out.write("# $projectName\n\n".toByteArray(Charsets.UTF_8))

                // 写入每页内容
                for (image in images) {
                    out.write("---\n\n".toByteArray(Charsets.UTF_8))
                    out.write("## 第 ${image.pageNumber} 页\n\n".toByteArray(Charsets.UTF_8))
                    out.write((image.ocrText ?: "").toByteArray(Charsets.UTF_8))
                    out.write("\n\n".toByteArray(Charsets.UTF_8))
                }
            }

            AppLog.i(LogTag.OCR_BATCH, "Markdown file generated: ${markdownFile.absolutePath}")
            Result.success(markdownFile)
        } catch (e: Exception) {
            AppLog.e(LogTag.OCR_BATCH, "Failed to generate markdown file for project: $projectName", e)
            Result.failure(e)
        }
    }

    /**
     * 获取OCR进度信息
     */
    suspend fun getOcrProgress(projectName: String): OcrProgressInfo {
        val project = repository.getProjectByName(projectName)
        val images = repository.getImagesByProject(projectName)

        return OcrProgressInfo(
            totalImages = images.size,
            completedCount = images.count { it.ocrStatus == ImageOcrStatus.COMPLETED.name },
            failedCount = images.count { it.ocrStatus == ImageOcrStatus.FAILED.name },
            pendingCount = images.count { it.ocrStatus == ImageOcrStatus.PENDING.name },
            processingCount = images.count { it.ocrStatus == ImageOcrStatus.PROCESSING.name },
            lastError = project?.lastOcrError
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: OcrBatchProcessor? = null

        fun getInstance(context: Context): OcrBatchProcessor {
            return INSTANCE ?: synchronized(this) {
                val instance = OcrBatchProcessor(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * OCR批处理结果
 */
data class OcrBatchResult(
    val totalImages: Int,
    val successCount: Int,
    val failedCount: Int
)

/**
 * OCR进度信息
 */
data class OcrProgressInfo(
    val totalImages: Int,
    val completedCount: Int,
    val failedCount: Int,
    val pendingCount: Int,
    val processingCount: Int,
    val lastError: String?
)
