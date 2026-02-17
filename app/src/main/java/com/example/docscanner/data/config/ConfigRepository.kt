package com.example.docscanner.data.config

import android.content.Context
import com.example.docscanner.ocr.OCREngine
import com.example.docscanner.ocr.SiliconFlowOCRModel
import com.example.docscanner.scanner.ScannerEngine
import kotlinx.coroutines.flow.Flow

/**
 * 配置仓库
 * 提供统一的配置读写接口
 */
class ConfigRepository private constructor(context: Context) {

    private val configDao: ConfigDao = AppDatabase.getDatabase(context).configDao()

    /**
     * 获取配置值
     */
    suspend fun getValue(key: String): String? {
        return configDao.getValue(key)
    }

    /**
     * 设置配置值
     */
    suspend fun setValue(key: String, value: String) {
        configDao.setConfig(AppConfig(key, value))
    }

    /**
     * 监听配置变化
     */
    fun getValueFlow(key: String): Flow<String?> {
        return configDao.getValueFlow(key)
    }

    /**
     * 删除配置
     */
    suspend fun deleteValue(key: String) {
        configDao.deleteConfig(key)
    }

    // ========== OCR 引擎配置 ==========

    /**
     * 获取选中的 OCR 引擎
     */
    suspend fun getOCREngine(): OCREngine {
        val value = getValue(ConfigKey.OCR_ENGINE)
        return try {
            value?.let { OCREngine.valueOf(it) } ?: OCREngine.ML_KIT
        } catch (e: Exception) {
            OCREngine.ML_KIT
        }
    }

    /**
     * 设置 OCR 引擎
     */
    suspend fun setOCREngine(engine: OCREngine) {
        setValue(ConfigKey.OCR_ENGINE, engine.name)
    }

    // ========== 百度 OCR 配置 ==========

    suspend fun getBaiduApiKey(): String? = getValue(ConfigKey.BAIDU_API_KEY)
    suspend fun getBaiduSecretKey(): String? = getValue(ConfigKey.BAIDU_SECRET_KEY)

    suspend fun setBaiduConfig(apiKey: String, secretKey: String) {
        setValue(ConfigKey.BAIDU_API_KEY, apiKey)
        setValue(ConfigKey.BAIDU_SECRET_KEY, secretKey)
    }

    // ========== 智谱 GLM-OCR 配置 ==========

    suspend fun getGLMApiKey(): String? = getValue(ConfigKey.GLM_API_KEY)

    suspend fun setGLMApiKey(apiKey: String) {
        setValue(ConfigKey.GLM_API_KEY, apiKey)
    }

    // ========== SiliconFlow OCR 配置 ==========

    suspend fun getSiliconFlowApiKey(): String? = getValue(ConfigKey.SILICONFLOW_API_KEY)

    suspend fun getSiliconFlowModel(): SiliconFlowOCRModel {
        val value = getValue(ConfigKey.SILICONFLOW_MODEL)
        return try {
            value?.let { SiliconFlowOCRModel.valueOf(it) } ?: SiliconFlowOCRModel.PADDLE_OCR_VL_1_5
        } catch (e: Exception) {
            SiliconFlowOCRModel.PADDLE_OCR_VL_1_5
        }
    }

    suspend fun setSiliconFlowConfig(apiKey: String, model: SiliconFlowOCRModel) {
        setValue(ConfigKey.SILICONFLOW_API_KEY, apiKey)
        setValue(ConfigKey.SILICONFLOW_MODEL, model.name)
    }

    // ========== 扫描引擎配置 ==========

    suspend fun getScannerEngine(): ScannerEngine {
        val value = getValue(ConfigKey.SCANNER_ENGINE)
        return try {
            value?.let { ScannerEngine.valueOf(it) } ?: ScannerEngine.ML_KIT
        } catch (e: Exception) {
            ScannerEngine.ML_KIT
        }
    }

    suspend fun setScannerEngine(engine: ScannerEngine) {
        setValue(ConfigKey.SCANNER_ENGINE, engine.name)
    }

    // ========== 导出目录配置 ==========

    /**
     * 获取导出目录 URI
     */
    suspend fun getExportDirectoryUri(): String? = getValue(ConfigKey.EXPORT_DIRECTORY_URI)

    /**
     * 设置导出目录 URI
     */
    suspend fun setExportDirectoryUri(uri: String) {
        setValue(ConfigKey.EXPORT_DIRECTORY_URI, uri)
    }

    // ========== 批量加载配置 ==========

    /**
     * 加载所有配置到 OCR 适配器
     * 应用启动时调用
     */
    suspend fun loadConfigsToAdapters() {
        // 加载百度 OCR 配置
        val baiduApiKey = getBaiduApiKey()
        val baiduSecretKey = getBaiduSecretKey()
        if (!baiduApiKey.isNullOrBlank() && !baiduSecretKey.isNullOrBlank()) {
            com.example.docscanner.ocr.BaiduOCRAdapter.apply {
                this.apiKey = baiduApiKey
                this.secretKey = baiduSecretKey
            }
        }

        // 加载 GLM-OCR 配置
        val glmApiKey = getGLMApiKey()
        if (!glmApiKey.isNullOrBlank()) {
            com.example.docscanner.ocr.GLMOCRAdapter.apiKey = glmApiKey
        }

        // 加载 SiliconFlow OCR 配置
        val siliconFlowApiKey = getSiliconFlowApiKey()
        val siliconFlowModel = getSiliconFlowModel()
        if (!siliconFlowApiKey.isNullOrBlank()) {
            com.example.docscanner.ocr.SiliconFlowOCRAdapter.apply {
                this.apiKey = siliconFlowApiKey
                this.selectedModel = siliconFlowModel
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ConfigRepository? = null

        fun getInstance(context: Context): ConfigRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = ConfigRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
