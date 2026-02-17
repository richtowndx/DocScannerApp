package com.example.docscanner.data.scanproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 扫描件项目服务
 * 负责管理扫描件的完整生命周期
 */
class ScanProjectService private constructor(private val context: Context) {

    private val repository = ScanProjectRepository.getInstance(context)

    // 工作目录：APP私有文件目录
    private val workDirectory: File
        get() = File(context.filesDir, "scan_projects")

    /**
     * 生成默认扫描件名称
     * 格式：扫描件20260216210001
     */
    fun generateDefaultName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return "扫描件${dateFormat.format(Date())}"
    }

    /**
     * 获取项目的工作目录
     */
    fun getProjectDirectory(projectName: String): File {
        return File(workDirectory, projectName)
    }

    /**
     * 创建新的扫描件项目
     */
    suspend fun createProject(name: String? = null): Result<ScanProjectEntity> {
        val projectName = name ?: generateDefaultName()

        // 创建项目目录
        val projectDir = getProjectDirectory(projectName)
        if (!projectDir.exists()) {
            val created = projectDir.mkdirs()
            if (!created) {
                AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Failed to create project directory: $projectDir")
                return Result.failure(IOException("无法创建项目目录"))
            }
        }

        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Project directory created: $projectDir")
        return repository.createProject(projectName)
    }

    /**
     * 获取所有项目
     */
    suspend fun getAllProjects(): List<ScanProjectEntity> {
        return repository.getAllProjects()
    }

    /**
     * 获取项目详情
     */
    suspend fun getProject(name: String): ScanProjectEntity? {
        return repository.getProjectByName(name)
    }

    /**
     * 添加扫描图片到项目
     * @param projectName 项目名称
     * @param bitmap 图片Bitmap
     * @return 保存的图片文件路径
     */
    suspend fun addImageToProject(
        projectName: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Adding image to project: $projectName, bitmap: ${bitmap.width}x${bitmap.height}")

            repository.getProjectByName(projectName)
                ?: return@withContext Result.failure(IllegalArgumentException("项目不存在: $projectName"))

            // 获取当前页码
            val images = repository.getImagesByProject(projectName)
            val nextPageNumber = (images.maxOfOrNull { it.pageNumber } ?: 0) + 1

            // 生成文件名：0001.png, 0002.png...
            val fileName = String.format("%04d.png", nextPageNumber)
            val projectDir = getProjectDirectory(projectName)
            val imageFile = File(projectDir, fileName)

            AppLog.d(LogTag.SCAN_PROJECT_SERVICE, "Saving image to: ${imageFile.absolutePath}")

            // 保存图片
            FileOutputStream(imageFile).use { out ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                if (!success) {
                    AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Bitmap.compress returned false")
                    return@withContext Result.failure(IOException("图片压缩失败"))
                }
                out.flush()
            }

            // 验证文件是否正确保存
            if (!imageFile.exists()) {
                AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Image file not created: ${imageFile.absolutePath}")
                return@withContext Result.failure(IOException("图片文件创建失败"))
            }

            val savedFileSize = imageFile.length()
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Image saved: ${imageFile.absolutePath}, size: $savedFileSize bytes")

            if (savedFileSize == 0L) {
                AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Image file is empty: ${imageFile.absolutePath}")
                return@withContext Result.failure(IOException("图片文件为空"))
            }

            // 验证图片是否可以正确读取
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(imageFile.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) {
                AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Saved image is corrupted: ${imageFile.absolutePath}")
                imageFile.delete()
                return@withContext Result.failure(IOException("保存的图片已损坏"))
            }

            // 添加到数据库
            repository.addImageToProject(projectName, imageFile.absolutePath)

            Result.success(imageFile.absolutePath)
        } catch (e: Exception) {
            AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Failed to add image to project: $projectName", e)
            Result.failure(e)
        }
    }

    /**
     * 从URI添加扫描图片到项目
     */
    suspend fun addImageFromUri(
        projectName: String,
        uri: android.net.Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Adding image from URI: $uri")

            // 从URI读取图片数据到内存（先复制到字节数组，避免流被提前关闭）
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("无法打开图片: $uri"))

            val bytes = inputStream.use { it.readBytes() }
            AppLog.d(LogTag.SCAN_PROJECT_SERVICE, "Read ${bytes.size} bytes from URI")

            if (bytes.isEmpty()) {
                return@withContext Result.failure(IOException("图片数据为空: $uri"))
            }

            // 从字节数组解码图片
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

            if (bitmap == null) {
                AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Failed to decode bitmap from URI: $uri")
                return@withContext Result.failure(IOException("无法解码图片: $uri"))
            }

            AppLog.d(LogTag.SCAN_PROJECT_SERVICE, "Decoded bitmap: ${bitmap.width}x${bitmap.height}")

            val result = addImageToProject(projectName, bitmap)

            // 回收 bitmap
            bitmap.recycle()

            result
        } catch (e: Exception) {
            AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Failed to add image from URI: $uri", e)
            Result.failure(e)
        }
    }

    /**
     * 获取项目的所有图片
     */
    suspend fun getProjectImages(projectName: String): List<ScanImageEntity> {
        return repository.getImagesByProject(projectName)
    }

    /**
     * 获取项目图片（按页码排序）
     */
    suspend fun getProjectImagesSorted(projectName: String): List<File> {
        val images = repository.getImagesByProject(projectName)
        return images
            .sortedBy { it.pageNumber }
            .map { File(it.filePath) }
            .filter { it.exists() }
    }

    /**
     * 更新项目状态
     */
    suspend fun updateProjectStatus(projectName: String, status: ProjectStatus) {
        repository.updateProjectStatus(projectName, status)
    }

    /**
     * 删除项目（包括所有图片文件）
     */
    suspend fun deleteProject(projectName: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 删除项目目录及文件
            val projectDir = getProjectDirectory(projectName)
            if (projectDir.exists()) {
                projectDir.deleteRecursively()
                AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Project directory deleted: $projectDir")
            }

            // 删除数据库记录
            repository.deleteProject(projectName)

            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Failed to delete project: $projectName", e)
            Result.failure(e)
        }
    }

    /**
     * 删除单张图片
     */
    suspend fun deleteImage(image: ScanImageEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 删除文件
            val file = File(image.filePath)
            if (file.exists()) {
                file.delete()
                AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Image file deleted: ${image.filePath}")
            }

            // 删除数据库记录
            repository.deleteImage(image)

            Result.success(Unit)
        } catch (e: Exception) {
            AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Failed to delete image: ${image.filePath}", e)
            Result.failure(e)
        }
    }

    /**
     * 获取工作目录大小
     */
    fun getWorkDirectorySize(): Long {
        return calculateDirectorySize(workDirectory)
    }

    private fun calculateDirectorySize(directory: File): Long {
        if (!directory.exists()) return 0

        var size = 0L
        directory.listFiles()?.forEach { file ->
            size += if (file.isDirectory) {
                calculateDirectorySize(file)
            } else {
                file.length()
            }
        }
        return size
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ScanProjectService? = null

        fun getInstance(context: Context): ScanProjectService {
            return INSTANCE ?: synchronized(this) {
                val instance = ScanProjectService(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
