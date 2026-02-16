# 百度 OCR 集成指南

## 概述

百度 OCR 提供高精度的文字识别服务，特别适合中文场景。支持通用文字识别、身份证识别、银行卡识别等多种场景。

## 注册与配置

### 1. 创建百度智能云账号

1. 访问 https://console.bce.baidu.com/
2. 注册并完成实名认证
3. 开通文字识别服务

### 2. 创建应用获取密钥

1. 进入"文字识别"控制台
2. 创建应用
3. 获取 `API Key` 和 `Secret Key`

## 依赖配置

```gradle
// build.gradle (app level)
dependencies {
    implementation 'com.baidu.aip:java-sdk:4.16.17'
}
```

## API 使用

### 获取 Access Token

```kotlin
import com.baidu.aip.util.Base64Util
import com.baidu.aip.util.HttpUtil
import org.json.JSONObject

class BaiduOCR {
    private val apiKey = "YOUR_API_KEY"
    private val secretKey = "YOUR_SECRET_KEY"
    private var accessToken: String? = null

    suspend fun getAccessToken(): String? {
        val url = "https://aip.baidubce.com/oauth/2.0/token?" +
                "grant_type=client_credentials&" +
                "client_id=$apiKey&" +
                "client_secret=$secretKey"

        val response = HttpUtil.get(url)
        val json = JSONObject(response)
        accessToken = json.getString("access_token")
        return accessToken
    }
}
```

### 通用文字识别

```kotlin
suspend fun recognizeText(bitmap: Bitmap): OCRResult {
    // 确保 access token 有效
    if (accessToken == null) {
        getAccessToken()
    }

    // 将 Bitmap 转换为 Base64
    val outputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
    val imageBase64 = Base64Util.encode(outputStream.toByteArray())

    // 构建请求 URL
    val url = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=$accessToken"

    // 构建请求参数
    val params = HashMap<String, String>()
    params["image"] = imageBase64
    params["language_type"] = "CHN_ENG"  // 中英文混合

    // 发送请求
    val response = HttpUtil.post(url, params)

    return parseResult(response)
}

private fun parseResult(response: String): OCRResult {
    val json = JSONObject(response)
    val wordsResult = json.getJSONArray("words_result")

    val textBlocks = mutableListOf<TextBlock>()
    val fullText = StringBuilder()

    for (i in 0 until wordsResult.length()) {
        val item = wordsResult.getJSONObject(i)
        val words = item.getString("words")
        fullText.append(words).append("\n")

        textBlocks.add(TextBlock(
            text = words,
            boundingBox = Rect(),
            confidence = 1.0f,
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
```

### 高精度识别

```kotlin
suspend fun recognizeAccurate(bitmap: Bitmap): OCRResult {
    val url = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic?access_token=$accessToken"

    val params = HashMap<String, String>()
    params["image"] = imageToBase64(bitmap)
    params["detect_direction"] = "true"
    params["paragraph"] = "true"

    val response = HttpUtil.post(url, params)
    return parseResult(response)
}
```

## 支持的识别类型

| API | 说明 |
|-----|------|
| general_basic | 通用文字识别 |
| accurate_basic | 通用文字识别（高精度）|
| general | 通用文字识别（含位置信息）|
| accurate | 通用文字识别（高精度含位置）|
| handwriting | 手写文字识别 |
| webimage | 网络图片文字识别 |

## 配额与计费

- 通用文字识别：每月 1000 次免费
- 高精度识别：每月 500 次免费
- 超出后按次计费

## 注意事项

1. **网络要求**: 需要网络连接
2. **API Key 安全**: 不要将密钥硬编码，建议使用后端代理
3. **图片大小**: 建议图片不超过 10MB
4. **调用频率**: 注意 API 调用频率限制

## 优缺点

### 优点
- 中文识别精度高
- 支持多种场景
- 有免费额度

### 缺点
- 需要网络
- 需要注册和认证
- 超出配额需付费
- 需要保护 API Key
