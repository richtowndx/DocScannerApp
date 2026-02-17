package com.example.docscanner.data.scanproject

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 扫描件项目数据访问对象
 */
@Dao
interface ScanProjectDao {

    @Query("SELECT * FROM scan_projects ORDER BY updatedAt DESC")
    fun getAllProjectsFlow(): Flow<List<ScanProjectEntity>>

    @Query("SELECT * FROM scan_projects ORDER BY updatedAt DESC")
    suspend fun getAllProjects(): List<ScanProjectEntity>

    @Query("SELECT * FROM scan_projects WHERE name = :name")
    suspend fun getProjectByName(name: String): ScanProjectEntity?

    @Query("SELECT * FROM scan_projects WHERE name = :name")
    fun getProjectByNameFlow(name: String): Flow<ScanProjectEntity?>

    @Query("SELECT * FROM scan_projects WHERE status = :status")
    suspend fun getProjectsByStatus(status: String): List<ScanProjectEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ScanProjectEntity): Long

    @Update
    suspend fun updateProject(project: ScanProjectEntity)

    @Delete
    suspend fun deleteProject(project: ScanProjectEntity)

    @Query("DELETE FROM scan_projects WHERE name = :name")
    suspend fun deleteProjectByName(name: String)

    @Query("UPDATE scan_projects SET imageCount = imageCount + 1, updatedAt = :updatedAt WHERE name = :name")
    suspend fun incrementImageCount(name: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE scan_projects SET status = :status, updatedAt = :updatedAt WHERE name = :name")
    suspend fun updateStatus(name: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE scan_projects SET ocrProgress = :progress, updatedAt = :updatedAt WHERE name = :name")
    suspend fun updateOcrProgress(name: String, progress: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE scan_projects SET lastOcrError = :error, updatedAt = :updatedAt WHERE name = :name")
    suspend fun updateOcrError(name: String, error: String?, updatedAt: Long = System.currentTimeMillis())

    @Transaction
    suspend fun createProject(name: String): ScanProjectEntity {
        val project = ScanProjectEntity(
            name = name,
            status = ProjectStatus.SCANNING.name
        )
        insertProject(project)
        return project
    }
}
