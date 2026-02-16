package com.example.docscanner.ui.screens

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import kotlinx.coroutines.launch

private const val TAG = "ScannerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    engine: ScannerEngine,
    onScanComplete: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var scannerReady by remember { mutableStateOf(false) }

    val scannerAdapter = remember { ScannerFactory.getAdapter(engine) }

    // ML Kit 扫描器配置
    val options = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(10)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }

    val scanner = remember { GmsDocumentScanning.getClient(options) }

    // ML Kit 扫描结果处理
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanningResult?.pages?.let { pages ->
                if (pages.isNotEmpty()) {
                    onScanComplete(pages.first().imageUri.toString())
                }
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            onBack()
        }
    }

    LaunchedEffect(engine) {
        try {
            scannerReady = scannerAdapter.isAvailable()
            isLoading = false
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    // 启动扫描的函数
    fun startScanning() {
        try {
            // 使用反射来调用API，因为方法名可能因版本不同而不同
            val scannerClass = scanner.javaClass
            val method = scannerClass.getMethod("getStartIntentSender", android.content.Context::class.java)
            val result = method.invoke(scanner, context)

            if (result is com.google.android.gms.tasks.Task<*>) {
                result.addOnSuccessListener { intentSender ->
                    if (intentSender is android.content.IntentSender) {
                        val request = IntentSenderRequest.Builder(intentSender).build()
                        scannerLauncher.launch(request)
                    }
                }.addOnFailureListener { e ->
                    Log.e(TAG, "Failed to start scanner", e)
                    error = "启动扫描器失败: ${e.message}"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scanner", e)
            error = "启动扫描器失败: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("扫描文档") },
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
                                    text = "当前引擎: ${engine.name}",
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

                        // 开始扫描按钮
                        Button(
                            onClick = { startScanning() },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(56.dp),
                            enabled = scannerReady && engine == ScannerEngine.ML_KIT
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
                                Text("4. 系统会自动检测边框")
                            }
                        }
                    }
                }
            }
        }
    }
}
