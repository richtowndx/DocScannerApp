# 方案C: 开源库集成方案

## 概述

集成成熟的开源扫描库，在开发效率和自定义能力之间取得平衡。推荐使用SmartCropper或ScanLibrary作为核心扫描引擎。

## 核心技术栈

```
Kotlin + SmartCropper/ScanLibrary + ML Kit OCR
```

## 推荐开源库

### 1. SmartCropper (推荐)

**GitHub**: https://github.com/pqpo/SmartCropper
**Stars**: 3.6k+

#### 特性
- 智能边框检测
- 透视校正裁剪
- 支持身份证、名片、文档等
- 纯Java实现，无OpenCV依赖

#### 依赖配置

```kotlin
// settings.gradle.kts
repositories {
    maven { url = uri("https://jitpack.io") }
}

// build.gradle.kts
implementation("com.github.pqpo:SmartCropper:v2.1.3")
```

#### 使用示例

```kotlin
import me.pqpo.smartcropperlib.SmartCropper

// 检测文档边框
val points = SmartCropper.scan(bitmap)
if (points != null && points.size == 4) {
    // 检测成功，points为四个角点
}

// 执行透视校正裁剪
val croppedBitmap = SmartCropper.crop(bitmap, points)
```

---

### 2. AndroidScannerDemo / ScanLibrary

**GitHub**: https://github.com/jhansireddy/AndroidScannerDemo
**Stars**: 1.2k+

#### 特性
- 基于OpenCV
- 边框检测与裁剪
- 透视校正
- 丰富的自定义选项

#### 依赖配置

```kotlin
implementation("com.github.jhansireddy:AndroidScannerDemo:TAG")
```

---

### 3. LiveEdgeDetection

**GitHub**: https://github.com/adityaarora1/LiveEdgeDetection
**Stars**: 600+

#### 特性
- 实时边缘检测
- 基于OpenCV
- 相机预览模式
- 支持手动调整

---

### 4. OpenNoteScanner

**GitHub**: https://github.com/allgood/OpenNoteScanner
**Stars**: 500+

#### 特性
- 完整的扫描App
- 可参考整体架构
- 自动边框检测
- 多种滤镜

---

### 5. Android Document Scanner Library (新)

**GitHub**: https://github.com/entropyconquers/android-document-scanner-library

#### 特性
- OpenCV + MLKit 混合方案
- Kotlin实现
- 现代化架构

---

## 推荐组合方案

### 组合A: SmartCropper + ML Kit OCR (推荐)

```kotlin
// 扫描: SmartCropper
implementation("com.github.pqpo:SmartCropper:v2.1.3")

// OCR: ML Kit
implementation("com.google.mlkit:text-recognition:19.0.0")
```

**优势**: 轻量级、快速集成、中文OCR支持好

### 组合B: ScanLibrary + Tesseract OCR

```kotlin
// 扫描: ScanLibrary (OpenCV)
implementation("com.github.jhansireddy:AndroidScannerDemo:TAG")

// OCR: Tesseract
implementation("cz.adaptech.tesseract4android:tesseract4android:4.1.0")
```

**优势**: 完全开源、可训练自定义模型

---

## SmartCropper 详细集成指南

### 1. 添加依赖

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.pqpo:SmartCropper:v2.1.3")
}
```

### 2. 初始化

```kotlin
// Application类中初始化
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // SmartCropper会自动初始化
    }
}
```

### 3. 扫描流程

```kotlin
class ScanViewModel : ViewModel() {

    suspend fun scanDocument(bitmap: Bitmap): Bitmap? = withContext(Dispatchers.Default) {
        // 1. 检测边框
        val points = SmartCropper.scan(bitmap)

        if (points != null && points.size == 4) {
            // 2. 执行裁剪（透视校正）
            return@withContext SmartCropper.crop(bitmap, points)
        }

        null
    }
}
```

### 4. UI实现

```kotlin
@Composable
fun CropScreen(
    bitmap: Bitmap,
    onCropComplete: (Bitmap) -> Unit
) {
    var cropPoints by remember { mutableStateOf<List<PointF>?>(null) }

    // 显示图片和可拖动的角点
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        // 绘制边框和控制点
        cropPoints?.let { points ->
            // 绘制四边形边框
            // 绘制四个可拖动的控制点
        }
    }

    Button(onClick = {
        cropPoints?.let { points ->
            val cropped = SmartCropper.crop(bitmap, points.toTypedArray())
            onCropComplete(cropped)
        }
    }) {
        Text("确认裁剪")
    }
}
```

---

## 对比总结

| 特性 | SmartCropper | ScanLibrary | LiveEdgeDetection |
|------|-------------|-------------|-------------------|
| Stars | 3.6k+ | 1.2k+ | 600+ |
| OpenCV依赖 | 无 | 有 | 有 |
| 边框检测 | 自动 | 自动 | 实时 |
| 透视校正 | 有 | 有 | 有 |
| APK增量 | ~2MB | ~20MB | ~20MB |
| 集成难度 | 简单 | 中等 | 中等 |
| 维护状态 | 活跃 | 一般 | 不活跃 |

---

## 优势

1. **开发效率高**: 成熟的开源实现，快速集成
2. **社区支持**: 问题可在GitHub上讨论
3. **源码可控**: 可以修改源码适应需求
4. **APK较小**: SmartCropper无OpenCV依赖

## 劣势

1. **版本更新**: 部分库更新不活跃
2. **兼容性**: 可能与项目其他依赖冲突
3. **功能限制**: 功能受限于库的设计
4. **Bug修复**: 需要等待社区修复或自行修复

## 适用场景

- 需要平衡开发效率和自定义能力
- 项目对APK大小有要求
- 需要参考成熟实现
- 中小规模的文档扫描需求

## 参考链接

- [SmartCropper GitHub](https://github.com/pqpo/SmartCropper)
- [AndroidScannerDemo GitHub](https://github.com/jhansireddy/AndroidScannerDemo)
- [LiveEdgeDetection GitHub](https://github.com/adityaarora1/LiveEdgeDetection)
- [OpenNoteScanner GitHub](https://github.com/allgood/OpenNoteScanner)
