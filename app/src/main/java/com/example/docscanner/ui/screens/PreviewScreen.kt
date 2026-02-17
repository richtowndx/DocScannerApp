package com.example.docscanner.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.docscanner.scanner.*
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

private const val TAG = "PreviewScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    imageUri: String,
    engine: ScannerEngine,
    onProcessImage: (String) -> Unit,
    onStartOCR: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isProcessing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showProcessDialog by remember { mutableStateOf(false) }

    // 边框检测相关
    var detectedCorners by remember { mutableStateOf<List<PointF>?>(null) }
    var adjustedCorners by remember { mutableStateOf<List<PointF>?>(null) }
    var imageDisplayRect by remember { mutableStateOf(Rect(0f, 0f, 0f, 0f)) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 处理选项
    var enableGrayscale by remember { mutableStateOf(false) }
    var enableSharpen by remember { mutableStateOf(false) }
    var enableAutoEnhance by remember { mutableStateOf(true) }
    var brightness by remember { mutableStateOf(1.0f) }
    var contrast by remember { mutableStateOf(1.0f) }

    val scannerAdapter = remember { ScannerFactory.getAdapter(engine) }

    // 加载图片并自动检测边框
    LaunchedEffect(imageUri, engine) {
        AppLog.i(LogTag.UI_PREVIEW, "Loading image: $imageUri with engine: $engine")
        isLoading = true
        loadError = null

        try {
            val uri = Uri.parse(imageUri)
            val inputStream: InputStream? = when (uri.scheme) {
                "file" -> {
                    val file = File(uri.path ?: "")
                    if (file.exists()) FileInputStream(file) else null
                }
                "content" -> context.contentResolver.openInputStream(uri)
                else -> null
            }

            if (inputStream != null) {
                originalBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (originalBitmap != null) {
                    AppLog.i(LogTag.UI_PREVIEW, "Image loaded: ${originalBitmap!!.width}x${originalBitmap!!.height}")
                    displayBitmap = originalBitmap

                    // 自动检测边框（仅对 OpenCV 和 SmartCropper）
                    if (engine != ScannerEngine.ML_KIT) {
                        AppLog.i(LogTag.UI_PREVIEW, "Starting auto edge detection...")
                        val corners = scannerAdapter.detectDocumentEdges(originalBitmap!!)
                        if (corners != null && corners.size == 4) {
                            detectedCorners = corners
                            adjustedCorners = corners
                            AppLog.i(LogTag.UI_PREVIEW, "Edge detected: ${corners.joinToString { "(${it.x}, ${it.y})" }}")
                        } else {
                            AppLog.w(LogTag.UI_PREVIEW, "No edges detected, using full image")
                            // 使用整个图片作为默认边框
                            val w = originalBitmap!!.width.toFloat()
                            val h = originalBitmap!!.height.toFloat()
                            val defaultCorners = listOf(
                                PointF(0f, 0f),
                                PointF(w, 0f),
                                PointF(w, h),
                                PointF(0f, h)
                            )
                            detectedCorners = defaultCorners
                            adjustedCorners = defaultCorners
                        }
                    }
                } else {
                    loadError = "无法解码图片"
                }
            } else {
                loadError = "无法打开图片文件"
            }
        } catch (e: Exception) {
            AppLog.e(LogTag.UI_PREVIEW, "Error loading image", e)
            loadError = "加载图片失败: ${e.message}"
        }
        isLoading = false
    }

    // 执行透视校正和图像处理
    fun applyFullProcessing() {
        val bitmap = originalBitmap ?: return
        val corners = adjustedCorners ?: return

        scope.launch {
            isProcessing = true
            showProcessDialog = true

            try {
                AppLog.i(LogTag.UI_PREVIEW, "Starting full processing...")

                // 1. 透视校正
                var resultBitmap = withContext(Dispatchers.Default) {
                    AppLog.i(LogTag.UI_PREVIEW, "Applying perspective correction...")
                    scannerAdapter.applyPerspectiveCorrection(bitmap, corners)
                }

                // 2. 图像处理
                val options = ProcessOptions(
                    enableGrayscale = enableGrayscale,
                    enableSharpen = enableSharpen,
                    enableAutoEnhance = enableAutoEnhance,
                    brightness = brightness,
                    contrast = contrast
                )

                resultBitmap = withContext(Dispatchers.Default) {
                    AppLog.i(LogTag.UI_PREVIEW, "Applying image enhancement...")
                    scannerAdapter.applyImageProcessing(resultBitmap, options)
                }

                processedBitmap = resultBitmap
                displayBitmap = resultBitmap
                AppLog.i(LogTag.UI_PREVIEW, "Processing complete: ${resultBitmap.width}x${resultBitmap.height}")

            } catch (e: Exception) {
                AppLog.e(LogTag.UI_PREVIEW, "Processing failed", e)
            }

            isProcessing = false
            showProcessDialog = false
        }
    }

    // 绘制带边框的图片
    val bitmapWithOverlay = remember(displayBitmap, adjustedCorners, imageDisplayRect, containerSize) {
        if (displayBitmap == null) return@remember null

        if (engine == ScannerEngine.ML_KIT || adjustedCorners == null || containerSize == IntSize.Zero) {
            return@remember displayBitmap
        }

        // 计算缩放比例
        val scaleX = containerSize.width.toFloat() / displayBitmap!!.width
        val scaleY = containerSize.height.toFloat() / displayBitmap!!.height
        val scale = minOf(scaleX, scaleY)

        // 计算图片在容器中的偏移（保留用于未来使用）
        val scaledWidth = displayBitmap!!.width * scale
        val scaledHeight = displayBitmap!!.height * scale
        @Suppress("UNUSED_VARIABLE")
        val offsetX = (containerSize.width - scaledWidth) / 2f
        @Suppress("UNUSED_VARIABLE")
        val offsetY = (containerSize.height - scaledHeight) / 2f

        // 创建带边框的位图
        val result = displayBitmap!!.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        // 将屏幕坐标转换为图片坐标
        val cornersInImage = adjustedCorners!!.map { corner ->
            PointF(corner.x, corner.y)
        }

        // 绘制边框
        val paint = Paint().apply {
            color = Color.GREEN
            strokeWidth = 8f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
        }

        val path = android.graphics.Path()
        path.moveTo(cornersInImage[0].x, cornersInImage[0].y)
        for (i in 1 until cornersInImage.size) {
            path.lineTo(cornersInImage[i].x, cornersInImage[i].y)
        }
        path.close()
        canvas.drawPath(path, paint)

        // 绘制角点
        val cornerPaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        cornersInImage.forEach { corner ->
            canvas.drawCircle(corner.x, corner.y, 20f, cornerPaint)
        }

        result
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预览 - ${engine.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        @Suppress("DEPRECATION")
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("正在加载图片并检测边框...")
                }
            }
        } else if (loadError != null) {
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
                        imageVector = Icons.Default.BrokenImage,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(loadError!!, color = MaterialTheme.colorScheme.error)
                    Button(onClick = onBack) { Text("返回") }
                }
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
                        .onGloballyPositioned { coordinates ->
                            containerSize = coordinates.size
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val showBitmap = bitmapWithOverlay ?: displayBitmap
                    showBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "扫描预览",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // 显示检测状态
                    if (engine != ScannerEngine.ML_KIT && adjustedCorners != null) {
                        Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "边框已检测",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
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
                                icon = Icons.Default.Crop,
                                label = "透视校正",
                                active = adjustedCorners != null && engine != ScannerEngine.ML_KIT,
                                onClick = { }
                            )
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
                                onClick = { applyFullProcessing() },
                                modifier = Modifier.weight(1f),
                                enabled = !isProcessing
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("处理图片")
                            }

                            Button(
                                onClick = {
                                    // 保存处理后的图片
                                    processedBitmap?.let { bitmap ->
                                        scope.launch {
                                            try {
                                                val file = File.createTempFile(
                                                    "processed_${System.currentTimeMillis()}",
                                                    ".jpg",
                                                    context.cacheDir
                                                )
                                                FileOutputStream(file).use { out ->
                                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                                }
                                                onProcessImage(Uri.fromFile(file).toString())
                                                onStartOCR()
                                            } catch (e: Exception) {
                                                AppLog.e(LogTag.UI_PREVIEW, "Failed to save processed image", e)
                                            }
                                        }
                                    } ?: run {
                                        // 如果没有处理过，直接使用原图
                                        onStartOCR()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = displayBitmap != null
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
                    Text(
                        text = "边框检测 → 透视校正 → 图像增强",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
