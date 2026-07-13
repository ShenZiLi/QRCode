package com.example.qrcode.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.qrcode.viewmodel.ScannerViewModel

/**
 * 根布局：上下分栏，上半部分相机（weight=1），下半部分文本编辑器（固定高度）。
 */
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 应用启动时检查权限，若未授权则触发请求
    val requestPermission = rememberCameraPermissionLauncher { granted ->
        viewModel.onCameraPermissionResult(granted)
    }
    LaunchedEffect(Unit) {
        requestPermission()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            CameraSection(
                cameraEnabled = state.cameraEnabled,
                hasCameraPermission = state.hasCameraPermission,
                scanSuccess = state.scanSuccess,
                generatedQrText = state.generatedQrText,
                showGeneratedQr = state.showGeneratedQr,
                onToggleCamera = viewModel::onToggleCamera,
                onGenerateQr = viewModel::onGenerateQr,
                onHideQr = viewModel::onHideQr,
                onScanResult = viewModel::onScanSuccess,
                onRequestPermission = requestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            TextEditorSection(
                text = state.text,
                copied = state.copied,
                onTextChanged = viewModel::onTextChanged,
                onClear = viewModel::onClear,
                onBackspace = viewModel::onBackspace,
                onCopy = viewModel::onCopy,
                onUndo = viewModel::onUndo,
                onRedo = viewModel::onRedo,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        }
    }
}
