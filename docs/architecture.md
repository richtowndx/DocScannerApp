# 统一适配层架构设计

## 概述

本文档描述了文档扫描App的统一适配层架构，支持多种扫描引擎和OCR引擎的无缝切换。

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      UI Layer (Compose)                      │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────────────┐ │
│  │ Home    │  │Scanner  │  │Preview  │  │ OCR Result      │ │
│  │ Screen  │  │Screen   │  │Screen   │  │ Screen          │ │
│  └────┬────┘  └────┬────┘  └────┬────┘  └────────┬────────┘ │
└───────┼────────────┼────────────┼────────────────┼──────────┘
        │            │            │                │
        ▼            ▼            ▼                ▼
┌─────────────────────────────────────────────────────────────┐
│                    ViewModel / State                         │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           ScannerState / OCRState                    │    │
│  └─────────────────────────────────────────────────────┘    │
└────────────────────────────┬────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    Adapter Layer (统一接口)                   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           DocumentScannerAdapter (interface)          │   │
│  │  - detectDocumentEdges()                              │   │
│  │  - applyPerspectiveCorrection()                       │   │
│  │  - applyImageProcessing()                             │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ MLKitScanner    │  │ OpenCVScanner   │  │SmartCropper │  │
│  │ Adapter         │  │ Adapter         │  │Adapter      │  │
│  └────────┬────────┘  └────────┬────────┘  └──────┬──────┘  │
└───────────┼─────────────────────┼──────────────────┼─────────┘
            │                     │                  │
            ▼                     ▼                  ▼
┌─────────────────────────────────────────────────────────────┐
│                    Engine Layer (实现)                        │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ Google ML Kit   │  │ OpenCV4Android  │  │SmartCropper │  │
│  │ DocumentScanner │  │                 │  │ Library     │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    OCRAdapter (interface)              │   │
│  │  - recognizeText()                                    │   │
│  │  - convertToMarkdown()                                │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────┐  │
│  │ ML Kit OCR      │  │ Tesseract OCR   │  │ Baidu OCR   │  │
│  │ Adapter         │  │ Adapter         │  │ Adapter     │  │
│  └─────────────────┘  └─────────────────┘  └─────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

## 核心接口定义

### ScannerAdapter 接口

```kotlin
interface DocumentScannerAdapter {
    val engineType: ScannerEngine

    suspend fun isAvailable(): Boolean
    suspend fun prepareScannerIntent(context: Context, config: ScannerConfig): Any
    suspend fun processScanResult(resultData: Any): List<ScanResult>
    suspend fun detectDocumentEdges(bitmap: Bitmap): List<PointF>?
    suspend fun applyPerspectiveCorrection(bitmap: Bitmap, corners: List<PointF>): Bitmap
    suspend fun applyImageProcessing(bitmap: Bitmap, options: ProcessOptions): Bitmap
}
```

### OCRAdapter 接口

```kotlin
interface OCRAdapter {
    val engineType: OCREngine

    suspend fun isAvailable(): Boolean
    suspend fun initialize(): Boolean
    suspend fun recognizeText(bitmap: Bitmap, config: OCRConfig): OCRResult
}
```

### 工厂类

```kotlin
object ScannerFactory {
    fun getAdapter(engine: ScannerEngine): DocumentScannerAdapter
    suspend fun getBestAvailableEngine(): ScannerEngine
}

object OCRFactory {
    fun getAdapter(engine: OCREngine): OCRAdapter
    suspend fun getBestAvailableEngine(): OCREngine
}
```

## 数据流

### 扫描流程

```
1. 用户选择引擎 -> ScannerFactory.getAdapter(engine)
2. 启动扫描     -> adapter.prepareScannerIntent()
3. 获取结果     -> adapter.processScanResult()
4. 预览处理     -> adapter.applyImageProcessing()
5. 保存/分享    -> Export to JPEG/PDF
```

### OCR流程

```
1. 获取图像      -> 从扫描结果或相册
2. 选择OCR引擎   -> OCRFactory.getAdapter(engine)
3. 执行识别      -> adapter.recognizeText()
4. 格式化输出    -> OCRResult.markdownText
5. 复制/导出     -> Copy to clipboard / Save as .md
```

## 引擎选择策略

```kotlin
suspend fun selectBestEngine(): ScannerEngine {
    // 优先级: ML Kit > SmartCropper > OpenCV
    val priority = listOf(
        ScannerEngine.ML_KIT,
        ScannerEngine.SMART_CROPPER,
        ScannerEngine.OPENCV_CUSTOM
    )

    for (engine in priority) {
        if (ScannerFactory.getAdapter(engine).isAvailable()) {
            return engine
        }
    }

    return ScannerEngine.ML_KIT // 默认
}
```

## 配置管理

```kotlin
data class AppConfig(
    val scannerEngine: ScannerEngine = ScannerEngine.ML_KIT,
    val ocrEngine: OCREngine = OCREngine.ML_KIT,
    val processOptions: ProcessOptions = ProcessOptions(),
    val ocrConfig: OCRConfig = OCRConfig()
)

// 使用 DataStore 持久化
class ConfigManager(context: Context) {
    private val dataStore = context.createDataStore("app_config")

    val config: Flow<AppConfig> = dataStore.data
        .catch { emit(AppConfig()) }
        .map { preferences ->
            AppConfig(
                scannerEngine = ScannerEngine.valueOf(
                    preferences[SCANNER_ENGINE] ?: ScannerEngine.ML_KIT.name
                ),
                ocrEngine = OCREngine.valueOf(
                    preferences[OCR_ENGINE] ?: OCREngine.ML_KIT.name
                )
            )
        }

    suspend fun updateConfig(newConfig: AppConfig) {
        dataStore.edit { preferences ->
            preferences[SCANNER_ENGINE] = newConfig.scannerEngine.name
            preferences[OCR_ENGINE] = newConfig.ocrEngine.name
        }
    }
}
```

## 错误处理

```kotlin
sealed class ScanError : Exception() {
    object EngineNotAvailable : ScanError()
    object PermissionDenied : ScanError()
    object ScanCancelled : ScanError()
    data class ProcessingFailed(override val message: String) : ScanError()
}

sealed class OCRError : Exception() {
    object EngineNotInitialized : OCRError()
    object NoTextDetected : OCRError()
    data class RecognitionFailed(override val message: String) : OCRError()
}
```

## 扩展性

### 添加新扫描引擎

1. 实现 `DocumentScannerAdapter` 接口
2. 在 `ScannerEngine` 枚举中添加新类型
3. 在 `ScannerFactory` 中添加创建逻辑

```kotlin
// 示例：添加新的扫描引擎
enum class ScannerEngine {
    ML_KIT, OPENCV_CUSTOM, SMART_CROPPER,
    NEW_ENGINE  // 新增
}

class NewEngineAdapter : DocumentScannerAdapter {
    override val engineType = ScannerEngine.NEW_ENGINE
    // ... 实现接口方法
}

// 在工厂类中添加
object ScannerFactory {
    fun getAdapter(engine: ScannerEngine): DocumentScannerAdapter {
        return when (engine) {
            // ...
            ScannerEngine.NEW_ENGINE -> NewEngineAdapter()
        }
    }
}
```

### 添加新OCR引擎

类似地，实现 `OCRAdapter` 接口并更新工厂类。

## 项目结构

```
com.example.docscanner/
├── MainActivity.kt
├── scanner/
│   ├── ScannerTypes.kt          # 数据类型定义
│   ├── DocumentScannerAdapter.kt # 接口定义
│   ├── ScannerFactory.kt        # 工厂类
│   ├── MLKitScannerAdapter.kt   # ML Kit实现
│   ├── OpenCVScannerAdapter.kt  # OpenCV实现
│   └── SmartCropperAdapter.kt   # SmartCropper实现
├── ocr/
│   ├── OCRTypes.kt              # 数据类型定义
│   ├── OCRAdapter.kt            # 接口定义
│   ├── OCRFactory.kt            # 工厂类
│   ├── MLKitOCRAdapter.kt       # ML Kit OCR实现
│   ├── TesseractOCRAdapter.kt   # Tesseract实现
│   └── BaiduOCRAdapter.kt       # 百度OCR实现
├── ui/
│   ├── DocScannerApp.kt         # 导航
│   ├── theme/
│   │   └── Theme.kt
│   └── screens/
│       ├── HomeScreen.kt
│       ├── ScannerScreen.kt
│       ├── PreviewScreen.kt
│       ├── OCRResultScreen.kt
│       └── SettingsScreen.kt
└── util/
    ├── ImageUtils.kt
    └── FileUtils.kt
```

## 总结

统一适配层架构提供了：

1. **灵活性**: 支持多种引擎，可运行时切换
2. **可扩展性**: 易于添加新的扫描/OCR引擎
3. **可维护性**: 接口统一，实现分离
4. **容错性**: 自动选择最佳可用引擎
