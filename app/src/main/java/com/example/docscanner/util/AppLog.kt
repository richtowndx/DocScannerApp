package com.example.docscanner.util

import android.util.Log

/**
 * 统一日志工具类
 * 始终输出日志，便于调试
 */
object AppLog {
    private const val APP_TAG = "DocScanner"
    private const val ENABLE_LOG = true  // 始终启用日志

    private fun formatTag(tag: String): String {
        return "$APP_TAG-$tag"
    }

    fun d(tag: String, message: String) {
        if (ENABLE_LOG) {
            Log.d(formatTag(tag), message)
        }
    }

    fun i(tag: String, message: String) {
        if (ENABLE_LOG) {
            Log.i(formatTag(tag), message)
        }
    }

    fun w(tag: String, message: String) {
        if (ENABLE_LOG) {
            Log.w(formatTag(tag), message)
        }
    }

    fun e(tag: String, message: String) {
        Log.e(formatTag(tag), message)
    }

    fun e(tag: String, message: String, throwable: Throwable) {
        Log.e(formatTag(tag), message, throwable)
    }

    fun v(tag: String, message: String) {
        if (ENABLE_LOG) {
            Log.v(formatTag(tag), message)
        }
    }
}

/**
 * 常用 TAG 定义
 */
object LogTag {
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
}
