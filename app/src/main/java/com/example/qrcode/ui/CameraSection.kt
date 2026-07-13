package com.example.qrcode.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.qrcode.R
import com.example.qrcode.util.BarcodeAnalyzer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

private val SuccessGreen = Color(0xFF4CAF50)

/**
 * 相机区域容器。
 */
@Composable
fun CameraSection(
    cameraEnabled: Boolean,
    hasCameraPermission: Boolean,
    scanSuccess: Boolean,
    generatedQrText: String?,
    showGeneratedQr: Boolean,
    onToggleCamera: () -> Unit,
    onGenerateQr: () -> Unit,
    onHideQr: () -> Unit,
    onScanResult: (String) -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        val context = LocalContext.current
        LaunchedEffect(scanSuccess) {
            if (scanSuccess) {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java)
                }
                if (vibrator?.hasVibrator() == true) {
                    vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        }
        when {
            !hasCameraPermission -> CameraPermissionPlaceholder(onClick = onRequestPermission)
            showGeneratedQr -> CameraOffPlaceholder()
            cameraEnabled -> CameraPreview(
                onScanResult = onScanResult,
                scanEnabled = true,
                modifier = Modifier.fillMaxSize()
            )
            else -> CameraOffPlaceholder()
        }

        ScanSuccessOverlay(visible = scanSuccess)

        if (showGeneratedQr && generatedQrText != null) {
            GeneratedQrOverlay(
                qrText = generatedQrText,
                onHide = onHideQr,
                modifier = Modifier.fillMaxSize()
            )
        }

        if (hasCameraPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                CameraToggleButton(
                    cameraOn = cameraEnabled,
                    onClick = onToggleCamera
                )
                GenerateQrButton(onClick = onGenerateQr)
            }
        }
    }
}

/**
 * CameraX 预览封装。绑定 Preview + ImageAnalysis。
 */
@Composable
fun CameraPreview(
    onScanResult: (String) -> Unit,
    scanEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val analyzer = remember(scanEnabled, onScanResult) {
        if (scanEnabled) BarcodeAnalyzer(onScanResult) else null
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val selector = CameraSelector.DEFAULT_BACK_CAMERA
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    if (analyzer != null) {
                        it.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            } catch (_: Exception) {
                // 绑定失败忽略，保留黑屏预览
            }
        }, ContextCompat.getMainExecutor(context))
    }
}

/**
 * 相机关闭占位：黑屏 + 摄像头划掉图标。
 */
@Composable
fun CameraOffPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.NoPhotography,
            contentDescription = stringResource(R.string.camera_off),
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp)
        )
    }
}

/**
 * 相机权限缺失占位：点击引导授权。
 */
@Composable
private fun CameraPermissionPlaceholder(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.camera_permission_denied),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

/**
 * 扫描成功动效叠加层，总时长 800ms：边框闪绿 + 中心对勾。
 */
@Composable
fun ScanSuccessOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(0)),
        exit = fadeOut(tween(200)),
        modifier = modifier
    ) {
        val transition = rememberInfiniteTransition(label = "scan-success")
        val borderAlpha by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "border-alpha"
        )
        val checkScale by animateFloatAsState(
            targetValue = if (visible) 1f else 0.5f,
            animationSpec = tween(300),
            label = "check-scale"
        )
        val checkAlpha by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "check-alpha"
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = 6.dp,
                        color = SuccessGreen.copy(alpha = borderAlpha),
                        shape = RoundedCornerShape(0.dp)
                    )
            )
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(SuccessGreen.copy(alpha = checkAlpha * 0.2f), RoundedCornerShape(48.dp))
                    .graphicsLayer {
                        scaleX = checkScale
                        scaleY = checkScale
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}

/**
 * 相机开关按钮（右上角）。
 */
@Composable
fun CameraToggleButton(
    cameraOn: Boolean,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = if (cameraOn) MaterialTheme.colorScheme.primary else Color.DarkGray,
        contentColor = Color.White
    ) {
        Icon(
            imageVector = if (cameraOn) Icons.Default.NoPhotography else Icons.Default.CameraAlt,
            contentDescription = if (cameraOn) stringResource(R.string.turn_off_camera)
            else stringResource(R.string.turn_on_camera)
        )
    }
}

/**
 * 生成二维码按钮（位于相机开关按钮下方）。
 */
@Composable
fun GenerateQrButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Icon(
            imageVector = Icons.Default.QrCode,
            contentDescription = stringResource(R.string.generate_qr)
        )
    }
}

/**
 * 权限请求封装：在 Compose 中触发运行时权限申请。
 */
@Composable
fun rememberCameraPermissionLauncher(
    onResult: (Boolean) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onResult(granted)
    }
    return remember(launcher) {
        {
            when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
                PackageManager.PERMISSION_GRANTED -> onResult(true)
                else -> launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

/**
 * 二维码生成叠加层：黑色半透明蒙层 + 中心白色圆角背景 + 二维码 Bitmap + 关闭按钮。
 * 点击任意位置（除关闭按钮外）触发 onHide。
 */
@Composable
fun GeneratedQrOverlay(
    qrText: String,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(qrText) {
        val size = 512
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(qrText, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        bmp
    }
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onHide
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.generated_qr),
                modifier = Modifier.size(256.dp)
            )
        }
        FloatingActionButton(
            onClick = onHide,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            containerColor = Color.White,
            contentColor = Color.Black
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.close_qr)
            )
        }
    }
}
