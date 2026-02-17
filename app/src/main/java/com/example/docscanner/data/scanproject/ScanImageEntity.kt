package com.example.docscanner.data.scanproject

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 图片OCR状态
 */
enum class ImageOcrStatus {
    PENDING,        // 待处理
    PROCESSING,     // 处理中
    COMPLETED,      // 已完成
    FAILED,         // 失败
    SKIPPED         // 跳过
}

/**
 * 扫描图片实体
 */
@Entity(
    tableName = "scan_images",
    foreignKeys = [
        ForeignKey(
            entity = ScanProjectEntity::class,
            parentColumns = ["name"],
            childColumns = ["projectName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectName"]), Index(value = ["projectName", "pageNumber"], unique = true)]
)
data class ScanImageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectName: String,            // 所属扫描件名称
    val pageNumber: Int,                // 页码（0001, 0002...）
    val filePath: String,               // 图片文件路径
    val ocrStatus: String = ImageOcrStatus.PENDING.name,
    val ocrRetryCount: Int = 0,         // OCR重试次数
    val ocrError: String? = null,       // OCR错误信息
    val ocrText: String? = null,        // OCR识别的文本
    val createdAt: Long = System.currentTimeMillis()
)
