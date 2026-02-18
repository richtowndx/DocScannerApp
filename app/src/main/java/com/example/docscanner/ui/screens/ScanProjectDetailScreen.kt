package com.example.docscanner.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    onAddImages: (List<Uri>) -> Unit,  // 改为批量添加
    onExportPdf: (Uri) -> Unit,
    onExportTextWithAutoOcr: (Uri) -> Unit,
    onDeleteImage: (ScanImageEntity) -> Unit,
    onClearExportProgress: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

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
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(images, key = { it.id }) { image ->
                        ImageThumbnail(
                            image = image,
                            onDelete = { onDeleteImage(image) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

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
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(RoundedCornerShape(12.dp))
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

        // 删除按钮
        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.error
            )
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
