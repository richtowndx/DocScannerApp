# DocScannerApp

DocScannerApp 是一个 Android 文档扫描应用，支持多引擎文档扫描和 OCR 文字识别。使用 Kotlin + Jetpack Compose 构建。

## 功能特性

- 📷 文档扫描
- 🔍 OCR 文字识别
- 🌐 支持中英文识别
- 🎨 Modern UI with Jetpack Compose

## OCR 引擎支持

### ML Kit (默认引擎)
- **引擎**: Google ML Kit Text Recognition
- **特点**: 
  - 默认引擎，开箱即用
  - 支持中文识别
  - 支持拉丁文字
  - 快速准确
  - 无需额外下载语言数据文件

### Tesseract
- **引擎**: Tesseract OCR
- **特点**:
  - 开源 OCR 引擎
  - 支持多种语言
  - 需要下载语言数据文件
  - 可离线使用

## 技术栈

- **语言**: Kotlin
- **UI 框架**: Jetpack Compose
- **架构**: MVVM + Repository Pattern
- **依赖注入**: Hilt
- **相机**: CameraX
- **OCR**: 
  - Google ML Kit Text Recognition (中文支持)
  - Tesseract OCR

## 使用说明

1. 打开应用
2. 授予相机权限
3. 选择 OCR 引擎 (ML Kit 或 Tesseract)
4. 拍摄文档
5. 自动识别文字

## 注意事项

- **ML Kit**: 默认引擎，推荐使用，支持中文
- **Tesseract**: 需要手动下载语言数据文件到 `tessdata` 目录

## 关于 RAPIDOCR

RAPIDOCR 引擎曾被测试，但效果并不理想，因此未包含在当前版本中。

## License

MIT License