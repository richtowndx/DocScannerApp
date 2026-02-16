# 方案A: Google ML Kit 官方文档扫描方案

## 概述

Google ML Kit Document Scanner API 是Google在2024年2月发布的官方文档扫描解决方案，提供完整的扫描UI流程和ML处理逻辑。

## 核心技术栈

```
Kotlin + Jetpack Compose + ML Kit Document Scanner API
```

## 依赖配置

```kotlin
// build.gradle.kts (app level)
implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
implementation("com.google.mlkit:text-recognition:19.0.0")
```

## 功能特性

### 1. 边框识别
- 自动检测文档边界
- 高精度角点定位
- 支持手动微调

### 2. 透视校正
- 自动透视变换
- 将倾斜文档转为正矩形
- 背景自动裁剪

### 3. 图像增强
- 自动对比度调整
- 支持彩色/灰度模式
- 图像锐化

### 4. UI流程
- 完整的扫描UI
- 支持多页扫描
- 相册导入支持

## 代码示例

### 初始化扫描器

```kotlin
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning

val options = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(true)
    .setPageLimit(10)
    .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
    .build()

val scanner = GmsDocumentScanning.getClient(options)
```

### 启动扫描

```kotlin
scanner.getStartIntentSender(context).let { intentSender ->
    activityResultLauncher.launch(
        IntentSenderRequest.Builder(intentSender).build()
    )
}
```

### 处理扫描结果

```kotlin
override fun onActivityResult(result: ActivityResult) {
    if (result.resultCode == Activity.RESULT_OK) {
        val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        scanningResult?.pages?.forEach { page ->
            val imageUri = page.imageUri
            // 处理扫描的图像
        }
    }
}
```

## OCR识别

```kotlin
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

// 初始化中文识别器
val recognizer = TextRecognition.getClient(
    ChineseTextRecognizerOptions.Builder().build()
)

// 识别图像中的文字
val image = InputImage.fromBitmap(bitmap, 0)
val result = recognizer.process(image).await()

// 获取识别结果
val text = result.text
for (block in result.textBlocks) {
    val blockText = block.text
    val boundingBox = block.boundingBox
}
```

## 优势

1. **开发简单**: 一行代码即可集成完整的扫描流程
2. **官方支持**: Google维护，稳定可靠
3. **完全离线**: 模型通过Play Service动态下载，下载后离线可用
4. **UI一致**: 统一的Material Design风格界面
5. **自动更新**: ML模型自动更新

## 劣势

1. **UI自定义有限**: 无法深度定制扫描界面
2. **依赖Play服务**: 需要Google Play Services
3. **最低API要求**: Android API 21+
4. **模型下载**: 首次使用需要下载模型

## 适用场景

- 需要快速上线的文档扫描功能
- 不需要深度定制UI的应用
- 目标用户主要使用Google Play服务设备

## 参考链接

- [ML Kit Document Scanner 官方文档](https://developers.google.com/ml-kit/vision/doc-scanner/android)
- [ML Kit Release Notes](https://developers.google.com/ml-kit/release-notes)
- [Android Developers Blog](https://android-developers.googleblog.com/2024/02/ml-kit-document-scanner-api.html)
