package com.example.docscanner.data.scanproject

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 扫描件项目状态
 */
enum class ProjectStatus {
    CREATING,       // 创建中（输入名称阶段）
    SCANNING,       // 扫描中（添加图片阶段）
    COMPLETED,      // 扫描完成（可以导出）
    OCR_PROCESSING, // OCR处理中
    OCR_PAUSED,     // OCR暂停（出错后暂停）
    OCR_COMPLETED,  // OCR完成
    EXPORTED_PDF,   // 已导出PDF
    EXPORTED_TEXT   // 已导出文本
}

/**
 * 扫描件项目实体
 * 以扫描件名称为唯一标识
 */
@Entity(tableName = "scan_projects")
data class ScanProjectEntity(
    @PrimaryKey
    val name: String,                   // 扫描件名称（唯一ID）
    val status: String = ProjectStatus.CREATING.name,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val imageCount: Int = 0,            // 图片总数
    val ocrProgress: Int = 0,           // OCR已处理的图片数
    val lastOcrError: String? = null,   // 最后一次OCR错误信息
    val pdfPath: String? = null,        // 生成的PDF路径
    val textPath: String? = null        // 生成的文本文件路径
)
