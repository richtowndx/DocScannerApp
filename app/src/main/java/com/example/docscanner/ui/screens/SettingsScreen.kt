package com.example.docscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.docscanner.ocr.BaiduOCRAdapter
import com.example.docscanner.ocr.GLMOCRAdapter
import com.example.docscanner.ocr.OCREngine
import com.example.docscanner.ocr.SiliconFlowOCRAdapter
import com.example.docscanner.ocr.SiliconFlowOCRModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedOCREngine: OCREngine,
    onOCREngineSelected: (OCREngine) -> Unit,
    onBack: () -> Unit
) {
    // 百度OCR配置对话框
    var showBaiduConfigDialog by remember { mutableStateOf(false) }
    var baiduApiKey by remember { mutableStateOf(BaiduOCRAdapter.apiKey ?: "") }
    var baiduSecretKey by remember { mutableStateOf(BaiduOCRAdapter.secretKey ?: "") }

    // GLM-OCR配置对话框
    var showGLMConfigDialog by remember { mutableStateOf(false) }
    var glmApiKey by remember { mutableStateOf(GLMOCRAdapter.apiKey ?: "") }

    // SiliconFlow OCR配置对话框
    var showSiliconFlowConfigDialog by remember { mutableStateOf(false) }
    var siliconFlowApiKey by remember { mutableStateOf(SiliconFlowOCRAdapter.apiKey ?: "") }
    var siliconFlowModel by remember { mutableStateOf(SiliconFlowOCRAdapter.selectedModel) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // OCR引擎设置
            SettingsSection(title = "OCR识别引擎") {
                OCREngine.values().forEach { engine ->
                    OCREngineSettingItem(
                        engine = engine,
                        isSelected = selectedOCREngine == engine,
                        onClick = {
                            onOCREngineSelected(engine)
                            if (engine == OCREngine.BAIDU) {
                                showBaiduConfigDialog = true
                            } else if (engine == OCREngine.GLM_OCR) {
                                showGLMConfigDialog = true
                            } else if (engine == OCREngine.SILICONFLOW) {
                                showSiliconFlowConfigDialog = true
                            }
                        },
                        onConfigure = {
                            if (engine == OCREngine.BAIDU) {
                                showBaiduConfigDialog = true
                            } else if (engine == OCREngine.GLM_OCR) {
                                showGLMConfigDialog = true
                            } else if (engine == OCREngine.SILICONFLOW) {
                                showSiliconFlowConfigDialog = true
                            }
                        }
                    )
                }
            }

            HorizontalDivider()

            // 关于信息
            SettingsSection(title = "关于") {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "文档扫描器 v1.0",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "一个功能强大的文档扫描应用，支持边框识别、透视校正和OCR识别。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "技术栈: Kotlin + Jetpack Compose + ML Kit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // OCR引擎说明
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OCR引擎说明",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• ML Kit: Google官方，离线识别，支持中英文\n" +
                                    "• Tesseract: 开源引擎，内置中英文语言包\n" +
                                    "• 百度OCR: 高精度云端识别，需配置API Key\n" +
                                    "• GLM-OCR: 智谱AI云端高精度OCR，支持版面分析\n" +
                                    "• SiliconFlow: 云端多模型OCR，支持PaddleOCR-VL",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }
    }

    // 百度OCR配置对话框
    if (showBaiduConfigDialog) {
        BaiduConfigDialog(
            apiKey = baiduApiKey,
            secretKey = baiduSecretKey,
            onApiKeyChange = { baiduApiKey = it },
            onSecretKeyChange = { baiduSecretKey = it },
            onDismiss = { showBaiduConfigDialog = false },
            onConfirm = {
                val adapter = BaiduOCRAdapter()
                adapter.configure(baiduApiKey, baiduSecretKey)
                showBaiduConfigDialog = false
            }
        )
    }

    // GLM-OCR配置对话框
    if (showGLMConfigDialog) {
        GLMConfigDialog(
            apiKey = glmApiKey,
            onApiKeyChange = { glmApiKey = it },
            onDismiss = { showGLMConfigDialog = false },
            onConfirm = {
                val adapter = GLMOCRAdapter()
                adapter.configure(glmApiKey)
                showGLMConfigDialog = false
            }
        )
    }

    // SiliconFlow OCR配置对话框
    if (showSiliconFlowConfigDialog) {
        SiliconFlowConfigDialog(
            apiKey = siliconFlowApiKey,
            selectedModel = siliconFlowModel,
            onApiKeyChange = { siliconFlowApiKey = it },
            onModelChange = { siliconFlowModel = it },
            onDismiss = { showSiliconFlowConfigDialog = false },
            onConfirm = {
                val adapter = SiliconFlowOCRAdapter()
                adapter.configure(siliconFlowApiKey, siliconFlowModel)
                showSiliconFlowConfigDialog = false
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OCREngineSettingItem(
    engine: OCREngine,
    isSelected: Boolean,
    onClick: () -> Unit,
    onConfigure: () -> Unit
) {
    val (title, description, needsConfig) = when (engine) {
        OCREngine.ML_KIT -> Triple(
            "ML Kit",
            "Google官方OCR，离线识别，支持中英文",
            false
        )
        OCREngine.TESSERACT -> Triple(
            "Tesseract",
            "开源OCR引擎，内置中英文语言包",
            false
        )
        OCREngine.BAIDU -> Triple(
            "百度OCR",
            "高精度云端识别，需配置API Key",
            true
        )
        OCREngine.GLM_OCR -> Triple(
            "智谱GLM-OCR",
            "云端高精度OCR，支持复杂版面分析，需配置API Key",
            true
        )
        OCREngine.SILICONFLOW -> Triple(
            "SiliconFlow OCR",
            "云端多模型OCR，支持PaddleOCR-VL和DeepSeek-OCR，需配置API Key",
            true
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (needsConfig && isSelected) {
                TextButton(onClick = onConfigure) {
                    Text("配置")
                }
            }
        }
    }
}

@Composable
private fun BaiduConfigDialog(
    apiKey: String,
    secretKey: String,
    onApiKeyChange: (String) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置百度OCR") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "请在百度智能云控制台创建应用获取API Key和Secret Key",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = onSecretKeyChange,
                    label = { Text("Secret Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = apiKey.isNotBlank() && secretKey.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun GLMConfigDialog(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置智谱GLM-OCR") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "请在智谱AI开放平台获取API Key\n平台地址: https://open.bigmodel.cn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = apiKey.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SiliconFlowConfigDialog(
    apiKey: String,
    selectedModel: SiliconFlowOCRModel,
    onApiKeyChange: (String) -> Unit,
    onModelChange: (SiliconFlowOCRModel) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("配置 SiliconFlow OCR") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "请在 SiliconFlow 平台获取 API Key\n平台地址: https://cloud.siliconflow.cn",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 模型选择
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedModel.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("OCR 模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        SiliconFlowOCRModel.values().forEach { model ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(model.displayName)
                                        Text(
                                            text = model.modelId,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onModelChange(model)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = apiKey.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
