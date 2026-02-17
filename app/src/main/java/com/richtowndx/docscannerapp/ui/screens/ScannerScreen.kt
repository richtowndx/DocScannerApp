package com.richtowndx.docscannerapp.ui.screens

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.richtowndx.docscannerapp.R
import com.richtowndx.docscannerapp.ml.OCREngineType
import com.richtowndx.docscannerapp.viewmodel.OCRViewModel

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: OCRViewModel = hiltViewModel()
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val ocrText by viewModel.ocrText.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val selectedEngine by viewModel.selectedEngine.collectAsState()
    var showEngineSelector by remember { mutableStateOf(false) }
    var capturedImage by remember { mutableStateOf<Bitmap?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showEngineSelector = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Engine selector
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.select_ocr_engine),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (selectedEngine) {
                            OCREngineType.ML_KIT -> stringResource(R.string.ml_kit)
                            OCREngineType.TESSERACT -> stringResource(R.string.tesseract)
                        },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Camera preview or captured image
            capturedImage?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured document",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                )
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Camera preview would go here")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Capture button
            Button(
                onClick = {
                    if (cameraPermissionState.hasPermission) {
                        // Simulate capture - in real app, this would capture from camera
                        // For now, just show processing
                    } else {
                        cameraPermissionState.launchPermissionRequest()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.capture))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // OCR result
            if (isProcessing) {
                CircularProgressIndicator()
                Text(stringResource(R.string.processing))
            } else if (ocrText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.ocr_result),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = ocrText)
                    }
                }
            }
        }

        // Engine selector dialog
        if (showEngineSelector) {
            AlertDialog(
                onDismissRequest = { showEngineSelector = false },
                title = { Text(stringResource(R.string.select_ocr_engine)) },
                text = {
                    Column {
                        OCREngineType.values().forEach { engine ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedEngine == engine,
                                    onClick = {
                                        viewModel.selectEngine(engine)
                                        showEngineSelector = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when (engine) {
                                        OCREngineType.ML_KIT -> stringResource(R.string.ml_kit)
                                        OCREngineType.TESSERACT -> stringResource(R.string.tesseract)
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEngineSelector = false }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}
