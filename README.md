# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

DocScannerApp 是一个 Android 文档扫描应用，支持多引擎文档扫描和 OCR 文字识别。使用 Kotlin + Jetpack Compose 构建。

## 常用构建命令

```bash
# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK（需要签名配置）
./gradlew assembleRelease

# 清理构建
./gradlew clean

# 运行单元测试
./gradlew test

# 运行 Android 仪器测试
./gradlew connectedAndroidTest

# Lint 检查
./gradlew lint

# 安装到已连接设备
./gradlew installDebug
```

## 技术栈

- **语言**: Kotlin 1.9.0
- **UI**: Jetpack Compose (Material 3)
- **最低 SDK**: 24 (Android 7.0)
- **目标 SDK**: 34
- **JVM Target**: Java 17

## 架构：统一适配层

项目采用适配器模式，支持多种扫描/OCR 引擎的无缝切换。

### 核心组件

| 包路径 | 职责 |
|--------|------|
| `scanner/` | 文档扫描引擎适配器 |
| `ocr/` | OCR 文字识别引擎适配器 |
| `ui/screens/` | Compose UI 界面 |
| `util/` | 工具类 |

### 扫描引擎

- **ML_KIT**: Google ML Kit Document Scanner（默认，推荐）
- **OPENCV_CUSTOM**: OpenCV 自定义实现
- **SMART_CROPPER**: SmartCropper 库

### OCR 引擎

- **ML_KIT**: Google ML Kit Text Recognition（默认，支持中文）
- **TESSERACT**: Tesseract OCR（需要语言数据文件）
- **BAIDU**: 百度 OCR API
- **HUAWEI**: 华为 HMS ML Kit

### 使用工厂模式获取适配器

```kotlin
// 获取扫描适配器
val scannerAdapter = ScannerFactory.getAdapter(ScannerEngine.ML_KIT)

// 获取 OCR 适配器
val ocrAdapter = OCRFactory.getAdapter(OCREngine.ML_KIT)

// 自动选择最佳可用引擎
val bestEngine = ScannerFactory.getBestAvailableEngine()
```

### 主要接口

**DocumentScannerAdapter** (`scanner/DocumentScannerAdapter.kt`)
- `isAvailable()` - 检查引擎可用性
- `prepareScannerIntent()` - 准备扫描 Intent
- `processScanResult()` - 处理扫描结果
- `detectDocumentEdges()` - 检测文档边框
- `applyPerspectiveCorrection()` - 透视校正
- `applyImageProcessing()` - 图像处理

**OCRAdapter** (`ocr/OCRAdapter.kt`)
- `isAvailable()` - 检查引擎可用性
- `initialize()` - 初始化引擎
- `recognizeText()` - 执行文字识别

## 数据流

```
扫描: ScannerFactory → Adapter.prepareScannerIntent() → 处理结果 → PreviewScreen
OCR: OCRFactory → Adapter.recognizeText() → OCRResult.markdownText → OCRResultScreen
```

## 签名配置

Release 构建使用 `keystore.properties` 配置签名：

```properties
storeFile=keystore/release.keystore
storePassword=xxx
keyAlias=xxx
keyPassword=xxx
```

## 依赖镜像

项目使用阿里云 Maven 镜像加速（`settings.gradle.kts`）。

## 扩展新引擎

1. 在对应枚举中添加新类型 (`ScannerEngine` / `OCREngine`)
2. 实现 `DocumentScannerAdapter` 或 `OCRAdapter` 接口
3. 在 `ScannerFactory` 或 `OCRFactory` 中添加创建逻辑
