# Tasks

- [x] Task 1: 添加 VIBRATE 权限与 ZXing 依赖
  - [x] SubTask 1.1: 在 AndroidManifest.xml 中添加 `<uses-permission android:name="android.permission.VIBRATE" />`
  - [x] SubTask 1.2: 在 gradle/libs.versions.toml 添加 zxingCore 版本号与 library 声明（com.google.zxing:core:3.5.3）
  - [x] SubTask 1.3: 在 app/build.gradle.kts 的 dependencies 中添加 zxing-core 引用

- [x] Task 2: 扩展 ScannerViewModel 状态与方法
  - [x] SubTask 2.1: 在 ScannerUiState 中新增 `generatedQrText: String?`（待生成二维码的文本）与 `showGeneratedQr: Boolean` 字段
  - [x] SubTask 2.2: 新增 `onGenerateQr()` 方法：文本为空时直接返回，否则将当前文本写入 `generatedQrText` 并设置 `showGeneratedQr = true`
  - [x] SubTask 2.3: 新增 `onHideQr()` 方法：清空 `generatedQrText` 并设置 `showGeneratedQr = false`
  - [x] SubTask 2.4: 调整扫码暂停判定——在 `onScanSuccess` 开头增加 `showGeneratedQr` 检查，若二维码叠加层显示则忽略扫码回调

- [x] Task 3: 新增字符串资源
  - [x] SubTask 3.1: 在 strings.xml 添加 `generate_qr`（生成二维码）、`close_qr`（关闭二维码）、`generated_qr`（已生成二维码）文案

- [x] Task 4: 在 CameraSection 中实现震动触发
  - [x] SubTask 4.1: 在 CameraSection（或 ScannerScreen）中通过 `LocalContext.current` 获取 Vibrator，使用 `LaunchedEffect(scanSuccess)` 监听扫描成功并触发 `VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)`
  - [x] SubTask 4.2: 调用 `Vibrator.hasVibrator()` 进行保护性检查；无马达时静默跳过

- [x] Task 5: 在 CameraSection 中实现生成二维码按钮
  - [x] SubTask 5.1: 新增 `GenerateQrButton` Composable，使用 FloatingActionButton + Icons.Default.QrCode，位于 `CameraToggleButton` 下方（Column 包裹两个 FAB，间距 12dp）
  - [x] SubTask 5.2: 调整 `CameraSection` 的 Column 容器，使其 `Alignment.TopEnd` 且 `padding(12.dp)` 同时容纳两个 FAB
  - [x] SubTask 5.3: 添加 `onGenerateQr: () -> Unit` 参数到 `CameraSection` 并向下传递

- [x] Task 6: 实现 GeneratedQrOverlay 二维码叠加层
  - [x] SubTask 6.1: 新增 `GeneratedQrOverlay` Composable，接收 `qrText: String`、`onHide: () -> Unit`
  - [x] SubTask 6.2: 使用 `remember(qrText) { QRCodeWriter().encode(...) }` 生成 BitMatrix，再转为 Bitmap（256×256 dp 等效 px，使用 Density 转换或固定 512 px）
  - [x] SubTask 6.3: 叠加层布局：黑色半透明蒙层 + 中心白色圆角背景（RoundedCornerShape 16dp）+ 二维码 Bitmap + 右上角关闭按钮
  - [x] SubTask 6.4: 点击叠加层任意位置触发 `onHide`

- [x] Task 7: 在 CameraSection 中集成叠加层与扫码暂停
  - [x] SubTask 7.1: 在 CameraSection 中根据 `showGeneratedQr` 显示 `GeneratedQrOverlay`，覆盖整个取景框
  - [x] SubTask 7.2: 修改 `BarcodeAnalyzer` 或 `CameraPreview` 接收 `scanEnabled: Boolean` 参数，叠加层显示时停止回调（最简做法：在 CameraSection 中根据 `showGeneratedQr` 判断是否传 `null` 回调给 CameraPreview）

- [x] Task 8: 在 ScannerScreen 中连接新状态
  - [x] SubTask 8.1: 在 ScannerScreen 中将 `viewModel::onGenerateQr`、`state.showGeneratedQr`、`state.generatedQrText`、`viewModel::onHideQr` 传入 CameraSection
  - [x] SubTask 8.2: 验证整个 UI 流程：扫码→震动+动效；点击生成→显示二维码→扫码暂停；点击二维码→隐藏→扫码恢复

- [x] Task 9: 编写单元测试
  - [x] SubTask 9.1: 在 ScannerViewModelTest 中新增 `onGenerateQr` 文本为空时状态不变、文本非空时设置 `generatedQrText` 与 `showGeneratedQr = true` 的用例
  - [x] SubTask 9.2: 新增 `onHideQr` 清空状态的用例
  - [x] SubTask 9.3: 新增 `showGeneratedQr = true` 时 `onScanSuccess` 被忽略的用例

- [x] Task 10: 构建验证
  - [x] SubTask 10.1: 运行 `gradle :app:testDebugUnitTest`，确保所有测试通过
  - [x] SubTask 10.2: 运行 `gradle :app:assembleDebug`，确保构建成功

# Task Dependencies
- Task 2 依赖 Task 1（构建依赖基础）
- Task 4、5、6、7 可在 Task 2、3 完成后并行实现
- Task 8 依赖 Task 4、5、6、7
- Task 9 依赖 Task 2
- Task 10 依赖 Task 8、9
