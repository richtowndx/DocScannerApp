package com.example.docscanner.ui

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.docscanner.ocr.OCREngine
import com.example.docscanner.scanner.ScannerEngine
import com.example.docscanner.ui.screens.*
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Preview : Screen("preview")
    object OCRResult : Screen("ocr_result")
    object Settings : Screen("settings")
}

@Composable
fun DocScannerApp(
    navController: NavHostController,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    // 全局状态 - 扫描引擎固定为 ML Kit
    val selectedEngine = ScannerEngine.ML_KIT
    var selectedOCREngine by remember { mutableStateOf(OCREngine.ML_KIT) }
    var scannedImageUri by remember { mutableStateOf<String?>(null) }
    var ocrResultText by remember { mutableStateOf<String?>(null) }

    // 用于跟踪是否应该导航到扫描页（权限请求后）
    var pendingNavigationToScanner by remember { mutableStateOf(false) }

    // 监听权限状态变化，权限授予后自动导航
    LaunchedEffect(permissionsGranted) {
        AppLog.i(LogTag.NAVIGATION, "permissionsGranted changed to: $permissionsGranted, pendingNavigation: $pendingNavigationToScanner")
        if (permissionsGranted && pendingNavigationToScanner) {
            AppLog.i(LogTag.NAVIGATION, "Permissions granted, navigating to Scanner")
            pendingNavigationToScanner = false
            navController.navigate(Screen.Scanner.route)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartScan = {
                    AppLog.i(LogTag.UI_HOME, "onStartScan: permissionsGranted=$permissionsGranted")
                    if (permissionsGranted) {
                        navController.navigate(Screen.Scanner.route)
                    } else {
                        AppLog.i(LogTag.UI_HOME, "Requesting permissions...")
                        pendingNavigationToScanner = true
                        onRequestPermissions()
                    }
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Scanner.route) {
            ScannerScreen(
                engine = selectedEngine,
                onScanComplete = { imageUri ->
                    scannedImageUri = imageUri
                    navController.navigate(Screen.Preview.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Preview.route) {
            // 使用全局状态获取 URI，避免导航参数中的特殊字符问题
            PreviewScreen(
                imageUri = scannedImageUri ?: "",
                engine = selectedEngine,
                onProcessImage = { processedUri ->
                    scannedImageUri = processedUri
                },
                onStartOCR = {
                    navController.navigate(Screen.OCRResult.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.OCRResult.route) {
            OCRResultScreen(
                imageUri = scannedImageUri,
                ocrEngine = selectedOCREngine,
                resultText = ocrResultText,
                onResultReady = { result ->
                    ocrResultText = result
                },
                onBack = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onNewScan = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                selectedOCREngine = selectedOCREngine,
                onOCREngineSelected = { selectedOCREngine = it },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
