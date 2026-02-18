package com.example.docscanner.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.config.ConfigRepository
import com.example.docscanner.data.scanproject.*
import com.example.docscanner.scanner.ScannerConfig
import com.example.docscanner.scanner.ScannerEngine
import com.example.docscanner.scanner.ScannerFactory
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 导出进度信息
 */
data class ExportProgressInfo(
    val type: ExportType,
    val progress: Float, // 0.0 - 1.0
    val message: String,
    val isComplete: Boolean = false,
    val outputPath: String? = null,
    val displayPath: String? = null // 用于显示的友好路径
)

enum class ExportType {
    PDF,
    TEXT
}

/**
 * 从 URI 中提取友好的显示路径
 * URI 格式示例: content://com.android.externalstorage.documents/tree/primary%3ADownload%2Fnote/document/primary%3ADownload%2Fnote%2Ftest.pdf
 * lastPathSegment: primary:Download/note/test.pdf
 */
fun getDisplayPathFromUri(uri: Uri, fileName: String): String {
    // 优先使用 lastPathSegment，它包含完整路径
    val lastSegment = uri.lastPathSegment
    if (!lastSegment.isNullOrBlank()) {
        val decoded = java.net.URLDecoder.decode(lastSegment, "UTF-8")
        AppLog.d(LogTag.FILE_STORAGE, "getDisplayPathFromUri: lastSegment=$lastSegment, decoded=$decoded")

        // 格式通常是: primary:Download/note/文件名.pdf
        // 去掉 primary: 前缀
        val pathWithoutPrefix = decoded.substringAfter(":", decoded)
        AppLog.d(LogTag.FILE_STORAGE, "getDisplayPathFromUri: pathWithoutPrefix=$pathWithoutPrefix")

        // 如果路径已经包含文件名，直接返回
        if (pathWithoutPrefix.endsWith(fileName) || pathWithoutPrefix.contains(fileName.substringBeforeLast("."))) {
            return "/$pathWithoutPrefix"
        }

        // 否则拼接文件名
        return "/$pathWithoutPrefix/$fileName"
    }

    // 备用方案：从 path 中提取
    val path = uri.path ?: return fileName
    AppLog.d(LogTag.FILE_STORAGE, "getDisplayPathFromUri: path=$path")

    val segments = path.split("/")
    val docIndex = segments.indexOfFirst { it == "document" }

    if (docIndex >= 0 && docIndex + 1 < segments.size) {
        // 合并 document 后面的所有段
        val docPath = segments.subList(docIndex + 1, segments.size).joinToString("/")
        val decoded = java.net.URLDecoder.decode(docPath, "UTF-8")
        val pathWithoutPrefix = decoded.substringAfter(":", decoded)
        return "/$pathWithoutPrefix"
    }

    return fileName
}

class ScanProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val repository = ScanProjectRepository.getInstance(context)
    private val scanProjectService = ScanProjectService.getInstance(context)
    private val pdfGenerator = PdfGenerator.getInstance(context)
    private val ocrBatchProcessor = OcrBatchProcessor.getInstance(context)
    private val configRepository = ConfigRepository.getInstance(context)

    // ML Kit Scanner - 延迟初始化
    private var _scanner: GmsDocumentScanner? = null
    val scanner: GmsDocumentScanner?
        get() = _scanner

    init {
        // 在初始化时创建 scanner
        initScanner()
    }

    private fun initScanner() {
        viewModelScope.launch {
            try {
                val config = ScannerConfig(
                    engine = ScannerEngine.ML_KIT,
                    allowGalleryImport = true,
                    pageLimit = 100
                )
                val adapter = ScannerFactory.getAdapter(ScannerEngine.ML_KIT)
                _scanner = adapter.prepareScannerIntent(context, config) as? GmsDocumentScanner
                AppLog.i(LogTag.SCAN_PROJECT_UI, "Scanner initialized")
            } catch (e: Exception) {
                AppLog.e(LogTag.SCAN_PROJECT_UI, "Failed to initialize scanner", e)
            }
        }
    }

    // 所有项目列表
    val projects: StateFlow<List<ScanProjectEntity>> = repository.getAllProjectsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 缓存的项目详情
    private val projectCache = mutableMapOf<String, ScanProjectEntity?>()

    // 缓存的图片列表
    private val imageCache = mutableMapOf<String, List<ScanImageEntity>>()

    // UI 状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // OCR 处理状态
    private val _ocrProgress = MutableStateFlow<OcrProgressInfo?>(null)
    val ocrProgress: StateFlow<OcrProgressInfo?> = _ocrProgress.asStateFlow()

    // 导出进度状态
    private val _exportProgress = MutableStateFlow<ExportProgressInfo?>(null)
    val exportProgress: StateFlow<ExportProgressInfo?> = _exportProgress.asStateFlow()

    // 导出结果（成功后的文件路径）
    private val _exportResult = MutableStateFlow<String?>(null)
    val exportResult: StateFlow<String?> = _exportResult.asStateFlow()

    /**
     * 获取单个项目
     */
    fun getProject(name: String): Flow<ScanProjectEntity?> {
        return repository.getProjectByNameFlow(name).onEach {
            projectCache[name] = it
        }
    }

    /**
     * 获取项目的图片列表
     */
    fun getProjectImages(projectName: String): Flow<List<ScanImageEntity>> {
        return repository.getImagesByProjectFlow(projectName).onEach {
            imageCache[projectName] = it
        }
    }

    /**
     * 创建新项目
     */
    fun createProject(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = scanProjectService.createProject(name)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    _message.value = "项目创建成功"
                    AppLog.i(LogTag.SCAN_PROJECT_UI, "Project created: $name")
                },
                onFailure = { error ->
                    _message.value = "创建失败: ${error.message}"
                    AppLog.e(LogTag.SCAN_PROJECT_UI, "Failed to create project: $name", error)
                }
            )
        }
    }

    /**
     * 删除项目
     */
    fun deleteProject(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = scanProjectService.deleteProject(name)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    _message.value = "项目已删除"
                    projectCache.remove(name)
                    imageCache.remove(name)
                },
                onFailure = { error ->
                    _message.value = "删除失败: ${error.message}"
                }
            )
        }
    }

    /**
     * 添加图片到项目
     */
    fun addImageToProject(projectName: String, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = scanProjectService.addImageFromUri(projectName, uri)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    _message.value = "图片已添加"
                    // 更新项目状态为扫描中
                    repository.updateProjectStatus(projectName, ProjectStatus.SCANNING)
                },
                onFailure = { error ->
                    _message.value = "添加图片失败: ${error.message}"
                }
            )
        }
    }

    /**
     * 批量添加图片到项目（高性能版本）
     *
     * 优化策略：
     * - 并行预加载所有图片（利用多核CPU）
     * - 串行保存文件（保证页码顺序）
     */
    fun addImagesToProject(projectName: String, uris: List<Uri>) {
        viewModelScope.launch {
            _isLoading.value = true
            AppLog.i(LogTag.SCAN_PROJECT_UI, "=== Batch adding ${uris.size} images (optimized) ===")

            // 使用优化后的批量添加方法
            val (successCount, failCount) = scanProjectService.addImagesFromUris(projectName, uris)

            _isLoading.value = false

            AppLog.i(LogTag.SCAN_PROJECT_UI, "=== Batch add complete: $successCount success, $failCount failed ===")

            if (failCount == 0) {
                _message.value = "已添加 $successCount 张图片"
            } else {
                _message.value = "添加完成: $successCount 成功, $failCount 失败"
            }

            // 更新项目状态为扫描中
            if (successCount > 0) {
                repository.updateProjectStatus(projectName, ProjectStatus.SCANNING)
            }
        }
    }

    /**
     * 删除图片
     */
    fun deleteImage(image: ScanImageEntity) {
        viewModelScope.launch {
            _isLoading.value = true
            val result = scanProjectService.deleteImage(image)
            _isLoading.value = false

            result.fold(
                onSuccess = {
                    _message.value = "图片已删除"
                },
                onFailure = { error ->
                    _message.value = "删除失败: ${error.message}"
                }
            )
        }
    }

    /**
     * 导出 PDF 到指定目录
     */
    fun exportPdf(projectName: String, exportDirectoryUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _exportProgress.value = ExportProgressInfo(ExportType.PDF, 0f, "正在准备PDF生成...")

            AppLog.i(LogTag.SCAN_PROJECT_UI, "=== exportPdf called ===")
            AppLog.i(LogTag.SCAN_PROJECT_UI, "projectName: $projectName")
            AppLog.i(LogTag.SCAN_PROJECT_UI, "exportDirectoryUri: $exportDirectoryUri")

            // 首先生成 PDF 到应用私有目录
            val result = pdfGenerator.generatePdf(
                projectName = projectName,
                callback = object : PdfGenerator.ProgressCallback {
                    override fun onProgress(current: Int, total: Int, message: String) {
                        val progress = if (total > 0) current.toFloat() / total else 0f
                        _exportProgress.value = ExportProgressInfo(
                            ExportType.PDF,
                            progress * 0.8f, // 生成占80%
                            "$message ($current/$total)"
                        )
                    }

                    override fun onComplete(outputPath: String) {
                        AppLog.i(LogTag.SCAN_PROJECT_UI, "PDF generated: $outputPath")
                    }

                    override fun onError(error: Throwable) {
                        AppLog.e(LogTag.SCAN_PROJECT_UI, "PDF generation error: ${error.message}")
                        _exportProgress.value = null
                        _message.value = "PDF生成失败: ${error.message}"
                    }
                }
            )

            result.fold(
                onSuccess = { pdfPath ->
                    AppLog.i(LogTag.SCAN_PROJECT_UI, "PDF generated at: $pdfPath, now copying to user directory")
                    _exportProgress.value = ExportProgressInfo(ExportType.PDF, 0.9f, "正在导出到目录...")
                    // 复制到用户选择的目录
                    val exportResult = copyFileToDirectory(pdfPath, exportDirectoryUri, "$projectName.pdf")
                    exportResult.fold(
                        onSuccess = { createdFileUri ->
                            val displayPath = getDisplayPathFromUri(createdFileUri, "$projectName.pdf")
                            _exportProgress.value = ExportProgressInfo(
                                ExportType.PDF,
                                1f,
                                "导出完成",
                                isComplete = true,
                                outputPath = createdFileUri.toString(),
                                displayPath = displayPath
                            )
                            repository.updateProjectStatus(projectName, ProjectStatus.EXPORTED_PDF)
                            _exportResult.value = createdFileUri.toString()
                            AppLog.i(LogTag.SCAN_PROJECT_UI, "PDF exported successfully, displayPath: $displayPath")
                        },
                        onFailure = { error ->
                            _exportProgress.value = null
                            _message.value = "导出失败: ${error.message}"
                            AppLog.e(LogTag.SCAN_PROJECT_UI, "Failed to export PDF", error)
                        }
                    )
                },
                onFailure = { error ->
                    _exportProgress.value = null
                    _message.value = "PDF生成失败: ${error.message}"
                    AppLog.e(LogTag.SCAN_PROJECT_UI, "Failed to generate PDF", error)
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 导出 Markdown 到指定目录
     */
    fun exportMarkdown(projectName: String, directoryUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _exportProgress.value = ExportProgressInfo(ExportType.TEXT, 0f, "正在生成文本文件...")

            AppLog.i(LogTag.SCAN_PROJECT_UI, "exportMarkdown called: projectName=$projectName, directoryUri=$directoryUri")

            // 首先生成 Markdown 到应用私有目录
            val result = ocrBatchProcessor.generateMarkdownFile(projectName)

            result.fold(
                onSuccess = { file ->
                    AppLog.i(LogTag.SCAN_PROJECT_UI, "Markdown generated at: ${file.absolutePath}, now copying to user directory")
                    _exportProgress.value = ExportProgressInfo(ExportType.TEXT, 0.9f, "正在导出到目录...")
                    // 复制到用户选择的目录
                    val exportResult = copyFileToDirectory(file.absolutePath, directoryUri, "$projectName.md")
                    exportResult.fold(
                        onSuccess = { exportUri ->
                            val displayPath = getDisplayPathFromUri(exportUri, "$projectName.md")
                            _exportProgress.value = ExportProgressInfo(
                                ExportType.TEXT,
                                1f,
                                "导出完成",
                                isComplete = true,
                                outputPath = exportUri.toString(),
                                displayPath = displayPath
                            )
                            repository.updateProjectStatus(projectName, ProjectStatus.EXPORTED_TEXT)
                            _exportResult.value = exportUri.toString()
                            AppLog.i(LogTag.SCAN_PROJECT_UI, "Markdown exported successfully to: $exportUri, display: $displayPath")
                        },
                        onFailure = { error ->
                            _exportProgress.value = null
                            _message.value = "导出失败: ${error.message}"
                            AppLog.e(LogTag.SCAN_PROJECT_UI, "Failed to export Markdown", error)
                        }
                    )
                },
                onFailure = { error ->
                    _exportProgress.value = null
                    _message.value = "生成失败: ${error.message}"
                    AppLog.e(LogTag.SCAN_PROJECT_UI, "Failed to generate Markdown", error)
                }
            )

            _isLoading.value = false
        }
    }

    /**
     * 导出文本（自动执行OCR + 导出）
     * 如果OCR未完成，先执行OCR，然后导出
     */
    fun exportTextWithAutoOcr(projectName: String, directoryUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true

            // 检查项目状态
            val project = repository.getProjectByName(projectName)
            val projectStatus = project?.let { ProjectStatus.valueOf(it.status) } ?: ProjectStatus.CREATING

            if (projectStatus != ProjectStatus.OCR_COMPLETED) {
                // 需要先执行OCR
                _exportProgress.value = ExportProgressInfo(ExportType.TEXT, 0f, "正在OCR识别...")

                val ocrResult = ocrBatchProcessor.processProject(
                    projectName = projectName,
                    callback = object : OcrBatchProcessor.ProgressCallback {
                        override fun onProgress(current: Int, total: Int, imageName: String) {
                            val progress = if (total > 0) current.toFloat() / total else 0f
                            _exportProgress.value = ExportProgressInfo(
                                ExportType.TEXT,
                                progress * 0.7f, // OCR占70%
                                "OCR处理 $imageName ($current/$total)"
                            )
                            viewModelScope.launch {
                                _ocrProgress.value = ocrBatchProcessor.getOcrProgress(projectName)
                            }
                        }

                        override fun onImageComplete(imageId: Long, pageNumber: Int, success: Boolean) {
                            if (success) {
                                AppLog.i(LogTag.SCAN_PROJECT_UI, "OCR completed for page $pageNumber")
                            } else {
                                AppLog.w(LogTag.SCAN_PROJECT_UI, "OCR failed for page $pageNumber")
                            }
                        }

                        override fun onProjectComplete(projectName: String, successCount: Int, failedCount: Int) {
                            _exportProgress.value = ExportProgressInfo(
                                ExportType.TEXT,
                                0.7f,
                                "OCR完成，正在生成文本..."
                            )
                            _ocrProgress.value = null
                        }

                        override fun onError(projectName: String, error: String) {
                            _exportProgress.value = null
                            _message.value = "OCR出错: $error"
                        }
                    }
                )

                ocrResult.fold(
                    onSuccess = { batchResult ->
                        if (batchResult.failedCount == 0) {
                            repository.updateProjectStatus(projectName, ProjectStatus.OCR_COMPLETED)
                            // OCR完成，继续导出
                            exportMarkdownInternal(projectName, directoryUri)
                        } else {
                            _exportProgress.value = null
                            _message.value = "OCR部分失败: ${batchResult.successCount}成功, ${batchResult.failedCount}失败"
                        }
                    },
                    onFailure = { error ->
                        _exportProgress.value = null
                        _message.value = "OCR处理失败: ${error.message}"
                    }
                )
            } else {
                // OCR已完成，直接导出
                exportMarkdownInternal(projectName, directoryUri)
            }

            _isLoading.value = false
        }
    }

    /**
     * 内部方法：导出Markdown
     */
    private suspend fun exportMarkdownInternal(projectName: String, directoryUri: Uri) {
        _exportProgress.value = ExportProgressInfo(ExportType.TEXT, 0.8f, "正在生成文本文件...")

        val result = ocrBatchProcessor.generateMarkdownFile(projectName)

        result.fold(
            onSuccess = { file ->
                _exportProgress.value = ExportProgressInfo(ExportType.TEXT, 0.9f, "正在导出到目录...")
                val exportResult = copyFileToDirectory(file.absolutePath, directoryUri, "$projectName.md")
                exportResult.fold(
                    onSuccess = { exportUri ->
                        val displayPath = getDisplayPathFromUri(exportUri, "$projectName.md")
                        _exportProgress.value = ExportProgressInfo(
                            ExportType.TEXT,
                            1f,
                            "导出完成",
                            isComplete = true,
                            outputPath = exportUri.toString(),
                            displayPath = displayPath
                        )
                        repository.updateProjectStatus(projectName, ProjectStatus.EXPORTED_TEXT)
                        _exportResult.value = exportUri.toString()
                    },
                    onFailure = { error ->
                        _exportProgress.value = null
                        _message.value = "导出失败: ${error.message}"
                    }
                )
            },
            onFailure = { error ->
                _exportProgress.value = null
                _message.value = "生成失败: ${error.message}"
            }
        )
    }

    /**
     * 获取保存的导出目录
     */
    suspend fun getExportDirectory(): Uri? {
        val uriString = configRepository.getExportDirectoryUri()
        return uriString?.let { Uri.parse(it) }
    }

    /**
     * 清除导出进度
     */
    fun clearExportProgress() {
        _exportProgress.value = null
    }

    /**
     * 清除导出结果
     */
    fun clearExportResult() {
        _exportResult.value = null
    }

    /**
     * 使用保存的目录导出PDF
     */
    fun exportPdfWithSavedDir(projectName: String) {
        viewModelScope.launch {
            val exportDirectoryUri = getExportDirectory()
            AppLog.i(LogTag.FILE_STORAGE, "=== exportPdfWithSavedDir START ===")
            AppLog.i(LogTag.FILE_STORAGE, "projectName: $projectName")
            AppLog.i(LogTag.FILE_STORAGE, "exportDirectoryUri: $exportDirectoryUri")
            AppLog.i(LogTag.FILE_STORAGE, "exportDirectoryUri.path: ${exportDirectoryUri?.path}")
            AppLog.i(LogTag.FILE_STORAGE, "exportDirectoryUri.lastPathSegment: ${exportDirectoryUri?.lastPathSegment}")

            if (exportDirectoryUri == null) {
                _message.value = "请先在设置中配置导出目录"
                return@launch
            }
            exportPdf(projectName, exportDirectoryUri)
        }
    }

    /**
     * 使用保存的目录导出文本（自动OCR）
     */
    fun exportTextWithAutoOcrWithSavedDir(projectName: String) {
        viewModelScope.launch {
            val exportDirectoryUri = getExportDirectory()
            AppLog.i(LogTag.FILE_STORAGE, "=== exportTextWithAutoOcrWithSavedDir START ===")
            AppLog.i(LogTag.FILE_STORAGE, "projectName: $projectName")
            AppLog.i(LogTag.FILE_STORAGE, "exportDirectoryUri: $exportDirectoryUri")
            AppLog.i(LogTag.FILE_STORAGE, "exportDirectoryUri.path: ${exportDirectoryUri?.path}")
            AppLog.i(LogTag.FILE_STORAGE, "exportDirectoryUri.lastPathSegment: ${exportDirectoryUri?.lastPathSegment}")

            if (exportDirectoryUri == null) {
                _message.value = "请先在设置中配置导出目录"
                return@launch
            }
            exportTextWithAutoOcr(projectName, exportDirectoryUri)
        }
    }

    /**
     * 复制文件到用户选择的目录
     */
    private suspend fun copyFileToDirectory(
        sourcePath: String,
        exportDirectoryUri: Uri,
        fileName: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            AppLog.i(LogTag.FILE_STORAGE, "=== copyFileToDirectory START ===")
            AppLog.i(LogTag.FILE_STORAGE, "sourcePath: $sourcePath")
            AppLog.i(LogTag.FILE_STORAGE, "exportDirectoryUri: $exportDirectoryUri")
            AppLog.i(LogTag.FILE_STORAGE, "fileName: $fileName")

            val sourceFile = File(sourcePath)
            if (!sourceFile.exists()) {
                AppLog.e(LogTag.FILE_STORAGE, "Source file not found: $sourcePath")
                return@withContext Result.failure(Exception("源文件不存在: $sourcePath"))
            }

            AppLog.i(LogTag.FILE_STORAGE, "Source file size: ${sourceFile.length()} bytes")

            val contentResolver = context.contentResolver

            // 获取持久化权限
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            contentResolver.takePersistableUriPermission(exportDirectoryUri, takeFlags)

            // 确定 MIME 类型
            val mimeType = when (fileName.substringAfterLast(".")) {
                "pdf" -> "application/pdf"
                "md" -> "text/markdown"
                "txt" -> "text/plain"
                else -> "application/octet-stream"
            }
            AppLog.i(LogTag.FILE_STORAGE, "Using MIME type: $mimeType")

            // 从 tree URI 获取文档 ID
            val treeDocumentId = DocumentsContract.getTreeDocumentId(exportDirectoryUri)
            AppLog.i(LogTag.FILE_STORAGE, "treeDocumentId: $treeDocumentId")

            // 构建可以创建文档的 URI - 这个 URI 指向用户选择的目录
            val targetDirectoryUri = DocumentsContract.buildDocumentUriUsingTree(exportDirectoryUri, treeDocumentId)
            AppLog.i(LogTag.FILE_STORAGE, "targetDirectoryUri: $targetDirectoryUri")

            // 使用 Storage Access Framework 创建文件
            val createdFileUri = DocumentsContract.createDocument(
                contentResolver,
                targetDirectoryUri,
                mimeType,
                fileName
            )

            if (createdFileUri == null) {
                AppLog.e(LogTag.FILE_STORAGE, "Failed to create document: DocumentsContract.createDocument returned null")
                return@withContext Result.failure(Exception("无法创建文件: $fileName"))
            }

            AppLog.i(LogTag.FILE_STORAGE, "createdFileUri: $createdFileUri")

            // 复制文件内容
            FileInputStream(sourcePath).use { input ->
                contentResolver.openOutputStream(createdFileUri)?.use { output ->
                    val bytesCopied = input.copyTo(output)
                    AppLog.i(LogTag.FILE_STORAGE, "Copied $bytesCopied bytes")
                } ?: run {
                    AppLog.e(LogTag.FILE_STORAGE, "Failed to open output stream for URI: $createdFileUri")
                    return@withContext Result.failure(Exception("无法打开输出流"))
                }
            }

            AppLog.i(LogTag.FILE_STORAGE, "=== copyFileToDirectory SUCCESS ===")
            AppLog.i(LogTag.FILE_STORAGE, "File copied to: $createdFileUri")
            Result.success(createdFileUri)
        } catch (e: Exception) {
            AppLog.e(LogTag.FILE_STORAGE, "Failed to copy file: $sourcePath", e)
            Result.failure(e)
        }
    }

    /**
     * 开始 OCR 处理
     */
    fun startOcr(projectName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _message.value = "正在OCR处理..."

            val result = ocrBatchProcessor.processProject(
                projectName = projectName,
                callback = object : OcrBatchProcessor.ProgressCallback {
                    override fun onProgress(current: Int, total: Int, imageName: String) {
                        _message.value = "处理 $imageName ($current/$total)"
                        viewModelScope.launch {
                            _ocrProgress.value = ocrBatchProcessor.getOcrProgress(projectName)
                        }
                    }

                    override fun onImageComplete(imageId: Long, pageNumber: Int, success: Boolean) {
                        if (success) {
                            AppLog.i(LogTag.SCAN_PROJECT_UI, "OCR completed for page $pageNumber")
                        } else {
                            AppLog.w(LogTag.SCAN_PROJECT_UI, "OCR failed for page $pageNumber")
                        }
                    }

                    override fun onProjectComplete(projectName: String, successCount: Int, failedCount: Int) {
                        _message.value = "OCR完成: $successCount 成功, $failedCount 失败"
                        _ocrProgress.value = null
                    }

                    override fun onError(projectName: String, error: String) {
                        _message.value = "OCR出错: $error"
                    }
                }
            )

            _isLoading.value = false

            result.fold(
                onSuccess = { batchResult ->
                    if (batchResult.failedCount == 0) {
                        repository.updateProjectStatus(projectName, ProjectStatus.OCR_COMPLETED)
                    }
                },
                onFailure = { error ->
                    _message.value = "OCR处理失败: ${error.message}"
                }
            )
        }
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _message.value = null
    }

    override fun onCleared() {
        super.onCleared()
        // GmsDocumentScanner 会被自动清理，无需手动关闭
        _scanner = null
    }
}

class ScanProjectViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScanProjectViewModel::class.java)) {
            return ScanProjectViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
