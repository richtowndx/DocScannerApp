package com.example.docscanner.ui

import androidx.compose.runtime.*
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.docscanner.scanner.ScannerEngine
import com.example.docscanner.ui.screens.*

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Preview : Screen("preview/{imageUri}")
    object OCRResult : Screen("ocr_result")
    object Settings : Screen("settings")

    fun createRoute(vararg args: String): String {
        var route = route
        args.forEach { arg ->
            route = route.replace(Regex("\\{[^}]+\\}"), arg)
        }
        return route
    }
}

@Composable
fun DocScannerApp(
    navController: NavHostController,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit
) {
    // 全局状态
    var selectedEngine by remember { mutableStateOf(ScannerEngine.ML_KIT) }
    var scannedImageUri by remember { mutableStateOf<String?>(null) }
    var ocrResultText by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                selectedEngine = selectedEngine,
                onEngineSelected = { selectedEngine = it },
                onStartScan = {
                    if (permissionsGranted) {
                        navController.navigate(Screen.Scanner.route)
                    } else {
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
                    navController.navigate(Screen.Preview.createRoute(imageUri))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Preview.route) { backStackEntry ->
            val imageUri = backStackEntry.arguments?.getString("imageUri") ?: ""

            PreviewScreen(
                imageUri = imageUri,
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
                selectedEngine = selectedEngine,
                onEngineSelected = { selectedEngine = it },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
