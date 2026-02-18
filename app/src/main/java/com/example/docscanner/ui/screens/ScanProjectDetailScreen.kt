package com.example.docscanner.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.docscanner.data.scanproject.*
import com.example.docscanner.ui.viewmodel.ExportProgressInfo
import com.example.docscanner.ui.viewmodel.ExportType
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanProjectDetailScreen(
    projectName: String,
    project: ScanProjectEntity?,
    images: List<ScanImageEntity>,
    scanner: GmsDocumentScanner?,
    exportProgress: ExportProgressInfo?,
    @Suppress("UNUSED_PARAMETER") exportResult: String?,
    message: String?,  // 添加消息状态
    onAddImages: (List<Uri>) -> Unit,  // 改为批量添加
    onExportPdf: (Uri) -> Unit,
    onExportTextWithAutoOcr: (Uri) -> Unit,
    onDeleteImage: (ScanImageEntity) -> Unit,
    onClearExportProgress: () -> Unit,
    onClearMessage: () -> Unit,  // 添加清除消息回调
    onNavigateToSettings: () -> Unit,  // 添加导航到设置的回调
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp

    // 计算每行显示4张图片时，每张图片的大小（减去padding和间距）
    val horizontalPadding = 32.dp  // 左右各16dp
    val spacing = 8.dp  // 图片间距
    val columns = 4
    val imageSize = (screenWidthDp - horizontalPadding - spacing * (columns - 1)) / columns

    AppLog.i(LogTag.SCAN_PROJECT_UI, "ScanProjectDetailScreen composing for: $projectName")

    // ML Kit 扫描器启动器
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            // 收集所有图片URI，然后批量添加（保持原始顺序）
            val uris = scanningResult?.pages?.map { it.imageUri } ?: emptyList()
            AppLog.i(LogTag.SCAN_PROJECT_UI, "Scanner returned ${uris.size} images")
            if (uris.isNotEmpty()) {
                onAddImages(uris)  // 批量添加，保持顺序
            }
        } else {
            AppLog.w(LogTag.SCAN_PROJECT_UI, "Scan cancelled or failed")
        }
    }

    val projectStatus = project?.let { ProjectStatus.valueOf(it.status) } ?: ProjectStatus.CREATING
    val isOcrProcessing = projectStatus == ProjectStatus.OCR_PROCESSING
    val isExporting = exportProgress != null && !exportProgress.isComplete

    // 导出结果对话框
    var showExportResultDialog by remember { mutableStateOf(false) }
    var lastExportResult by remember { mutableStateOf<String?>(null) }

    // 配置目录提示对话框
    var showConfigDirectoryDialog by remember { mutableStateOf(false) }

    // 全屏图片查看器状态
    var viewingImage by remember { mutableStateOf<ScanImageEntity?>(null) }

    // 监听消息变化，如果是需要配置目录的消息，显示对话框
    LaunchedEffect(message) {
        if (message?.contains("配置导出目录") == true) {
            showConfigDirectoryDialog = true
        }
    }

    // 监听导出完成
    LaunchedEffect(exportProgress) {
        if (exportProgress?.isComplete == true && exportProgress.displayPath != null) {
            lastExportResult = exportProgress.displayPath
            showExportResultDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = projectName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            // 底部进度条
            if (exportProgress != null) {
                BottomProgressIndicator(
                    progress = exportProgress.progress,
                    message = exportProgress.message,
                    type = exportProgress.type
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 状态信息卡片
            StatusCard(
                project = project,
                imageCount = images.size,
                modifier = Modifier.padding(16.dp)
            )

            // 图片预览区
            Text(
                text = "已扫描图片 (${images.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            if (images.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无图片，点击下方按钮开始扫描",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                // 多行网格显示（4列）
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        ImageThumbnail(
                            image = image,
                            size = imageSize,
                            onView = { viewingImage = image },
                            onDelete = { onDeleteImage(image) }
                        )
                    }
                }
            }

            // 操作按钮区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 添加图片按钮
                Button(
                    onClick = {
                        if (activity == null) {
                            AppLog.e(LogTag.SCAN_PROJECT_UI, "Activity is null")
                            return@Button
                        }
                        scanner?.let { gmsScanner ->
                            gmsScanner.getStartScanIntent(activity)
                                .addOnSuccessListener { intentSender: IntentSender ->
                                    val request = IntentSenderRequest.Builder(intentSender).build()
                                    scannerLauncher.launch(request)
                                }
                                .addOnFailureListener { e: Exception ->
                                    AppLog.e(LogTag.SCAN_PROJECT_UI, "Failed to start scanner", e)
                                }
                        } ?: run {
                            AppLog.e(LogTag.SCAN_PROJECT_UI, "Scanner not initialized")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isExporting
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加扫描图片")
                }

                // 导出PDF按钮
                Button(
                    onClick = {
                        scope.launch {
                            // 直接调用导出，目录由 ViewModel 从配置中读取
                            onExportPdf(Uri.EMPTY) // Uri.EMPTY 表示使用保存的目录
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = images.isNotEmpty() && !isOcrProcessing && !isExporting
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出PDF")
                }

                // 导出文本按钮（合并OCR和导出）
                Button(
                    onClick = {
                        scope.launch {
                            onExportTextWithAutoOcr(Uri.EMPTY)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = images.isNotEmpty() && !isOcrProcessing && !isExporting
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("导出文本（自动OCR）")
                }
            }
        }
    }

    // 配置目录提示对话框
    if (showConfigDirectoryDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfigDirectoryDialog = false
                onClearMessage()
            },
            icon = { Icon(Icons.Default.FolderOff, contentDescription = null) },
            title = { Text("需要配置导出目录") },
            text = {
                Text("请先在设置中配置导出目录，然后才能导出文件。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfigDirectoryDialog = false
                        onClearMessage()
                        onNavigateToSettings()
                    }
                ) {
                    Text("去设置")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showConfigDirectoryDialog = false
                        onClearMessage()
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    // 导出结果对话框
    if (showExportResultDialog && lastExportResult != null) {
        // 使用 ViewModel 返回的 displayPath
        val displayPath = lastExportResult ?: ""

        AlertDialog(
            onDismissRequest = {
                showExportResultDialog = false
                onClearExportProgress()
            },
            title = { Text("导出成功") },
            text = {
                Column {
                    Text("文件已保存到：")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExportResultDialog = false
                        onClearExportProgress()
                    }
                ) {
                    Text("确定")
                }
            }
        )
    }

    // 全屏图片查看器
    viewingImage?.let { image ->
        FullScreenImageViewer(
            imagePath = image.filePath,
            pageNumber = image.pageNumber,
            onDismiss = { viewingImage = null }
        )
    }
}

@Composable
private fun BottomProgressIndicator(
    progress: Float,
    message: String,
    type: ExportType
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (type == ExportType.PDF)
                        Icons.Default.PictureAsPdf
                    else
                        Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusCard(
    project: ScanProjectEntity?,
    imageCount: Int,
    modifier: Modifier = Modifier
) {
    val status = project?.let { ProjectStatus.valueOf(it.status) } ?: ProjectStatus.CREATING

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                ProjectStatus.OCR_PAUSED -> MaterialTheme.colorScheme.errorContainer
                ProjectStatus.OCR_PROCESSING -> MaterialTheme.colorScheme.tertiaryContainer
                ProjectStatus.OCR_COMPLETED -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 状态图标
            val (icon, text) = when (status) {
                ProjectStatus.CREATING, ProjectStatus.SCANNING ->
                    Icons.Default.Edit to "扫描中"
                ProjectStatus.COMPLETED ->
                    Icons.Default.CheckCircle to "扫描完成"
                ProjectStatus.OCR_PROCESSING ->
                    Icons.Default.Sync to "OCR处理中"
                ProjectStatus.OCR_PAUSED ->
                    Icons.Default.PauseCircle to "OCR已暂停"
                ProjectStatus.OCR_COMPLETED ->
                    Icons.Default.CheckCircle to "OCR完成"
                ProjectStatus.EXPORTED_PDF ->
                    Icons.Default.DoneAll to "已导出PDF"
                ProjectStatus.EXPORTED_TEXT ->
                    Icons.Default.DoneAll to "已导出文本"
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                if (project != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "图片: $imageCount",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (project.ocrProgress > 0) {
                            Text(
                                text = "OCR进度: ${project.ocrProgress}/$imageCount",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (!project.lastOcrError.isNullOrBlank()) {
                        Text(
                            text = "错误: ${project.lastOcrError}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageThumbnail(
    image: ScanImageEntity,
    size: Dp,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(image.filePath)
                .crossfade(true)
                .build(),
            contentDescription = "Page ${image.pageNumber}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 页码标签
        Surface(
            modifier = Modifier.align(Alignment.BottomStart),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            shape = RoundedCornerShape(topEnd = 8.dp)
        ) {
            Text(
                text = String.format("%04d", image.pageNumber),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // OCR状态指示
        val ocrStatus = ImageOcrStatus.valueOf(image.ocrStatus)
        if (ocrStatus != ImageOcrStatus.PENDING) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd),
                color = when (ocrStatus) {
                    ImageOcrStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    ImageOcrStatus.PROCESSING -> MaterialTheme.colorScheme.tertiary
                    ImageOcrStatus.FAILED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.outline
                },
                shape = RoundedCornerShape(bottomStart = 8.dp)
            ) {
                Icon(
                    imageVector = when (ocrStatus) {
                        ImageOcrStatus.COMPLETED -> Icons.Default.Check
                        ImageOcrStatus.PROCESSING -> Icons.Default.Sync
                        ImageOcrStatus.FAILED -> Icons.Default.Close
                        else -> Icons.AutoMirrored.Filled.Help
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(4.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // 操作按钮层
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 查看按钮
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp)
            ) {
                IconButton(
                    onClick = onView,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = "查看",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 删除按钮
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                shape = RoundedCornerShape(4.dp)
            ) {
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("确认删除") },
            text = { Text("确定要删除第 ${image.pageNumber} 页吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 全屏图片查看器，支持缩放
 */
@Composable
private fun FullScreenImageViewer(
    imagePath: String,
    pageNumber: Int,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.black)
    ) {
        // 图片显示区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(0.5f, 5f)

                        // 只有缩放大于1时才允许移动
                        if (newScale > 1f) {
                            // 计算允许的最大偏移量
                            val maxX = (size.width * (newScale - 1)) / 2
                            val maxY = (size.height * (newScale - 1)) / 2

                            // 确保最大值大于0，避免 coerceIn 崩溃
                            if (maxX > 0 && maxY > 0) {
                                offsetX = (offsetX + pan.x * newScale).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y * newScale).coerceIn(-maxY, maxY)
                            }
                        } else {
                            // 缩放小于等于1时，重置偏移
                            offsetX = 0f
                            offsetY = 0f
                        }

                        scale = newScale
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            // 双击切换缩放
                            if (scale > 1f) {
                                scale = 1f
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                scale = 2f
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imagePath)
                    .crossfade(true)
                    .build(),
                contentDescription = "Page $pageNumber",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            )
        }

        // 顶部工具栏
        Surface(
            modifier = Modifier.align(Alignment.TopCenter),
            color = MaterialTheme.colorScheme.black.copy(alpha = 0.6f)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "第 ${String.format("%04d", pageNumber)} 页",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.white,
                    modifier = Modifier.weight(1f)
                )

                // 缩放比例
                Text(
                    text = "${(scale * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.white,
                    modifier = Modifier.padding(end = 16.dp)
                )

                // 关闭按钮
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = MaterialTheme.colorScheme.white
                    )
                }
            }
        }

        // 底部提示
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter),
            color = MaterialTheme.colorScheme.black.copy(alpha = 0.6f)
        ) {
            Text(
                text = "双指缩放 | 双击切换 | 滑动移动",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.white.copy(alpha = 0.7f),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 12.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
        }
    }
}

// 扩展属性：黑色颜色
private val ColorScheme.black: Color
    get() = Color.Black

private val ColorScheme.white: Color
    get() = Color.White
