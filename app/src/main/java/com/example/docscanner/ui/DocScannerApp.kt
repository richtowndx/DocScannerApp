package com.example.docscanner.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.docscanner.data.config.ConfigRepository
import com.example.docscanner.ocr.OCREngine
import com.example.docscanner.scanner.ScannerEngine
import com.example.docscanner.ui.screens.*
import com.example.docscanner.ui.viewmodel.ScanProjectViewModel
import com.example.docscanner.ui.viewmodel.ScanProjectViewModelFactory
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Scanner : Screen("scanner")
    object Preview : Screen("preview")
    object OCRResult : Screen("ocr_result")
    object Settings : Screen("settings")
    object ScanProjectList : Screen("scan_project_list")
    object ScanProjectDetail : Screen("scan_project_detail/{projectName}") {
        fun createRoute(projectName: String) = "scan_project_detail/$projectName"
    }
}

@Composable
fun DocScannerApp(
    navController: NavHostController,
    permissionsGranted: Boolean,
    onRequestPermissions: () -> Unit,
    application: Application
) {
    val scope = rememberCoroutineScope()
    val configRepository = remember { ConfigRepository.getInstance(application) }

    // 全局状态 - 扫描引擎固定为 ML Kit
    val selectedEngine = ScannerEngine.ML_KIT
    var selectedOCREngine by remember { mutableStateOf(OCREngine.ML_KIT) }
    var scannedImageUri by remember { mutableStateOf<String?>(null) }
    var ocrResultText by remember { mutableStateOf<String?>(null) }

    // 扫描件 ViewModel
    val scanProjectViewModel: ScanProjectViewModel = viewModel(
        factory = ScanProjectViewModelFactory(application)
    )

    // 加载保存的 OCR 引擎配置
    LaunchedEffect(Unit) {
        val savedEngine = configRepository.getOCREngine()
        selectedOCREngine = savedEngine
        AppLog.i(LogTag.UI_HOME, "Loaded saved OCR engine: $savedEngine")
    }

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
                },
                onNavigateToScanProject = {
                    navController.navigate(Screen.ScanProjectList.route)
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
                onOCREngineSelected = { newEngine ->
                    selectedOCREngine = newEngine
                    // 保存选择到配置
                    scope.launch {
                        configRepository.setOCREngine(newEngine)
                        AppLog.i(LogTag.UI_HOME, "Saved OCR engine selection: $newEngine")
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ScanProjectList.route) {
            val projects by scanProjectViewModel.projects.collectAsState()

            ScanProjectListScreen(
                projects = projects,
                onCreateProject = { name ->
                    scanProjectViewModel.createProject(name)
                    navController.navigate(Screen.ScanProjectDetail.createRoute(name))
                },
                onSelectProject = { name ->
                    navController.navigate(Screen.ScanProjectDetail.createRoute(name))
                },
                onDeleteProject = { name ->
                    scanProjectViewModel.deleteProject(name)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.ScanProjectDetail.route) { backStackEntry ->
            val projectName = backStackEntry.arguments?.getString("projectName") ?: ""
            val project by scanProjectViewModel.getProject(projectName).collectAsState(initial = null)
            val images by scanProjectViewModel.getProjectImages(projectName).collectAsState(initial = emptyList())
            val exportProgress by scanProjectViewModel.exportProgress.collectAsState()
            val exportResult by scanProjectViewModel.exportResult.collectAsState()
            val message by scanProjectViewModel.message.collectAsState()

            ScanProjectDetailScreen(
                projectName = projectName,
                project = project,
                images = images,
                scanner = scanProjectViewModel.scanner,
                exportProgress = exportProgress,
                exportResult = exportResult,
                message = message,
                onAddImages = { uris ->
                    scanProjectViewModel.addImagesToProject(projectName, uris)
                },
                onExportPdf = { _ ->
                    // 使用保存的导出目录
                    scanProjectViewModel.exportPdfWithSavedDir(projectName)
                },
                onExportTextWithAutoOcr = { _ ->
                    // 使用保存的导出目录，自动执行OCR
                    scanProjectViewModel.exportTextWithAutoOcrWithSavedDir(projectName)
                },
                onDeleteImage = { image ->
                    scanProjectViewModel.deleteImage(image)
                },
                onClearExportProgress = {
                    scanProjectViewModel.clearExportProgress()
                    scanProjectViewModel.clearExportResult()
                },
                onClearMessage = {
                    scanProjectViewModel.clearMessage()
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
