# 二维码扫描文本解析器 - 设计文档

## 概述

单页面 Android 应用，上半部分为相机取景框，下半部分为可编辑文本框。支持连续扫描二维码并将文本累加到文本框，提供撤销/恢复/复制/清空/退格等功能。

## 技术选型

| 项目 | 选择 | 版本 |
|------|------|------|
| 开发语言 | Kotlin | - |
| UI 框架 | Jetpack Compose | BOM 最新稳定版 |
| 扫码方案 | CameraX + ML Kit Barcode | 最新稳定版 |
| 最低 SDK | minSdk 34 | compileSdk/targetSdk 36 |
| 架构 | MVVM + 单 Activity | - |

## 页面结构

单 Activity + 单 Screen，无导航、无二级页面。

```
MainActivity
└── ScannerScreen (Column 上下布局)
    ├── CameraSection (上半部分, weight=1)
    │   ├── CameraPreview (CameraX PreviewView)
    │   ├── ScanSuccessOverlay (绿色对勾 + 边框闪烁动效, 临时叠加层)
    │   ├── CameraToggleButton (右上角)
    │   └── CameraOffPlaceholder (黑屏 + 摄像头划掉图标)
    └── TextEditorSection (下半部分, 固定高度)
        ├── TextEditorHeader (右上角: 清空、删除按钮)
        ├── TextInputField (多行文本框)
        └── TextEditorFooter (底部: 复制、撤销、恢复按钮)
```

## 功能设计

### 1. 扫码识别

- CameraX `ImageAnalysis` 持续分析预览帧
- ML Kit `BarcodeScanner` 识别二维码（FORMAT_QR_CODE）
- 识别成功后：
  1. 触发 800ms 成功动效
  2. 将文本追加到文本框（不换行分隔，直接拼接；扫码块作为快照入栈）
  3. 重置分析器，避免同一二维码重复触发
- 动效结束后自动恢复分析，支持连续扫描

### 2. 撤销/恢复（统一快照栈）

采用**文本快照栈**方案，扫码与手动编辑统一管理。

**数据结构：**
- `undoStack: ArrayDeque<String>` - 历史快照栈
- `redoStack: ArrayDeque<String>` - 恢复快照栈

**记录时机：**
- **扫码成功**：将「当前文本」push 到 undoStack，清空 redoStack，然后追加扫码文本，更新 `lastCommittedText`
- **手动编辑/退格/清空**：维护 `lastCommittedText`（上次入栈的稳定文本）。每次文本变化重置 500ms 定时器，定时器触发时：若当前文本 ≠ `lastCommittedText`，将 `lastCommittedText` push 到 undoStack，清空 redoStack，更新 `lastCommittedText` 为当前文本
- **undo/redo 操作本身**：不触发新快照记录，但更新 `lastCommittedText` 为操作后的当前文本，并取消 pending 的 debounce 定时器

**操作逻辑：**
- **撤销**：将「当前文本」push 到 redoStack，从 undoStack pop 栈顶 → 设为当前文本
- **恢复**：将「当前文本」push 到 undoStack，从 redoStack pop 栈顶 → 设为当前文本
- **按钮可用性**：undoStack 为空时撤销按钮禁用，redoStack 为空时恢复按钮禁用

**初始状态：** undoStack 和 redoStack 均为空，文本框为空字符串，`lastCommittedText = ""`。

### 3. 文本框操作

- **可编辑**：用户可手动输入和修改
- **清空按钮**：清空整个文本框内容（进入撤销栈）
- **删除按钮（退格）**：逐字删除末尾一个字符（与手动编辑共享 debounce 逻辑）
- **复制按钮**：将文本框内容复制到系统剪贴板，短暂提示"已复制"（非文字动效，用 Toast 或短暂状态指示）

### 4. 摄像头控制

- **关闭/开启按钮**位于取景框右上角
- 关闭时：释放 CameraX 相机资源，取景框显示黑屏 + 摄像头划掉图标
- 开启时：重新绑定 CameraX，恢复预览和分析

### 5. 扫描成功动效

Compose 动画实现，总时长 800ms：
- 取景框四边边框闪绿色（alpha 0→1→0 渐变）
- 中心出现绿色对勾（scale 0.5→1 + alpha 0→1→0）
- 动效为纯图形，无任何文字
- 动效期间扫码分析暂停，动效结束自动恢复

## 数据流

```
BarcodeScanner → ViewModel.onScanSuccess(text)
              → push 当前文本到 undoStack, 清空 redoStack
              → 追加扫码文本到 textFieldState
              → 触发 successAnimation state (800ms)

TextInputField.onValueChange → ViewModel.onTextChanged(newText, oldText)
                            → debounce 500ms → push oldText 到 undoStack, 清空 redoStack

按钮点击:
  onUndo()    → push 当前文本到 redoStack, undoStack.pop() 设为当前文本
  onRedo()    → push 当前文本到 undoStack, redoStack.pop() 设为当前文本
  onCopy()    → 复制到剪贴板
  onClear()   → 走 onTextChanged 逻辑, 清空文本
  onBackspace → 走 onTextChanged 逻辑, 删除末尾字符

CameraToggleButton → ViewModel.onToggleCamera()
                   → 切换 cameraEnabled state
                   → 绑定/解绑 CameraX
```

## 组件清单

### ViewModel
- `ScannerViewModel` - 管理 UiState，包含扫码、文本、撤销栈、相机状态

### Composable 组件
- `ScannerScreen` - 根布局
- `CameraSection` - 相机区域容器
- `CameraPreview` - CameraX 预览封装
- `ScanSuccessOverlay` - 成功动效叠加层
- `CameraToggleButton` - 相机开关按钮
- `CameraOffPlaceholder` - 相机关闭占位
- `TextEditorSection` - 文本编辑区域容器
- `TextEditorHeader` - 清空/删除按钮
- `TextInputField` - 多行文本输入
- `TextEditorFooter` - 复制/撤销/恢复按钮

### 工具类
- `BarcodeAnalyzer` - ML Kit 二维码分析器，实现 CameraX `ImageAnalysis.Analyzer`

## 权限

- `android.permission.CAMERA`（运行时申请）

## 错误处理

- 相机权限拒绝：取景框显示提示图标，点击引导授权
- ML Kit 模型加载失败：不影响相机预览，扫码不可用
- 剪贴板复制失败：忽略（系统保证成功率）

## 测试策略

- ViewModel 单元测试：撤销/恢复栈逻辑、扫码追加逻辑、debounce 行为
- Compose UI 测试：按钮可用性、文本框交互
- 手动测试：连续扫码、撤销恢复跨场景、相机关闭开启

## 非目标（YAGNI）

- 不支持条形码（仅 QR_CODE）
- 不支持从相册识别
- 不支持历史记录持久化
- 不支持多语言（仅中文）
- 不支持横屏（仅竖屏）
- 不支持主题切换
