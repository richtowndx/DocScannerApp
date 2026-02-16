package com.example.docscanner.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.docscanner.scanner.ScannerEngine
import com.example.docscanner.ui.theme.DocScannerTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    selectedEngine: ScannerEngine,
    onEngineSelected: (ScannerEngine) -> Unit,
    onStartScan: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文档扫描器") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "智能文档扫描",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "边框识别 • 透视校正 • OCR识别",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // 引擎选择
            Text(
                text = "选择扫描引擎",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            EngineSelector(
                selectedEngine = selectedEngine,
                onEngineSelected = onEngineSelected
            )

            // 功能说明
            Text(
                text = "功能特性",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )

            FeatureGrid()

            Spacer(modifier = Modifier.weight(1f))

            // 开始扫描按钮
            Button(
                onClick = onStartScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "开始扫描",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun EngineSelector(
    selectedEngine: ScannerEngine,
    onEngineSelected: (ScannerEngine) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScannerEngine.values().forEach { engine ->
            EngineOption(
                engine = engine,
                isSelected = selectedEngine == engine,
                onClick = { onEngineSelected(engine) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EngineOption(
    engine: ScannerEngine,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val (title, description, icon) = when (engine) {
        ScannerEngine.ML_KIT -> Triple(
            "ML Kit 官方方案",
            "Google官方API，简单快速",
            Icons.Default.AutoAwesome
        )
        ScannerEngine.OPENCV_CUSTOM -> Triple(
            "OpenCV 自定义",
            "完全自定义，功能强大",
            Icons.Default.Build
        )
        ScannerEngine.SMART_CROPPER -> Triple(
            "SmartCropper",
            "开源库，智能裁剪",
            Icons.Default.Crop
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FeatureGrid() {
    val features = listOf(
        Triple("边框识别", "自动检测文档边界", Icons.Default.CropFree),
        Triple("透视校正", "将倾斜文档转为正矩形", Icons.Default.Transform),
        Triple("图像增强", "灰度化、锐化、对比度调整", Icons.Default.Tune),
        Triple("OCR识别", "识别文字并导出Markdown", Icons.Default.TextFields)
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        features.chunked(2).forEach { rowFeatures ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowFeatures.forEach { (title, desc, icon) ->
                    FeatureCard(
                        title = title,
                        description = desc,
                        icon = icon,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowFeatures.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    DocScannerTheme {
        HomeScreen(
            selectedEngine = ScannerEngine.ML_KIT,
            onEngineSelected = {},
            onStartScan = {},
            onNavigateToSettings = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    DocScannerTheme(darkTheme = true) {
        HomeScreen(
            selectedEngine = ScannerEngine.ML_KIT,
            onEngineSelected = {},
            onStartScan = {},
            onNavigateToSettings = {}
        )
    }
}
