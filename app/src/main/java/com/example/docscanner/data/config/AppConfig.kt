package com.example.docscanner.data.config

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用配置实体
 * 所有配置项持久化存储到 SQLite 数据库
 */
@Entity(tableName = "app_config")
data class AppConfig(
    @PrimaryKey
    val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 配置键常量
 */
object ConfigKey {
    // OCR 引擎选择
    const val OCR_ENGINE = "ocr_engine"

    // 百度 OCR 配置
    const val BAIDU_API_KEY = "baidu_api_key"
    const val BAIDU_SECRET_KEY = "baidu_secret_key"

    // 智谱 GLM-OCR 配置
    const val GLM_API_KEY = "glm_api_key"

    // SiliconFlow OCR 配置
    const val SILICONFLOW_API_KEY = "siliconflow_api_key"
    const val SILICONFLOW_MODEL = "siliconflow_model"

    // 扫描引擎选择
    const val SCANNER_ENGINE = "scanner_engine"

    // 导出目录配置（PDF 和 文本共用）
    const val EXPORT_DIRECTORY_URI = "export_directory_uri"
}
