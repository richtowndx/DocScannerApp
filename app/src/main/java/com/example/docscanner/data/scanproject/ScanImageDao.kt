package com.example.docscanner.data.scanproject

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 扫描图片数据访问对象
 */
@Dao
interface ScanImageDao {

    @Query("SELECT * FROM scan_images WHERE projectName = :projectName ORDER BY pageNumber ASC")
    fun getImagesByProjectFlow(projectName: String): Flow<List<ScanImageEntity>>

    @Query("SELECT * FROM scan_images WHERE projectName = :projectName ORDER BY pageNumber ASC")
    suspend fun getImagesByProject(projectName: String): List<ScanImageEntity>

    @Query("SELECT * FROM scan_images WHERE id = :id")
    suspend fun getImageById(id: Long): ScanImageEntity?

    @Query("SELECT * FROM scan_images WHERE projectName = :projectName AND pageNumber = :pageNumber")
    suspend fun getImageByPageNumber(projectName: String, pageNumber: Int): ScanImageEntity?

    @Query("SELECT MAX(pageNumber) FROM scan_images WHERE projectName = :projectName")
    suspend fun getMaxPageNumber(projectName: String): Int?

    @Query("SELECT COUNT(*) FROM scan_images WHERE projectName = :projectName")
    suspend fun getImageCount(projectName: String): Int

    @Query("SELECT COUNT(*) FROM scan_images WHERE projectName = :projectName AND ocrStatus = :status")
    suspend fun getImageCountByStatus(projectName: String, status: String): Int

    @Query("SELECT * FROM scan_images WHERE projectName = :projectName AND ocrStatus = :status ORDER BY pageNumber ASC LIMIT 1")
    suspend fun getFirstImageByStatus(projectName: String, status: String): ScanImageEntity?

    @Query("SELECT * FROM scan_images WHERE projectName = :projectName AND ocrStatus IN ('PENDING', 'FAILED') ORDER BY pageNumber ASC")
    suspend fun getPendingOrFailedImages(projectName: String): List<ScanImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ScanImageEntity): Long

    @Update
    suspend fun updateImage(image: ScanImageEntity)

    @Delete
    suspend fun deleteImage(image: ScanImageEntity)

    @Query("DELETE FROM scan_images WHERE projectName = :projectName")
    suspend fun deleteImagesByProject(projectName: String)

    @Query("DELETE FROM scan_images WHERE id = :id")
    suspend fun deleteImageById(id: Long)

    @Transaction
    suspend fun addImageToProject(projectName: String, filePath: String): ScanImageEntity {
        val maxPage = getMaxPageNumber(projectName) ?: 0
        val newPageNumber = maxPage + 1
        val image = ScanImageEntity(
            projectName = projectName,
            pageNumber = newPageNumber,
            filePath = filePath
        )
        insertImage(image)
        return image
    }

    @Query("UPDATE scan_images SET ocrStatus = :status, ocrText = :text, ocrError = :error WHERE id = :id")
    suspend fun updateOcrStatus(id: Long, status: String, text: String? = null, error: String? = null)

    @Query("UPDATE scan_images SET ocrRetryCount = ocrRetryCount + 1 WHERE id = :id")
    suspend fun incrementRetryCount(id: Long)
}
