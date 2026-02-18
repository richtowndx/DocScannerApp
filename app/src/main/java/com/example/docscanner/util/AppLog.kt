package com.example.docscanner.util

import android.util.Log
import com.example.docscanner.BuildConfig

/**
 * 统一日志工具类
 * Debug 版本输出日志，Release 版本不输出
 */
object AppLog {
    private const val APP_TAG = "DocScanner"

    // 使用 BuildConfig.DEBUG 自动判断是否是 debug 版本
    // Debug 编译时为 true，Release 编译时为 false
    private val isDebug: Boolean
        get() = BuildConfig.DEBUG

    private fun formatTag(tag: String): String {
        return "$APP_TAG-$tag"
    }

    fun d(tag: String, message: String) {
        if (isDebug) {
            Log.d(formatTag(tag), message)
        }
    }

    fun i(tag: String, message: String) {
        if (isDebug) {
            Log.i(formatTag(tag), message)
        }
    }

    fun w(tag: String, message: String) {
        if (isDebug) {
            Log.w(formatTag(tag), message)
        }
    }

    fun e(tag: String, message: String) {
        // 错误日志在 release 版本也输出（可选，根据需求决定）
        // 如果完全不输出，改为 if (isDebug)
        if (isDebug) {
            Log.e(formatTag(tag), message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        if (isDebug) {
            Log.e(formatTag(tag), message, throwable)
        }
    }

    fun v(tag: String, message: String) {
        if (isDebug) {
            Log.v(formatTag(tag), message)
        }
    }
}

/**
 * 常用 TAG 定义
 */
object LogTag {
    const val APP = "App"
    const val SCANNER_MLKIT = "MLKit"
    const val OCR_MLKIT = "OCR-MLKit"
    const val OCR_TESSERACT = "OCR-Tess"
    const val OCR_BAIDU = "OCR-Baidu"
    const val OCR_GLM = "OCR-GLM"
    const val OCR_SILICONFLOW = "OCR-SiliconFlow"
    const val UI_HOME = "UI-Home"
    const val UI_SCANNER = "UI-Scanner"
    const val UI_PREVIEW = "UI-Preview"
    const val UI_OCR_RESULT = "UI-OCR"
    const val UI_SETTINGS = "UI-Settings"
    const val UI_MAIN = "UI-Main"
    const val CAMERA = "Camera"
    const val PERMISSION = "Permission"
    const val NAVIGATION = "Navigation"
    const val GENERAL = "General"

    // 扫描件项目相关
    const val SCAN_PROJECT = "ScanProject"
    const val SCAN_PROJECT_UI = "ScanProject-UI"
    const val SCAN_PROJECT_SERVICE = "ScanProject-Service"
    const val PDF_SERVICE = "PDF-Service"
    const val OCR_BATCH = "OCR-Batch"
    const val FILE_STORAGE = "FileStorage"
}
