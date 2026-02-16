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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OCRResultScreen(
    imageUri: String?,
    resultText: String?,
    onResultReady: (String) -> Unit,
    onBack: () -> Unit,
    onNewScan: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    var isLoading by remember { mutableStateOf(true) }
    var ocrResult by remember { mutableStateOf<OCRResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showOriginalImage by remember { mutableStateOf(false) }

    val ocrAdapter = remember { OCRFactory.getAdapter(OCREngine.ML_KIT) }

    // 执行OCR识别
    LaunchedEffect(imageUri) {
        if (imageUri != null) {
            try {
                // 初始化OCR引擎
                ocrAdapter.initialize()

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
                                engine = OCREngine.ML_KIT,
                                enableMarkdown = true
                            )
                        )
                    }
                    ocrResult = result
                    onResultReady(result.markdownText)
                }
                isLoading = false
            } catch (e: Exception) {
                error = e.message
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
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
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
                                    text = "耗时: ${ocrResult!!.processingTimeMs}ms",
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
