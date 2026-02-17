package com.example.docscanner

import android.app.Application
import com.example.docscanner.data.config.ConfigRepository
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用程序入口类
 * 负责初始化配置和加载数据
 */
class DocScannerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.i(LogTag.APP, "DocScanner Application starting...")

        // 初始化配置仓库并加载配置
        val configRepository = ConfigRepository.getInstance(this)
        applicationScope.launch {
            try {
                configRepository.loadConfigsToAdapters()
                AppLog.i(LogTag.APP, "Configurations loaded successfully")
            } catch (e: Exception) {
                AppLog.e(LogTag.APP, "Failed to load configurations: ${e.message}", e)
            }
        }
    }
}
