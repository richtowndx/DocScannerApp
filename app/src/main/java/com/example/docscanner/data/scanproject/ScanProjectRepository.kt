package com.example.docscanner.data.scanproject

import android.content.Context
import com.example.docscanner.data.config.AppDatabase
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.flow.Flow

/**
 * 扫描件项目仓库
 * 提供统一的数据访问接口
 */
class ScanProjectRepository private constructor(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val projectDao = database.scanProjectDao()
    private val imageDao = database.scanImageDao()

    // ========== 项目操作 ==========

    /**
     * 获取所有项目（Flow）
     */
    fun getAllProjectsFlow(): Flow<List<ScanProjectEntity>> {
        return projectDao.getAllProjectsFlow()
    }

    /**
     * 获取所有项目
     */
    suspend fun getAllProjects(): List<ScanProjectEntity> {
        return projectDao.getAllProjects()
    }

    /**
     * 根据名称获取项目
     */
    suspend fun getProjectByName(name: String): ScanProjectEntity? {
        return projectDao.getProjectByName(name)
    }

    /**
     * 根据名称获取项目（Flow）
     */
    fun getProjectByNameFlow(name: String): Flow<ScanProjectEntity?> {
        return projectDao.getProjectByNameFlow(name)
    }

    /**
     * 创建新项目
     */
    suspend fun createProject(name: String): Result<ScanProjectEntity> {
        return try {
            AppLog.i(LogTag.SCAN_PROJECT, "Creating project: $name")
            val existing = projectDao.getProjectByName(name)
            if (existing != null) {
                AppLog.w(LogTag.SCAN_PROJECT, "Project already exists: $name")
                Result.failure(IllegalArgumentException("项目名称已存在: $name"))
            } else {
                val project = projectDao.createProject(name)
                AppLog.i(LogTag.SCAN_PROJECT, "Project created: $name")
                Result.success(project)
            }
        } catch (e: Exception) {
            AppLog.e(LogTag.SCAN_PROJECT, "Failed to create project: $name", e)
            Result.failure(e)
        }
    }

    /**
     * 更新项目
     */
    suspend fun updateProject(project: ScanProjectEntity) {
        AppLog.d(LogTag.SCAN_PROJECT, "Updating project: ${project.name}")
        projectDao.updateProject(project)
    }

    /**
     * 删除项目（会级联删除所有图片记录）
     */
    suspend fun deleteProject(name: String) {
        AppLog.i(LogTag.SCAN_PROJECT, "Deleting project: $name")
        projectDao.deleteProjectByName(name)
    }

    /**
     * 更新项目状态
     */
    suspend fun updateProjectStatus(name: String, status: ProjectStatus) {
        AppLog.i(LogTag.SCAN_PROJECT, "Updating project status: $name -> $status")
        projectDao.updateStatus(name, status.name)
    }

    /**
     * 更新OCR进度
     */
    suspend fun updateOcrProgress(name: String, progress: Int) {
        projectDao.updateOcrProgress(name, progress)
    }

    /**
     * 更新OCR错误
     */
    suspend fun updateOcrError(name: String, error: String?) {
        projectDao.updateOcrError(name, error)
    }

    // ========== 图片操作 ==========

    /**
     * 获取项目的所有图片（Flow）
     */
    fun getImagesByProjectFlow(projectName: String): Flow<List<ScanImageEntity>> {
        return imageDao.getImagesByProjectFlow(projectName)
    }

    /**
     * 获取项目的所有图片
     */
    suspend fun getImagesByProject(projectName: String): List<ScanImageEntity> {
        return imageDao.getImagesByProject(projectName)
    }

    /**
     * 添加图片到项目
     */
    suspend fun addImageToProject(projectName: String, filePath: String): Result<ScanImageEntity> {
        return try {
            AppLog.i(LogTag.SCAN_PROJECT, "Adding image to project $projectName: $filePath")
            val image = imageDao.addImageToProject(projectName, filePath)
            projectDao.incrementImageCount(projectName)
            AppLog.i(LogTag.SCAN_PROJECT, "Image added: page ${image.pageNumber}")
            Result.success(image)
        } catch (e: Exception) {
            AppLog.e(LogTag.SCAN_PROJECT, "Failed to add image to project: $projectName", e)
            Result.failure(e)
        }
    }

    /**
     * 更新图片
     */
    suspend fun updateImage(image: ScanImageEntity) {
        imageDao.updateImage(image)
    }

    /**
     * 删除图片
     */
    suspend fun deleteImage(image: ScanImageEntity) {
        AppLog.i(LogTag.SCAN_PROJECT, "Deleting image: ${image.pageNumber} from ${image.projectName}")
        imageDao.deleteImage(image)
    }

    /**
     * 获取待处理或失败的图片
     */
    suspend fun getPendingOrFailedImages(projectName: String): List<ScanImageEntity> {
        return imageDao.getPendingOrFailedImages(projectName)
    }

    /**
     * 更新图片OCR状态
     */
    suspend fun updateImageOcrStatus(
        imageId: Long,
        status: ImageOcrStatus,
        text: String? = null,
        error: String? = null
    ) {
        imageDao.updateOcrStatus(imageId, status.name, text, error)
    }

    /**
     * 增加重试计数
     */
    suspend fun incrementRetryCount(imageId: Long) {
        imageDao.incrementRetryCount(imageId)
    }

    /**
     * 获取项目图片数量
     */
    suspend fun getImageCount(projectName: String): Int {
        return imageDao.getImageCount(projectName)
    }

    companion object {
        @Volatile
        private var INSTANCE: ScanProjectRepository? = null

        fun getInstance(context: Context): ScanProjectRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ScanProjectRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
