# 震动反馈与二维码生成 Spec

## Why
当前应用扫码成功时仅有视觉动效（绿色边框闪烁 + 对勾），缺少触觉反馈，在嘈杂或注意力不集中场景下感知弱。同时，应用只能"扫"二维码，无法"生成"二维码，用户需切换其他应用才能将文本转为二维码展示给他人扫描。

## What Changes
- 扫码成功后触发短暂震动（Vibrator + VibrationEffect）
- 新增"生成二维码"按钮，位于右上角摄像头开关按钮**下方**
- 点击按钮后，将文本框内容生成二维码，**显示在相机取景框中**（叠加在预览之上）
- 二维码显示期间，暂停相机扫码分析，避免扫到自己的二维码造成循环
- 点击生成的二维码或关闭按钮可隐藏，恢复正常扫码

## Impact
- Affected specs: docs/superpowers/specs/2026-07-10-qr-scanner-text-parser-design.md（扫码→文本解析流程，需叠加二维码展示与震动）
- Affected code:
  - [ScannerViewModel.kt](file:///Users/shen/Studio/Code/QRCode/app/src/main/java/com/example/qrcode/viewmodel/ScannerViewModel.kt)（新增 UI 状态与生成/隐藏方法）
  - [CameraSection.kt](file:///Users/shen/Studio/Code/QRCode/app/src/main/java/com/example/qrcode/ui/CameraSection.kt)（新增按钮、二维码叠加层、震动触发、扫码暂停）
  - [ScannerScreen.kt](file:///Users/shen/Studio/Code/QRCode/app/src/main/java/com/example/qrcode/ui/ScannerScreen.kt)（连接新状态）
  - [strings.xml](file:///Users/shen/Studio/Code/QRCode/app/src/main/res/values/strings.xml)（新增文案）
  - [AndroidManifest.xml](file:///Users/shen/Studio/Code/QRCode/app/src/main/AndroidManifest.xml)（新增 VIBRATE 权限）
  - [libs.versions.toml](file:///Users/shen/Studio/Code/QRCode/gradle/libs.versions.toml) + [app/build.gradle.kts](file:///Users/shen/Studio/Code/QRCode/app/build.gradle.kts)（新增 ZXing 依赖）

## ADDED Requirements

### Requirement: 扫码成功震动反馈
系统 SHALL 在每次成功识别二维码后立即触发一次短暂震动（约 30ms 单次脉冲），与现有 800ms 视觉动效同时进行但不阻塞扫码分析暂停逻辑。

#### Scenario: 扫码成功触发震动
- **WHEN** 用户扫描到一个 QR_CODE
- **THEN** 设备震动约 30ms
- **AND** 视觉动效（绿色边框 + 对勾）保持原有 800ms 时序
- **AND** 扫码分析暂停 800ms 后恢复

#### Scenario: 设备无震动马达
- **WHEN** 设备不支持震动（`Vibrator.hasVibrator()` 返回 false）
- **THEN** 静默跳过震动，不报错，不影响其他功能

### Requirement: 生成二维码按钮
系统 SHALL 在相机区域右上角、摄像头开关按钮正下方提供一个"生成二维码"浮动按钮，按钮始终可见（不依赖相机是否开启）。

#### Scenario: 按钮位置
- **WHEN** 用户进入扫码界面
- **THEN** 右上角依次显示两个 FAB：上方为摄像头开关，下方为生成二维码
- **AND** 两个按钮垂直对齐，间距 12dp

#### Scenario: 文本为空时点击
- **WHEN** 文本框内容为空
- **AND** 用户点击"生成二维码"按钮
- **THEN** 不生成二维码，不弹出任何提示（保持极简）

### Requirement: 二维码展示叠加层
系统 SHALL 在点击"生成二维码"按钮后，将文本框内容生成的二维码 Bitmap 显示在相机取景框区域，居中、带白色圆角背景。

#### Scenario: 生成并显示二维码
- **WHEN** 文本框有内容
- **AND** 用户点击"生成二维码"按钮
- **THEN** 取景框区域居中显示生成的二维码 Bitmap（256×256 dp 区域）
- **AND** 二维码外有白色圆角背景，提供良好的视觉对比
- **AND** 相机扫码分析暂停（避免扫到自己的二维码）

#### Scenario: 隐藏二维码
- **WHEN** 二维码叠加层正在显示
- **AND** 用户点击叠加层任意位置（或点击关闭按钮）
- **THEN** 二维码叠加层消失
- **AND** 相机扫码分析恢复（若相机处于开启状态）

## MODIFIED Requirements

### Requirement: 相机扫码暂停逻辑
原有：扫码成功后暂停 800ms 扫码分析以播放动效。
修改为：扫码成功后暂停 800ms 扫码分析以播放动效；**二维码叠加层显示期间也暂停扫码分析**，隐藏叠加层后恢复。两种暂停源需独立管理，避免相互覆盖导致提前恢复。

#### Scenario: 二维码显示期间屏蔽扫码
- **WHEN** 二维码叠加层正在显示
- **THEN** 即使相机预览仍在运行，扫码分析不触发
- **AND** 隐藏二维码后，扫码立即恢复（若无动效期未结束）
