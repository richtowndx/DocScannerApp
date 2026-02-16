package com.example.docscanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.example.docscanner.ui.DocScannerApp
import com.example.docscanner.ui.theme.DocScannerTheme
import com.example.docscanner.util.AppLog
import com.example.docscanner.util.LogTag

class MainActivity : ComponentActivity() {

    // 根据Android版本选择需要的权限
    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 只需要相机权限
            arrayOf(Manifest.permission.CAMERA)
        } else {
            // Android 12及以下需要相机和存储权限
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        }

    private var permissionsGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        AppLog.i(LogTag.PERMISSION, "Permission result: $permissions")
        permissions.forEach { (permission, granted) ->
            AppLog.i(LogTag.PERMISSION, "  $permission = $granted")
        }
        // 只要有相机权限就认为权限已授予
        permissionsGranted = permissions[Manifest.permission.CAMERA] == true
        AppLog.i(LogTag.PERMISSION, "Camera permission granted: $permissionsGranted")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()
        AppLog.i(LogTag.PERMISSION, "Initial permissions check: $permissionsGranted, SDK: ${Build.VERSION.SDK_INT}")

        setContent {
            DocScannerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    DocScannerApp(
                        navController = navController,
                        permissionsGranted = permissionsGranted,
                        onRequestPermissions = { requestPermissions() }
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        // 只检查相机权限
        permissionsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        AppLog.i(LogTag.PERMISSION, "Requesting permissions: ${requiredPermissions.toList()}")
        permissionLauncher.launch(requiredPermissions)
    }
}
