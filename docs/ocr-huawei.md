# 华为 HMS ML Kit OCR 集成指南

## 概述

华为 HMS ML Kit 提供端侧和云侧文本识别服务，支持多语言识别，识别速度快，精度高。

## 准备工作

### 1. 注册华为开发者

1. 访问 https://developer.huawei.com/consumer/cn/
2. 注册并完成实名认证
3. 创建应用

### 2. 配置 AppGallery Connect

1. 在 AppGallery Connect 中创建项目和应用
2. 开通机器学习服务
3. 下载 `agconnect-services.json` 文件

## 依赖配置

### 项目级 build.gradle

```gradle
allprojects {
    repositories {
        google()
        jcenter()
        maven { url 'https://developer.huawei.com/repo/' }
    }
}
```

### 应用级 build.gradle

```gradle
dependencies {
    // 基础 ML Kit
    implementation 'com.huawei.hms:ml-computer-vision-ocr:3.11.0.300'
    // 中文识别模型
    implementation 'com.huawei.hms:ml-computer-vision-ocr-chinese-model:3.11.0.300'
    // 日文识别模型（可选）
    implementation 'com.huawei.hms:ml-computer-vision-ocr-japanese-model:3.11.0.300'
    // 韩文识别模型（可选）
    implementation 'com.huawei.hms:ml-computer-vision-ocr-korean-model:3.11.0.300'
    // 拉丁文识别模型（可选）
    implementation 'com.huawei.hms:ml-computer-vision-ocr-latin-model:3.11.0.300'
}
```

## API 使用

### 端侧文本识别

```kotlin
import com.huawei.hms.mlsdk.common.MLFrame
import com.huawei.hms.mlsdk.text.MLLocalTextSetting
import com.huawei.hms.mlsdk.text.MLText
import com.huawei.hms.mlsdk.text.MLTextAnalyzer

class HuaweiOCR {
    private var analyzer: MLTextAnalyzer? = null

    fun initialize() {
        // 配置识别语言
        val setting = MLLocalTextSetting.Factory()
            .setOCRMode(MLLocalTextSetting.OCR_DETECT_MODE)  // 检测模式
            .setLanguage("zh")  // 中文
            .create()

        analyzer = MLTextAnalyzer.Factory().setLocalTextSetting(setting).create()
    }

    suspend fun recognizeText(bitmap: Bitmap): MLText? {
        val frame = MLFrame.fromBitmap(bitmap)
        return analyzer?.asyncAnalyseFrame(frame)?.await()
    }

    fun parseResult(mlText: MLText?): OCRResult {
        if (mlText == null) {
            return OCRResult(emptyList(), "", "", 0)
        }

        val textBlocks = mutableListOf<TextBlock>()
        val fullText = StringBuilder()

        for (block in mlText.blocks) {
            val blockText = block.stringValue
            fullText.append(blockText).append("\n")

            val boundingBox = block.border?.let { border ->
                Rect(
                    border.left.toInt(),
                    border.top.toInt(),
                    border.right.toInt(),
                    border.bottom.toInt()
                )
            } ?: Rect()

            textBlocks.add(TextBlock(
                text = blockText,
                boundingBox = boundingBox,
                confidence = block.probability,
                blockType = BlockType.PARAGRAPH
            ))
        }

        return OCRResult(
            textBlocks = textBlocks,
            fullText = fullText.toString(),
            markdownText = fullText.toString(),
            processingTimeMs = 0
        )
    }

    fun release() {
        analyzer?.close()
        analyzer = null
    }
}
```

### 云侧文本识别（更高精度）

```kotlin
import com.huawei.hms.mlsdk.text.MLRemoteTextSetting
import com.huawei.hms.mlsdk.text.MLRemoteTextAnalyzer

class HuaweiCloudOCR {
    private var cloudAnalyzer: MLRemoteTextAnalyzer? = null

    fun initialize() {
        val setting = MLRemoteTextSetting.Factory()
            .setTextDensityScene(MLRemoteTextSetting.OCR_LOOSE_SCENE)
            .setLanguageList(arrayOf("zh", "en"))
            .setBorderType(MLRemoteTextSetting.ARC)
            .create()

        cloudAnalyzer = MLRemoteTextAnalyzer.Factory()
            .setRemoteTextSetting(setting)
            .create()
    }

    suspend fun recognizeText(bitmap: Bitmap): MLText? {
        val frame = MLFrame.fromBitmap(bitmap)
        return cloudAnalyzer?.asyncAnalyseFrame(frame)?.await()
    }
}
```

### 完整示例

```kotlin
class HuaweiOCRAdapter : OCRAdapter {

    override val engineType: OCREngine = OCREngine.HUAWEI

    private var analyzer: MLTextAnalyzer? = null

    override suspend fun isAvailable(): Boolean {
        return try {
            // 检查 HMS Core 是否可用
            val hmsApiAvailability = HuaweiApiAvailability.getInstance()
            hmsApiAvailability.isHuaweiMobileServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun initialize(): Boolean {
        return try {
            val setting = MLLocalTextSetting.Factory()
                .setOCRMode(MLLocalTextSetting.OCR_TRACKING_MODE)
                .setLanguage("zh")
                .create()

            analyzer = MLTextAnalyzer.Factory()
                .setLocalTextSetting(setting)
                .create()

            // 下载模型（如果需要）
            val modelManager = MLModelManager.getInstance()
            MLLocalTextAnalyzer.getInstance().getLocalTextSetting()
            true
        } catch (e: Exception) {
            Log.e("HuaweiOCR", "初始化失败: ${e.message}")
            false
        }
    }

    override suspend fun recognizeText(bitmap: Bitmap, config: OCRConfig): OCRResult {
        if (analyzer == null) {
            initialize()
        }

        val startTime = System.currentTimeMillis()
        val frame = MLFrame.fromBitmap(bitmap)
        val mlText = analyzer?.asyncAnalyseFrame(frame)?.await()

        val result = parseResult(mlText)
        val processingTime = System.currentTimeMillis() - startTime

        return result.copy(processingTimeMs = processingTime)
    }

    private fun parseResult(mlText: MLText?): OCRResult {
        // ... 解析逻辑
    }
}
```

## 支持的语言

| 语言 | 代码 |
|------|------|
| 中文 | zh |
| 英文 | en |
| 日文 | ja |
| 韩文 | ko |
| 拉丁文 | la |
| 俄文 | ru |
| 葡萄牙文 | pt |

## 注意事项

1. **HMS Core**: 设备需要安装 HMS Core 服务
2. **模型下载**: 首次使用会自动下载语言模型
3. **华为设备**: 在华为设备上效果最佳
4. **非华为设备**: 也可以使用，但需要安装 HMS Core

## 优缺点

### 优点
- 识别速度快
- 中文识别精度高
- 支持端侧识别（离线）
- 免费使用

### 缺点
- 需要安装 HMS Core
- 非 Huawei 设备体验可能受影响
- 需要华为开发者账号
