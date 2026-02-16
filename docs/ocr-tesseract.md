# Tesseract OCR 集成指南

## 概述

Tesseract 是一个开源的 OCR 引擎，由 Google 维护。在 Android 平台上使用 tess-two 或 tesseract4android 库。

## 依赖配置

### 方式一: tesseract4android (推荐)

```gradle
// build.gradle (app level)
dependencies {
    implementation 'cz.adaptech.tesseract4android:tesseract4android:4.1.0'
}
```

### 方式二: tess-two

```gradle
dependencies {
    implementation 'com.rmtheis:tess-two:9.1.0'
}
```

## 语言数据文件

需要下载语言训练数据文件 (.traineddata) 并放置到设备上：

- 中文简体: `chi_sim.traineddata`
- 英文: `eng.traineddata`
- 中文繁体: `chi_tra.traineddata`

下载地址: https://github.com/tesseract-ocr/tessdata

### 文件存放路径

```
/storage/emulated/0/tesseract/tessdata/
    ├── chi_sim.traineddata
    ├── eng.traineddata
    └── chi_tra.traineddata
```

## API 使用

### Kotlin 示例

```kotlin
import com.googlecode.tesseract.android.TessBaseAPI

class TesseractOCR {
    private var tessBaseAPI: TessBaseAPI? = null
    private val tessDataPath = "/storage/emulated/0/tesseract/"
    private val language = "chi_sim+eng"  // 中文+英文

    fun initialize(): Boolean {
        return try {
            tessBaseAPI = TessBaseAPI()
            tessBaseAPI?.init(tessDataPath, language)
            true
        } catch (e: Exception) {
            Log.e("TesseractOCR", "初始化失败: ${e.message}")
            false
        }
    }

    fun recognizeText(bitmap: Bitmap): String {
        tessBaseAPI?.setImage(bitmap)
        val text = tessBaseAPI?.utf8Text ?: ""
        return text
    }

    fun getWords(): List<Word> {
        val words = mutableListOf<Word>()
        val result = tessBaseAPI?.words ?: return words
        for (word in result) {
            words.add(Word(
                text = word.utf8Text,
                boundingBox = word.boundingBox,
                confidence = word.confidence / 100f
            ))
        }
        return words
    }

    fun release() {
        tessBaseAPI?.end()
        tessBaseAPI = null
    }
}

data class Word(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)
```

## 配置选项

```kotlin
// 设置页面分割模式
tessBaseAPI?.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO)

// 设置 OCR 引擎模式
tessBaseAPI?.setOcrEngineMode(TessBaseAPI.OcrEngineMode.OEM_LSTM_ONLY)

// 设置变量
tessBaseAPI?.setVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ")
```

## 注意事项

1. **性能**: Tesseract 处理速度较慢，建议在后台线程执行
2. **内存**: 处理大图片可能导致 OOM，建议先压缩图片
3. **精度**: 对于中文识别，推荐使用 LSTM 模型
4. **离线**: 完全离线工作，不需要网络

## 优缺点

### 优点
- 完全开源免费
- 支持多种语言
- 完全离线工作
- 可自定义训练模型

### 缺点
- 处理速度较慢
- 需要手动管理语言数据文件
- 对复杂排版效果一般
