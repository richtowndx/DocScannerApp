package com.example.docscanner.ui.screens

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.docscanner.scanner.*
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

private const val TAG = "ScannerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    engine: ScannerEngine,
    onScanComplete: (String) -> Unit,
    onBack: () -> Unit
) {
    AppLog.i(LogTag.UI_SCANNER, "ScannerScreen composing, engine=$engine")

    val context = LocalContext.current
    val activity = context as? Activity

    AppLog.d(LogTag.UI_SCANNER, "Context class: ${context.javaClass.name}, activity=$activity")

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var scannerReady by remember { mutableStateOf(false) }

    val scannerAdapter = remember { ScannerFactory.getAdapter(engine) }
    AppLog.d(LogTag.UI_SCANNER, "ScannerAdapter: ${scannerAdapter.javaClass.name}")

    // ML Kit 扫描器配置
    val mlKitOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    val mlKitScanner = remember { GmsDocumentScanning.getClient(mlKitOptions) }

    // ML Kit 扫描结果处理
    val mlKitLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        AppLog.i(LogTag.UI_SCANNER, "ML Kit scanner result: resultCode=${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    val imageUri = pages.first().imageUri
                    AppLog.i(LogTag.UI_SCANNER, "ML Kit scan complete: $imageUri")
                    onScanComplete(imageUri.toString())
                }
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            AppLog.i(LogTag.UI_SCANNER, "ML Kit scan cancelled")
        }
    }

    LaunchedEffect(engine) {
        try {
            scannerReady = scannerAdapter.isAvailable()
            isLoading = false
            AppLog.i(LogTag.UI_SCANNER, "Engine $engine ready: $scannerReady")
        } catch (e: Exception) {
            AppLog.e(LogTag.UI_SCANNER, "Engine check failed", e)
            error = e.message
            isLoading = false
        }
    }

    // 启动 ML Kit 扫描
    fun startMLKitScanning() {
        AppLog.i(LogTag.UI_SCANNER, "startMLKitScanning() called")
        AppLog.d(LogTag.UI_SCANNER, "activity=$activity, scannerReady=$scannerReady")

        if (activity == null) {
            AppLog.e(LogTag.UI_SCANNER, "Activity is null, cannot start scanner")
            error = "无法获取Activity"
            return
        }

        try {
            AppLog.i(LogTag.UI_SCANNER, "Calling mlKitScanner.getStartScanIntent()...")
            mlKitScanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender: IntentSender ->
                    AppLog.i(LogTag.UI_SCANNER, "getStartScanIntent success, launching scanner...")
                    val request = IntentSenderRequest.Builder(intentSender).build()
                    mlKitLauncher.launch(request)
                    AppLog.i(LogTag.UI_SCANNER, "Scanner activity launched")
                }
                .addOnFailureListener { e: Exception ->
                    AppLog.e(LogTag.UI_SCANNER, "Failed to start ML Kit scanner", e)
                    error = "启动扫描器失败: ${e.message}"
                }
        } catch (e: Exception) {
            AppLog.e(LogTag.UI_SCANNER, "Error starting ML Kit scanner", e)
            error = "启动扫描器失败: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描文档") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("正在初始化扫描器...")
                    }
                }

                error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "错误: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = onBack) {
                            Text("返回")
                        }
                    }
                }

                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // 引擎信息
                        Card(
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "当前引擎: ML Kit",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (scannerReady) "就绪" else "不可用",
                                    color = if (scannerReady) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }

                        // ML Kit 使用内置扫描器
                        Button(
                            onClick = { startMLKitScanning() },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(56.dp),
                            enabled = scannerReady
                        ) {
                            Text("开始扫描")
                        }

                        // 使用说明
                        Card(
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "使用说明",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("1. 将文档放置在对比度高的背景上")
                                Text("2. 确保光线充足，避免阴影")
                                Text("3. 保持手机平稳，对准文档")
                                Text("4. 系统会自动检测边框并校正")
                            }
                        }
                    }
                }
            }
        }
    }
}
