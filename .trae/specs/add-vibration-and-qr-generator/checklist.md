# Checklist

## 依赖与权限
- [x] AndroidManifest.xml 中已添加 `android.permission.VIBRATE` 权限
- [x] libs.versions.toml 中已声明 zxingCore 版本（3.5.3）与 library 条目
- [x] app/build.gradle.kts 中已添加 zxing-core 依赖

## ViewModel
- [x] ScannerUiState 包含 `generatedQrText: String?` 与 `showGeneratedQr: Boolean` 字段（默认 null/false）
- [x] `onGenerateQr()` 文本为空时直接返回，非空时设置 `generatedQrText` 与 `showGeneratedQr = true`
- [x] `onHideQr()` 清空 `generatedQrText` 并设置 `showGeneratedQr = false`
- [x] `onScanSuccess` 在 `showGeneratedQr = true` 时直接返回，不触发扫码追加与动效

## 震动反馈
- [x] 扫码成功后通过 `Vibrator` + `VibrationEffect.createOneShot` 触发约 30ms 震动
- [x] 调用 `Vibrator.hasVibrator()` 进行保护性检查，无马达时静默跳过
- [x] 震动与现有 800ms 视觉动效并行，不互相阻塞
- [x] 震动逻辑放在 UI 层（通过 `LaunchedEffect(scanSuccess)`），ViewModel 不持有 Context

## 生成二维码按钮
- [x] 按钮使用 FloatingActionButton + Icons.Default.QrCode
- [x] 按钮位于右上角摄像头开关按钮正下方，间距 12dp
- [x] 两个按钮垂直对齐（Column + Alignment.TopEnd）
- [x] 按钮始终可见，不依赖相机是否开启

## 二维码叠加层
- [x] 点击生成按钮后，取景框区域显示二维码 Bitmap
- [x] 二维码居中，外有白色圆角背景（RoundedCornerShape 16dp）
- [x] 二维码生成使用 ZXing `QRCodeWriter().encode()`
- [x] 二维码大小为 256×256 dp（或等效 px）
- [x] 叠加层显示期间，相机扫码分析暂停
- [x] 点击叠加层任意位置或关闭按钮，叠加层消失，扫码恢复
- [x] 二维码 Bitmap 通过 `remember(qrText)` 缓存，避免重复生成

## ScannerScreen 连接
- [x] `viewModel::onGenerateQr` 已传入 CameraSection
- [x] `state.showGeneratedQr`、`state.generatedQrText`、`viewModel::onHideQr` 已传入 CameraSection

## 测试
- [x] `onGenerateQr` 文本为空时状态不变的单元测试通过
- [x] `onGenerateQr` 文本非空时正确设置状态的单元测试通过
- [x] `onHideQr` 清空状态的单元测试通过
- [x] `showGeneratedQr = true` 时 `onScanSuccess` 被忽略的单元测试通过
- [x] 既有 17 个测试仍全部通过（无回归）

## 构建验证
- [x] `gradle :app:testDebugUnitTest` 通过
- [x] `gradle :app:assembleDebug` 通过
