package com.example.docscanner.data.scanproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 扫描件项目服务
 * 负责管理扫描件的完整生命周期
 */
class ScanProjectService private constructor(private val context: Context) {

    private val repository = ScanProjectRepository.getInstance(context)

    // 项目级别的互斥锁，防止并发添加图片时的竞态条件
    private val projectMutexes = ConcurrentHashMap<String, Mutex>()

    // 获取或创建项目互斥锁
    private fun getProjectMutex(projectName: String): Mutex {
        return projectMutexes.computeIfAbsent(projectName) { Mutex() }
    }

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
     * 使用项目级别的互斥锁来防止并发竞态条件
     * @param projectName 项目名称
     * @param bitmap 图片Bitmap
     * @return 保存的图片文件路径
     */
    suspend fun addImageToProject(
        projectName: String,
        bitmap: Bitmap
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "=== addImageToProject START ===")
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Project: $projectName")
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Bitmap: ${bitmap.width}x${bitmap.height}, config=${bitmap.config}")

            repository.getProjectByName(projectName)
                ?: return@withContext Result.failure(IllegalArgumentException("项目不存在: $projectName"))

            // 使用项目级别的互斥锁来确保页码分配和文件保存的原子性
            val mutex = getProjectMutex(projectName)
            val (imageFile, addedImage) = mutex.withLock {
                // === 临界区开始：页码分配和文件保存必须原子执行 ===

                // 获取当前页码（在锁内查询，确保不会被其他协程干扰）
                val images = repository.getImagesByProject(projectName)
                val currentCount = images.size
                val nextPageNumber = (images.maxOfOrNull { it.pageNumber } ?: 0) + 1

                AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Current images in DB: $currentCount, nextPageNumber: $nextPageNumber [LOCKED]")

                // 生成文件名：0001.png, 0002.png...
                val fileName = String.format("%04d.png", nextPageNumber)
                val projectDir = getProjectDirectory(projectName)
                val imageFile = File(projectDir, fileName)

                AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Target file: ${imageFile.absolutePath}")
                AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Project dir exists: ${projectDir.exists()}")

                // 保存图片
                FileOutputStream(imageFile).use { out ->
                    val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    if (!success) {
                        AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Bitmap.compress returned false")
                        throw IOException("图片压缩失败")
                    }
                    out.flush()
                }

                // 验证文件是否正确保存
                if (!imageFile.exists()) {
                    AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Image file not created: ${imageFile.absolutePath}")
                    throw IOException("图片文件创建失败")
                }

                val savedFileSize = imageFile.length()
                AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "File saved: ${imageFile.name}, size: $savedFileSize bytes")

                if (savedFileSize == 0L) {
                    AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Image file is empty: ${imageFile.absolutePath}")
                    imageFile.delete()
                    throw IOException("图片文件为空")
                }

                // 验证图片是否可以正确读取
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(imageFile.absolutePath, options)
                if (options.outWidth <= 0 || options.outHeight <= 0) {
                    AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Saved image is corrupted: ${imageFile.absolutePath}")
                    imageFile.delete()
                    throw IOException("保存的图片已损坏")
                }

                // 添加到数据库（在锁内执行，确保页码和文件路径一致）
                val addResult = repository.addImageToProject(projectName, imageFile.absolutePath)
                val addedImage = addResult.getOrThrow()

                AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "DB record created: id=${addedImage.id}, page=${addedImage.pageNumber}, path=${addedImage.filePath}")

                // === 临界区结束 ===
                Pair(imageFile, addedImage)
            }

            // 验证数据库记录数量（锁外执行，不影响性能）
            val newCount = repository.getImagesByProject(projectName).size
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Total images in DB after add: $newCount")
            AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "=== addImageToProject SUCCESS: page=${addedImage.pageNumber}, file=${imageFile.name} ===")

            Result.success(imageFile.absolutePath)
        } catch (e: Exception) {
            AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "=== addImageToProject FAILED ===", e)
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
     * 批量添加图片（高性能版本）
     *
     * 优化策略：
     * 1. 并行预加载所有图片的Bitmap（利用多核CPU）
     * 2. 按顺序保存文件（保证页码顺序正确）
     * 3. 最小化临界区范围（只在页码分配时加锁）
     *
     * @param projectName 项目名称
     * @param uris 图片URI列表（按原始顺序）
     * @return 成功添加的数量和失败数量的Pair
     */
    suspend fun addImagesFromUris(
        projectName: String,
        uris: List<android.net.Uri>
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "=== addImagesFromUris START: ${uris.size} images ===")
        val startTime = System.currentTimeMillis()

        // 验证项目存在
        repository.getProjectByName(projectName)
            ?: return@withContext Pair(0, uris.size).also {
                AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "Project not found: $projectName")
            }

        // === 阶段1：并行预加载所有Bitmap（这是最耗时的操作）===
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Phase 1: Parallel loading ${uris.size} bitmaps...")
        val loadStartTime = System.currentTimeMillis()

        val loadedBitmaps = uris.mapIndexed { index, uri ->
            // 使用 async 并行加载
            async(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        AppLog.w(LogTag.SCAN_PROJECT_SERVICE, "[$index] Failed to open URI: $uri")
                        return@async index to null
                    }
                    val bytes = inputStream.use { it.readBytes() }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bitmap == null) {
                        AppLog.w(LogTag.SCAN_PROJECT_SERVICE, "[$index] Failed to decode bitmap: $uri")
                    }
                    index to bitmap
                } catch (e: Exception) {
                    AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "[$index] Error loading URI: $uri", e)
                    index to null
                }
            }
        }.awaitAll()

        val loadTime = System.currentTimeMillis() - loadStartTime
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Phase 1 complete: ${loadedBitmaps.size} bitmaps loaded in ${loadTime}ms")

        // === 阶段2：按顺序保存文件（串行，但临界区最小化）===
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Phase 2: Sequential saving...")

        val mutex = getProjectMutex(projectName)
        var successCount = 0
        var failCount = 0

        // 按原始索引顺序处理，保证页码顺序
        for ((index, bitmap) in loadedBitmaps.sortedBy { it.first }) {
            if (bitmap == null) {
                failCount++
                continue
            }

            try {
                // 临界区：只包含页码分配和文件保存
                mutex.withLock {
                    // 获取下一个页码
                    val images = repository.getImagesByProject(projectName)
                    val nextPageNumber = (images.maxOfOrNull { it.pageNumber } ?: 0) + 1

                    // 生成文件名并保存
                    val fileName = String.format("%04d.png", nextPageNumber)
                    val projectDir = getProjectDirectory(projectName)
                    val imageFile = File(projectDir, fileName)

                    // 保存图片
                    FileOutputStream(imageFile).use { out ->
                        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw IOException("图片压缩失败")
                        }
                        out.flush()
                    }

                    // 添加到数据库
                    repository.addImageToProject(projectName, imageFile.absolutePath)

                    successCount++
                    AppLog.d(LogTag.SCAN_PROJECT_SERVICE, "[$index] Saved: $fileName")
                }
            } catch (e: Exception) {
                failCount++
                AppLog.e(LogTag.SCAN_PROJECT_SERVICE, "[$index] Failed to save image", e)
            } finally {
                // 及时回收Bitmap释放内存
                bitmap.recycle()
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "=== addImagesFromUris COMPLETE: $successCount success, $failCount failed, ${totalTime}ms ===")

        Pair(successCount, failCount)
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
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "=== getProjectImagesSorted START for project: $projectName ===")

        val images = repository.getImagesByProject(projectName)
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "DB returned ${images.size} image records")

        // 详细打印每条记录
        images.forEachIndexed { index, img ->
            AppLog.d(LogTag.SCAN_PROJECT_SERVICE, "Image[$index]: id=${img.id}, page=${img.pageNumber}, path=${img.filePath}")
        }

        val sortedImages = images.sortedBy { it.pageNumber }
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Sorted images by pageNumber")

        val files = sortedImages.map { File(it.filePath) }
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Converted to ${files.size} File objects")

        // 检查每个文件是否存在
        val existingFiles = mutableListOf<File>()
        val missingFiles = mutableListOf<Pair<Int, String>>()

        files.forEachIndexed { index, file ->
            val exists = file.exists()
            AppLog.d(LogTag.SCAN_PROJECT_SERVICE, "File[$index]: ${file.absolutePath}, exists=$exists, length=${if (exists) file.length() else "N/A"}")
            if (exists) {
                existingFiles.add(file)
            } else {
                missingFiles.add(index to file.absolutePath)
            }
        }

        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "=== FILE EXISTENCE SUMMARY ===")
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Existing files: ${existingFiles.size}")
        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "Missing files: ${missingFiles.size}")

        if (missingFiles.isNotEmpty()) {
            AppLog.w(LogTag.SCAN_PROJECT_SERVICE, "=== MISSING FILES DETAIL ===")
            missingFiles.forEach { (index, path) ->
                AppLog.w(LogTag.SCAN_PROJECT_SERVICE, "Missing[$index]: $path")
            }
        }

        AppLog.i(LogTag.SCAN_PROJECT_SERVICE, "=== getProjectImagesSorted END, returning ${existingFiles.size} files ===")
        return existingFiles
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
