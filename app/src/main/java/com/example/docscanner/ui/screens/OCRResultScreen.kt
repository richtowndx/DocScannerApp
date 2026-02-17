package com.example.docscanner.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.docscanner.ocr.*
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OCRResultScreen(
    imageUri: String?,
    ocrEngine: OCREngine,
    @Suppress("UNUSED_PARAMETER") resultText: String?,
    onResultReady: (String) -> Unit,
    onBack: () -> Unit,
    onNewScan: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var isLoading by remember { mutableStateOf(true) }
    var ocrResult by remember { mutableStateOf<OCRResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showOriginalImage by remember { mutableStateOf(false) }
    var currentEngine by remember { mutableStateOf(ocrEngine) }

    val ocrAdapter = remember(currentEngine) {
        AppLog.i(LogTag.UI_OCR_RESULT, "Creating OCR adapter for engine: $currentEngine")
        OCRFactory.getAdapter(currentEngine, context)
    }

    // 执行OCR识别
    LaunchedEffect(imageUri, currentEngine) {
        if (imageUri != null) {
            try {
                isLoading = true
                error = null

                AppLog.i(LogTag.UI_OCR_RESULT, "Starting OCR with engine: $currentEngine")

                // 初始化OCR引擎
                val initSuccess = ocrAdapter.initialize()
                if (!initSuccess) {
                    AppLog.w(LogTag.UI_OCR_RESULT, "Engine $currentEngine not available, falling back to ML Kit")
                    // 如果当前引擎不可用，回退到ML Kit
                    if (currentEngine != OCREngine.ML_KIT) {
                        currentEngine = OCREngine.ML_KIT
                        return@LaunchedEffect
                    }
                }

                // 加载图片
                val uri = Uri.parse(imageUri)
                val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    // 执行OCR
                    val result = withContext(Dispatchers.Default) {
                        ocrAdapter.recognizeText(
                            bitmap,
                            OCRConfig(
                                engine = currentEngine,
                                enableMarkdown = true
                            )
                        )
                    }
                    ocrResult = result
                    onResultReady(result.markdownText)
                    AppLog.i(LogTag.UI_OCR_RESULT, "OCR completed: ${result.textBlocks.size} blocks, ${result.processingTimeMs}ms")
                } else {
                    error = "无法加载图片"
                }
                isLoading = false
            } catch (e: Exception) {
                AppLog.e(LogTag.UI_OCR_RESULT, "OCR failed: ${e.message}", e)
                error = e.message ?: "识别失败"
                isLoading = false
            }
        } else {
            error = "未找到图片"
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OCR识别结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 显示当前使用的引擎
                    Text(
                        text = getEngineDisplayName(currentEngine),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    IconButton(onClick = { showOriginalImage = !showOriginalImage }) {
                        Icon(
                            if (showOriginalImage) Icons.Default.ImageNotSupported else Icons.Default.Image,
                            contentDescription = "显示原图"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("正在识别文字...")
                        Text(
                            text = "使用引擎: ${getEngineDisplayName(currentEngine)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "识别失败: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onBack) {
                            Text("返回重试")
                        }
                    }
                }
            }

            ocrResult != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // 识别信息
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "识别完成",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "引擎: ${getEngineDisplayName(currentEngine)} | 耗时: ${ocrResult!!.processingTimeMs}ms",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            AssistChip(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(ocrResult!!.markdownText))
                                    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                                },
                                label = { Text("复制Markdown") },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }

                    // 结果显示
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                text = ocrResult!!.markdownText,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // 底部操作栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onNewScan,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新扫描")
                        }

                        Button(
                            onClick = {
                                // 导出文件
                                // TODO: 实现文件导出功能
                                Toast.makeText(context, "导出功能开发中", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("导出")
                        }
                    }
                }
            }
        }
    }

    // 原图预览对话框
    if (showOriginalImage && imageUri != null) {
        AlertDialog(
            onDismissRequest = { showOriginalImage = false },
            title = { Text("原始图片") },
            text = {
                // 简化显示，实际应使用AsyncImage等加载图片
                Text("原图URI: $imageUri")
            },
            confirmButton = {
                TextButton(onClick = { showOriginalImage = false }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun getEngineDisplayName(engine: OCREngine): String {
    return when (engine) {
        OCREngine.ML_KIT -> "ML Kit"
        OCREngine.TESSERACT -> "Tesseract"
        OCREngine.BAIDU -> "百度OCR"
        OCREngine.GLM_OCR -> "GLM-OCR"
        OCREngine.SILICONFLOW -> "SiliconFlow"
    }
}
