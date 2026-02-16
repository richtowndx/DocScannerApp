# OCR 文字识别方案

## 概述

本文档总结了Android平台可用的OCR文字识别方案，包括各方案的对比、集成方式和Markdown输出策略。

## OCR方案对比

| OCR方案 | 准确率 | 速度 | 离线 | 中文支持 | 费用 | 推荐度 |
|---------|--------|------|------|----------|------|--------|
| Google ML Kit | ⭐⭐⭐⭐⭐ | 快 | ✅ | ✅ | 免费 | ⭐⭐⭐⭐⭐ |
| Tesseract | ⭐⭐⭐ | 中 | ✅ | ✅ | 开源免费 | ⭐⭐⭐ |
| 百度OCR | ⭐⭐⭐⭐⭐ | 快 | ❌ | ✅✅ | 有免费额度 | ⭐⭐⭐⭐ |
| 腾讯OCR | ⭐⭐⭐⭐ | 快 | ❌ | ✅ | 有免费额度 | ⭐⭐⭐ |
| 华为HMS | ⭐⭐⭐⭐ | 快 | ✅ | ✅ | 免费 | ⭐⭐⭐⭐ |

---

## 方案1: Google ML Kit Text Recognition (推荐)

### 优势
- 完全离线
- 中文支持良好
- 集成简单
- 与ML Kit Document Scanner协同

### 依赖配置

```kotlin
// build.gradle.kts
implementation("com.google.mlkit:text-recognition:19.0.0")

// 中文识别
implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

// 日文识别
implementation("com.google.mlkit:text-recognition-japanese:16.0.0")

// 韩文识别
implementation("com.google.mlkit:text-recognition-korean:16.0.0")
```

### 使用示例

```kotlin
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

class MLKitOCR {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    suspend fun recognize(bitmap: Bitmap): OCRResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()

        val textBlocks = result.textBlocks.map { block ->
            TextBlock(
                text = block.text,
                boundingBox = block.boundingBox ?: Rect(),
                blockType = determineBlockType(block.text, block.boundingBox)
            )
        }

        return OCRResult(
            textBlocks = textBlocks,
            fullText = result.text,
            markdownText = convertToMarkdown(textBlocks)
        )
    }
}
```

---

## 方案2: Tesseract OCR

### 优势
- 完全开源
- 支持多语言
- 可训练自定义模型

### 依赖配置

```kotlin
// build.gradle.kts
implementation("cz.adaptech.tesseract4android:tesseract4android:4.1.0")
```

### 使用示例

```kotlin
import com.googlecode.tesseract.android.TessBaseAPI

class TesseractOCR(private val tessDataPath: String) {

    private var tessBaseAPI: TessBaseAPI? = null

    fun initialize(): Boolean {
        tessBaseAPI = TessBaseAPI()
        return tessBaseAPI!!.init(tessDataPath, "chi_sim+eng")
    }

    fun recognize(bitmap: Bitmap): String {
        tessBaseAPI!!.setImage(bitmap)
        return tessBaseAPI!!.utf8Text
    }

    fun getConfidence(): Float {
        return tessBaseAPI!!.meanConfidence()
    }

    fun release() {
        tessBaseAPI!!.end()
    }
}
```

### 语言数据下载

需要从 https://github.com/tesseract-ocr/tessdata 下载语言数据文件：
- `chi_sim.traineddata` - 简体中文
- `eng.traineddata` - 英文

放置到: `/storage/emulated/0/tesseract/tessdata/`

---

## 方案3: 百度OCR API

### 优势
- 中文识别准确率最高
- 支持手写体识别
- 支持表格识别

### 依赖配置

```kotlin
// build.gradle.kts
implementation("com.baidu.aip:java-sdk:4.16.17")
```

### 使用示例

```kotlin
import com.baidu.aip.ocr.AipOcr

class BaiduOCR(
    private val appId: String,
    private val apiKey: String,
    private val secretKey: String
) {
    private val client = AipOcr(appId, apiKey, secretKey)

    suspend fun recognize(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
        val bytes = stream.toByteArray()

        val options = HashMap<String, String>()
        options["language_type"] = "CHN_ENG"
        options["detect_direction"] = "true"
        options["detect_language"] = "true"

        val result = client.basicGeneral(bytes, options)

        val wordsResult = result.getJSONArray("words_result")
        val sb = StringBuilder()
        for (i in 0 until wordsResult.length()) {
            val word = wordsResult.getJSONObject(i)
            sb.append(word.getString("words"))
            sb.append("\n")
        }

        return sb.toString()
    }
}
```

### 免费额度

- 通用文字识别：每日500次
- 高精度识别：每日50次

---

## Markdown 格式化策略

### 文本块类型识别

```kotlin
enum class BlockType {
    TITLE,       // 标题 -> # heading
    SUBTITLE,    // 副标题 -> ## heading
    PARAGRAPH,   // 段落 -> plain text
    LIST_ITEM,   // 列表项 -> - item
    TABLE,       // 表格 -> | table |
    CODE,        // 代码 -> ``` code ```
    UNKNOWN
}

fun determineBlockType(text: String, boundingBox: Rect?): BlockType {
    val trimmedText = text.trim()

    // 检测标题（通常较短，字体较大）
    if (trimmedText.length < 50) {
        // 如果文本较短且全大写，可能是标题
        if (trimmedText.all { it.isUpperCase() || it.isDigit() || it.isWhitespace() }) {
            return BlockType.TITLE
        }
        // 如果boundingBox存在，可以通过高度判断
        boundingBox?.let {
            if (it.height() > it.width() * 0.15) {
                return BlockType.TITLE
            }
        }
    }

    // 检测列表项
    if (trimmedText.startsWith("•") ||
        trimmedText.startsWith("-") ||
        trimmedText.startsWith("*") ||
        trimmedText.matches(Regex("^\\d+\\..*"))
    ) {
        return BlockType.LIST_ITEM
    }

    // 检测表格（包含多个 |）
    if (trimmedText.count { it == '|' } >= 2) {
        return BlockType.TABLE
    }

    // 检测代码（包含大量特殊字符）
    if (trimmedText.count { it in setOf('{', '}', ';', '(', ')') } > trimmedText.length * 0.1) {
        return BlockType.CODE
    }

    return BlockType.PARAGRAPH
}
```

### 转换为Markdown

```kotlin
fun convertToMarkdown(textBlocks: List<TextBlock>): String {
    val markdown = StringBuilder()

    for (block in textBlocks) {
        val text = block.text.trim()

        when (block.blockType) {
            BlockType.TITLE -> {
                markdown.append("# $text\n\n")
            }
            BlockType.SUBTITLE -> {
                markdown.append("## $text\n\n")
            }
            BlockType.LIST_ITEM -> {
                val formattedText = text
                    .replace(Regex("^[•*]\\s*"), "- ")
                    .replace(Regex("^(\\d+)\\."), "$1.")
                markdown.append("$formattedText\n")
            }
            BlockType.TABLE -> {
                markdown.append("$text\n")
            }
            BlockType.CODE -> {
                markdown.append("```\n$text\n```\n\n")
            }
            BlockType.PARAGRAPH -> {
                markdown.append("$text\n\n")
            }
            BlockType.UNKNOWN -> {
                markdown.append("$text\n\n")
            }
        }
    }

    return markdown.toString().trim()
}
```

---

## 统一OCR接口设计

```kotlin
interface OCRAdapter {
    val engineType: OCREngine
    suspend fun isAvailable(): Boolean
    suspend fun initialize(): Boolean
    suspend fun recognizeText(bitmap: Bitmap, config: OCRConfig): OCRResult
}

data class OCRConfig(
    val engine: OCREngine = OCREngine.ML_KIT,
    val language: String = "chi_sim+eng",
    val enableMarkdown: Boolean = true
)

data class OCRResult(
    val textBlocks: List<TextBlock>,
    val fullText: String,
    val markdownText: String,
    val processingTimeMs: Long
)

data class TextBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float = 0f,
    val blockType: BlockType = BlockType.TEXT
)

enum class OCREngine {
    ML_KIT, TESSERACT, BAIDU, HUAWEI
}
```

---

## 推荐：ML Kit + Markdown输出

基于准确率、离线能力和集成难度，推荐使用 **Google ML Kit Text Recognition** 作为主要OCR方案：

```kotlin
// 完整示例
class DocumentProcessor {

    private val ocrAdapter = MLKitOCRAdapter()

    suspend fun processDocument(bitmap: Bitmap): ProcessResult {
        // 1. 执行OCR识别
        val ocrResult = ocrAdapter.recognizeText(
            bitmap,
            OCRConfig(enableMarkdown = true)
        )

        // 2. 返回结果
        return ProcessResult(
            originalBitmap = bitmap,
            fullText = ocrResult.fullText,
            markdownText = ocrResult.markdownText,
            textBlocks = ocrResult.textBlocks
        )
    }
}
```

---

## 参考链接

- [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition)
- [Tesseract OCR](https://github.com/tesseract-ocr/tesseract)
- [百度OCR API](https://cloud.baidu.com/product/ocr)
- [华为HMS ML Kit](https://developer.huawei.com/consumer/cn/hms/huawei-mlkit/)
