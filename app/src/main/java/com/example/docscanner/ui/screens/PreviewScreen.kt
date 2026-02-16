package com.example.docscanner.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.docscanner.scanner.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    imageUri: String,
    onProcessImage: (String) -> Unit,
    onStartOCR: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showProcessDialog by remember { mutableStateOf(false) }

    // 处理选项
    var enableGrayscale by remember { mutableStateOf(false) }
    var enableSharpen by remember { mutableStateOf(false) }
    var enableAutoEnhance by remember { mutableStateOf(true) }
    var brightness by remember { mutableStateOf(1.0f) }
    var contrast by remember { mutableStateOf(1.0f) }

    val scannerAdapter = remember { ScannerFactory.getAdapter(ScannerEngine.OPENCV_CUSTOM) }

    // 加载图片
    LaunchedEffect(imageUri) {
        try {
            val uri = Uri.parse(imageUri)
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            isLoading = false
        } catch (e: Exception) {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 图片预览区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val displayBitmap = processedBitmap ?: bitmap
                    displayBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "扫描预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } ?: run {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("无法加载图片")
                        }
                    }
                }

                // 底部控制栏
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        // 处理选项
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProcessButton(
                                icon = Icons.Default.FilterBAndW,
                                label = "灰度",
                                active = enableGrayscale,
                                onClick = { enableGrayscale = !enableGrayscale }
                            )
                            ProcessButton(
                                icon = Icons.Default.Brush,
                                label = "锐化",
                                active = enableSharpen,
                                onClick = { enableSharpen = !enableSharpen }
                            )
                            ProcessButton(
                                icon = Icons.Default.AutoFixHigh,
                                label = "增强",
                                active = enableAutoEnhance,
                                onClick = { enableAutoEnhance = !enableAutoEnhance }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 操作按钮
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        showProcessDialog = true
                                        try {
                                            val original = bitmap!!
                                            val options = ProcessOptions(
                                                enableGrayscale = enableGrayscale,
                                                enableSharpen = enableSharpen,
                                                enableAutoEnhance = enableAutoEnhance,
                                                brightness = brightness,
                                                contrast = contrast
                                            )
                                            processedBitmap = withContext(Dispatchers.Default) {
                                                scannerAdapter.applyImageProcessing(original, options)
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        showProcessDialog = false
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("应用效果")
                            }

                            Button(
                                onClick = onStartOCR,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.TextFields, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OCR识别")
                            }
                        }
                    }
                }
            }
        }
    }

    // 处理中对话框
    if (showProcessDialog) {
        Dialog(onDismissRequest = { }) {
            Card {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在处理图像...")
                }
            }
        }
    }
}

@Composable
private fun ProcessButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            shape = MaterialTheme.shapes.medium,
            color = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
