# QRCode — 系统详细设计文档

> 版本：1.0 | 最后更新：2026-07-13

---

## 1. 项目概述

QRCode 是一个单页面 Android 应用，核心功能为**连续扫描二维码**并将识别文本**实时追加**到可编辑文本框中，附带完整的撤销/恢复、复制、清空等文本编辑能力。

### 1.1 设计目标

- 连续扫码零中断，无需手动切换
- 扫码结果与手动编辑统一管理，不给用户两套不一致的撤销逻辑
- 扫码成功给予明确的视觉反馈
- 极简交互，单页面完成所有操作

### 1.2 非目标（YAGNI）

- 不支持条形码（仅 QR_CODE）
- 不支持从相册识别
- 不支持历史记录持久化
- 不支持多语言（仅简体中文）
- 不支持横屏（仅竖屏）
- 不支持主题切换（跟随系统）

---

## 2. 系统架构

### 2.1 架构总览

```mermaid
graph TB
    subgraph "Presentation Layer"
        MA[MainActivity<br/>单 Activity 入口]
        SS[ScannerScreen<br/>根 Composable]
        CS[CameraSection<br/>相机区域]
        TS[TextEditorSection<br/>文本编辑区域]
        SO[ScanSuccessOverlay<br/>成功动效叠加层]
    end

    subgraph "ViewModel Layer"
        VM[ScannerViewModel<br/>状态管理 & 业务逻辑]
        US[ScannerUiState<br/>不可变 UI 状态]
        US_S[undoStack / redoStack<br/>快照栈]
    end

    subgraph "Data/Service Layer"
        BA[BarcodeAnalyzer<br/>ML Kit 扫码分析器]
        CAM[CameraX<br/>相机预览 + ImageAnalysis]
    end

    MA --> SS
    SS --> CS
    SS --> TS
    CS --> SO
    SS --> VM
    VM --> US
    VM --> US_S
    CS --> BA
    BA --> CAM
```

### 2.2 分层职责

| 层级 | 组件 | 职责 |
|------|------|------|
| **Presentation** | Composable 组件 | 渲染 UI、分发用户事件、管理动画 |
| **ViewModel** | ScannerViewModel | 维护 UI 状态、管理撤销栈、处理扫码/编辑/复制业务逻辑 |
| **Data/Service** | BarcodeAnalyzer + CameraX | 相机预览、图像帧分析、二维码识别 |

### 2.3 数据流方向

```mermaid
flowchart LR
    CAM[CameraX] -->|图像帧| BA[BarcodeAnalyzer]
    BA -->|扫码结果 String| VM[ScannerViewModel]
    VM -->|UI 状态 StateFlow| UI[Compose UI]
    UI -->|用户事件| VM
```

---

## 3. 组件设计

### 3.1 组件树

```mermaid
graph TD
    MainActivity --> ScannerScreen
    ScannerScreen --> CameraSection
    ScannerScreen --> TextEditorSection

    CameraSection --> CameraPreview
    CameraSection --> CameraPermissionPlaceholder
    CameraSection --> CameraOffPlaceholder
    CameraSection --> ScanSuccessOverlay
    CameraSection --> CameraToggleButton

    TextEditorSection --> TextEditorHeader
    TextEditorSection --> TextInputField
    TextEditorSection --> TextEditorFooter
```

### 3.2 组件详述

#### 3.2.1 MainActivity

- **路径**：`MainActivity.kt`
- **类型**：`ComponentActivity`（单 Activity 模式）
- **职责**：启用 edge-to-edge 显示，注入 `QRCodeTheme`，挂载 `ScannerScreen`
- **配置**：AndroidManifest 中锁定竖屏（`screenOrientation="portrait"`），键盘模式 `adjustResize`

#### 3.2.2 ScannerScreen

- **路径**：`ui/ScannerScreen.kt`
- **类型**：`@Composable`
- **布局**：`Column` 上下分栏
- **职责**：
  - 实例化 `ScannerViewModel`
  - 收集 UI State 流
  - 触发相机权限请求（`LaunchedEffect` + `rememberCameraPermissionLauncher`）
  - 编排 CameraSection（上半部分，`weight(1f)`）和 TextEditorSection（下半部分，固定 `240.dp`）

#### 3.2.3 CameraSection

- **路径**：`ui/CameraSection.kt`
- **职责**：相机区域容器，处理三种状态：

```mermaid
stateDiagram-v2
    [*] --> NO_PERMISSION: 启动
    NO_PERMISSION --> ENABLED: 用户授权
    NO_PERMISSION --> NO_PERMISSION: 拒绝（点击重新授权）
    ENABLED --> DISABLED: 用户关闭
    DISABLED --> ENABLED: 用户开启
    ENABLED --> ENABLED: 扫码成功 → 动效叠加
```

- **子组件**：
  - `CameraPreview`：CameraX PreviewView 封装，绑定 `ProcessCameraProvider`
  - `CameraPermissionPlaceholder`：权限缺失时显示，点击触发授权
  - `CameraOffPlaceholder`：相机关闭时显示黑屏 + 图标
  - `ScanSuccessOverlay`：扫码成功动效（Compose 动画实现）
  - `CameraToggleButton`：开关按钮（FAB，右上角）

#### 3.2.4 CameraPreview

- **职责**：封装 CameraX 生命周期绑定
- **实现**：`AndroidView` 承载 `PreviewView`，`LaunchedEffect` 中异步绑定 `ProcessCameraProvider`
- **ImageAnalysis**：`STRATEGY_KEEP_ONLY_LATEST`，绑定 `BarcodeAnalyzer`

#### 3.2.5 BarcodeAnalyzer

- **路径**：`util/BarcodeAnalyzer.kt`
- **类型**：实现 `ImageAnalysis.Analyzer`
- **关键设计**：

```kotlin
class BarcodeAnalyzer(
    private val onResult: (String) -> Unit
) : ImageAnalysis.Analyzer {
    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    @Volatile private var busy = false
}
```

- **扫码流程**：

```mermaid
sequenceDiagram
    participant CameraX
    participant BarcodeAnalyzer
    participant MLKit
    participant ViewModel

    CameraX->>BarcodeAnalyzer: analyze(imageProxy)
    alt busy == true
        BarcodeAnalyzer->>CameraX: imageProxy.close()
    else busy == false
        BarcodeAnalyzer->>BarcodeAnalyzer: busy = true
        BarcodeAnalyzer->>MLKit: scanner.process(inputImage)
        MLKit-->>BarcodeAnalyzer: onSuccess(barcodes)
        BarcodeAnalyzer->>ViewModel: onResult(rawValue)
        BarcodeAnalyzer->>BarcodeAnalyzer: busy = false
        BarcodeAnalyzer->>CameraX: imageProxy.close()
    end
```

- **防重复机制**：`@Volatile busy` 标志位，处理期间跳过后续帧
- **格式限制**：仅识别 `Barcode.FORMAT_QR_CODE`

#### 3.2.6 TextEditorSection

- **路径**：`ui/TextEditorSection.kt`
- **职责**：文本编辑区域容器
- **子组件**：
  - `TextEditorHeader`：右上角退格（`Backspace`）和清空（`Delete`）按钮
  - `TextInputField`：`OutlinedTextField`，等宽字体，最小 4 行
  - `TextEditorFooter`：底部复制、撤销（`Undo`）、恢复（`Redo`）按钮，复制成功后短暂显示"已复制"提示

#### 3.2.7 ScannerViewModel

- **路径**：`viewmodel/ScannerViewModel.kt`
- **类型**：继承 `ViewModel`
- **UI 状态**：

```kotlin
data class ScannerUiState(
    val text: String = "",           // 文本框当前内容
    val canUndo: Boolean = false,     // 是否可撤销
    val canRedo: Boolean = false,     // 是否可恢复
    val cameraEnabled: Boolean = true,// 相机是否开启
    val hasCameraPermission: Boolean = false, // 是否拥有相机权限
    val scanSuccess: Boolean = false, // 是否在扫码成功动效中
    val copied: Boolean = false       // 是否刚完成复制
)
```

- **内部状态**（非 UI）：

```mermaid
classDiagram
    class ScannerViewModel {
        -undoStack: ArrayDeque~String~
        -redoStack: ArrayDeque~String~
        -lastCommittedText: String
        -debounceJob: Job?
        -scanAnimationJob: Job?
        -copiedJob: Job?
        -scanPaused: Boolean

        +onScanSuccess(scannedText: String)
        +onTextChanged(newText: String)
        +onClear()
        +onBackspace()
        +onCopy()
        +onUndo()
        +onRedo()
        +onToggleCamera()
        +onCameraPermissionResult(granted: Boolean)
    }
```

---

## 4. 核心算法设计

### 4.1 统一快照栈 — Undo/Redo 方案

#### 4.1.1 数据结构

```
undoStack: ArrayDeque<String>  — 历史快照，栈顶 = 最近一次记录
redoStack: ArrayDeque<String>  — 恢复快照，栈顶 = 最近一次撤销
lastCommittedText: String      — 上次入栈的稳定文本（用于 debounce 判定）
```

#### 4.1.2 记录时机

```mermaid
flowchart TD
    A[用户/扫码触发变化] --> B{触发来源?}

    B -->|扫码成功| C[当前文本 → undoStack]
    C --> D[清空 redoStack]
    D --> E[追加扫码文本]
    E --> F[更新 lastCommittedText]
    F --> G[触发 800ms 动效]

    B -->|手动编辑| H[启动 500ms debounce]
    H --> I{500ms 内文本变化?}
    I -->|是| J[重置 debounce]
    J --> H
    I -->|否| K[lastCommittedText → undoStack]
    K --> L[清空 redoStack]
    L --> M[更新 lastCommittedText]

    B -->|清空/退格| H
    B -->|撤销| N[不记录快照]
    B -->|恢复| N
```

#### 4.1.3 撤销逻辑

```mermaid
flowchart TD
    A[用户点击撤销] --> B{undoStack 为空?}
    B -->|是| C[无操作]
    B -->|否| D[当前文本 → redoStack]
    D --> E[undoStack.pop → 设为当前文本]
    E --> F[更新 lastCommittedText]
    F --> G[取消 debounce 定时器]
    G --> H[更新 canUndo / canRedo 标志]
```

#### 4.1.4 恢复逻辑

```mermaid
flowchart TD
    A[用户点击恢复] --> B{redoStack 为空?}
    B -->|是| C[无操作]
    B -->|否| D[当前文本 → undoStack]
    D --> E[redoStack.pop → 设为当前文本]
    E --> F[更新 lastCommittedText]
    F --> G[取消 debounce 定时器]
    G --> H[更新 canUndo / canRedo 标志]
```

#### 4.1.5 状态示例

| 操作序列 | undoStack | redoStack | 当前文本 | canUndo | canRedo |
|----------|-----------|-----------|----------|---------|---------|
| 初始 | [] | [] | "" | ❌ | ❌ |
| 扫码"A" | [""] | [] | "A" | ✅ | ❌ |
| 扫码"B" | ["", "A"] | [] | "AB" | ✅ | ❌ |
| 撤销 | [""] | ["AB"] | "A" | ✅ | ✅ |
| 撤销 | [] | ["AB", "A"] | "" | ❌ | ✅ |
| 恢复 | [""] | ["AB"] | "A" | ✅ | ✅ |
| 编辑 → 扫码"C" | ["", "A"] | [] | "AC" | ✅ | ❌ |

### 4.2 扫码暂停保护

```mermaid
stateDiagram-v2
    [*] --> IDLE: 空闲
    IDLE --> SCANNING: 相机开启
    SCANNING --> SCANNING: 未识别到二维码
    SCANNING --> ANIMATING: 识别成功
    ANIMATING --> SCANNING: 800ms 动效结束
    ANIMATING --> ANIMATING: 期间扫码被忽略
    SCANNING --> PAUSED: 用户关闭相机
    PAUSED --> SCANNING: 用户开启相机
```

### 4.3 Debounce 防抖

```mermaid
sequenceDiagram
    participant User
    participant TextField
    participant VM as ScannerViewModel
    participant Timer

    User->>TextField: 连续输入
    TextField->>VM: onTextChanged("a")
    VM->>Timer: 启动 500ms
    TextField->>VM: onTextChanged("ab")  ← 300ms 后
    Timer->>Timer: ❌ 重置
    VM->>Timer: 新 500ms
    TextField->>VM: onTextChanged("abc")  ← 200ms 后
    Timer->>Timer: ❌ 重置
    VM->>Timer: 新 500ms
    Timer->>VM: ✅ 500ms 到
    VM->>VM: lastCommittedText → undoStack
    VM->>VM: 更新 lastCommittedText = "abc"
    Note over VM: undoStack 变更，canUndo = true
```

---

## 5. 动画设计

### 5.1 扫描成功动效

| 属性 | 参数 |
|------|------|
| 总时长 | 800ms |
| 进入 | 立即显现（fadeIn 0ms） |
| 退出 | 200ms fadeOut |
| 元素 | ① 四边绿色边框（6dp 宽）② 中心绿色对勾（96dp 圆形背景） |

边框动画：
- `infiniteRepeatable(tween(400), RepeatMode.Reverse)` — alpha 0↔1 循环

对勾动画：
- scale: `tween(300)` 0.5 → 1.0
- alpha: `infiniteRepeatable(tween(400), RepeatMode.Reverse)` 0↔1 循环

### 5.2 复制提示

- 复制成功后 `copied = true`
- 1.5 秒后自动清除
- UI 展示：AnimatedVisibility fadeIn/fadeOut

---

## 6. 权限设计

### 6.1 权限清单

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

- `required="false"`：允许无摄像头设备安装，扫码功能不可用但不崩溃

### 6.2 权限请求流程

```mermaid
flowchart TD
    A[ScannerScreen 启动] --> B[rememberCameraPermissionLauncher]
    B --> C{checkSelfPermission}
    C -->|已授权| D[更新 hasCameraPermission = true]
    C -->|未授权| E[launch RequestPermission]
    E --> F{用户选择}
    F -->|允许| D
    F -->|拒绝| G[更新 hasCameraPermission = false]
    G --> H[显示权限缺失占位]
    H --> I[用户点击占位]
    I --> E
```

---

## 7. 依赖清单

### 7.1 Version Catalog（gradle/libs.versions.toml）

```toml
[versions]
agp = "8.13.2"
kotlin = "2.0.21"
coreKtx = "1.13.1"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.10.00"
camerax = "1.4.0"
mlkitBarcode = "17.3.0"
junit = "4.13.2"
coroutinesTest = "1.9.0"
```

### 7.2 核心依赖分组

| 分组 | 依赖 |
|------|------|
| AndroidX Core | core-ktx |
| Lifecycle | lifecycle-runtime-ktx, lifecycle-viewmodel-compose, lifecycle-runtime-compose |
| Compose | compose-bom(BOM), ui, ui-graphics, ui-tooling, ui-tooling-preview, material3, material-icons-extended |
| Activity | activity-compose |
| CameraX | camera-core, camera-camera2, camera-lifecycle, camera-view |
| ML Kit | mlkit-barcode-scanning |
| 测试 | junit, kotlinx-coroutines-test, androidx-junit, androidx-espresso-core |

---

## 8. 测试策略

### 8.1 测试覆盖矩阵

| 测试类别 | 用例数 | 覆盖范围 |
|----------|--------|----------|
| ViewModel 单元测试 | 19 | 初始状态、扫码追加、连续扫码、动效定时、debounce、清空、退格、undo/redo 栈状态、相机开关、权限更新、剪贴板提示 |

### 8.2 测试关键场景

```mermaid
flowchart LR
    subgraph "测试覆盖"
        T1[初始状态验证]
        T2[扫码追加 & undo]
        T3[连续扫码 & 撤销链]
        T4[动效定时清除]
        T5[动效期间扫码暂停]
        T6[手动编辑 debounce]
        T7[清空 & 退格]
        T8[undo/redo 边界]
        T9[相机开关 & 权限]
        T10[复制提示定时]
    end
```

### 8.3 测试工具

- **测试框架**：JUnit 4
- **协程测试**：`kotlinx-coroutines-test`（`StandardTestDispatcher` + `advanceTimeBy` / `advanceUntilIdle`）
- **测试替身**：无 mock，直接用 ViewModel 实例测试状态变化

---

## 9. 构建配置

### 9.1 gradle.properties

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

### 9.2 构建变体

| 变体 | minifyEnabled | ProGuard |
|------|---------------|----------|
| debug | false | 无 |
| release | false（当前） | proguard-android-optimize.txt + proguard-rules.pro |

---

## 10. 完整时序流

### 10.1 扫码全流程

```mermaid
sequenceDiagram
    participant User
    participant UI as Compose UI
    participant VM as ScannerViewModel
    participant BA as BarcodeAnalyzer
    participant MLKit

    User->>UI: 打开 App
    UI->>VM: onCameraPermissionResult(granted=true)
    VM-->>UI: hasCameraPermission = true

    UI->>BA: CameraPreview 绑定 CameraX
    BA->>MLKit: 持续分析帧

    User->>UI: 扫描二维码
    MLKit->>BA: 识别到二维码内容
    BA->>VM: onScanSuccess("https://...")

    VM->>VM: push 当前文本到 undoStack
    VM->>VM: 清空 redoStack
    VM->>VM: 追加扫码文本
    VM-->>UI: text = "https://..."
    VM-->>UI: canUndo = true
    VM-->>UI: scanSuccess = true

    UI->>UI: 显示动效（800ms 闪绿 + 对勾）

    Note over UI,VM: 动效期间 scanPaused = true

    UI-->>User: 动效结束
    VM-->>UI: scanSuccess = false
```

### 10.2 撤销操作时序

```mermaid
sequenceDiagram
    participant User
    participant UI as Compose UI
    participant VM as ScannerViewModel

    User->>UI: 点击撤销按钮
    UI->>VM: onUndo()

    VM->>VM: undoStack 非空?
    VM->>VM: 当前文本 → redoStack
    VM->>VM: undoStack.pop → 设为当前文本
    VM->>VM: 更新 lastCommittedText
    VM->>VM: 取消 debounce
    VM-->>UI: text = 上一状态
    VM-->>UI: canUndo / canRedo 更新

    UI-->>User: 文本框内容恢复
```

---

## 11. 错误处理与边界情况

| 场景 | 处理方式 |
|------|----------|
| 相机权限拒绝 | 显示权限引导占位，点击重新授权 |
| ML Kit 模型加载失败 | `addOnCompleteListener` 确保 `busy = false` 和 `imageProxy.close()`，不影响相机预览 |
| 剪贴板复制失败 | 系统保证成功率，不做额外处理 |
| 空内容点击清空/退格 | 判断空值直接 return |
| 空栈点击撤销/恢复 | 判断栈空直接 return |
| CameraX 绑定失败 | try-catch 包围，保留黑屏预览 |
| 连续快速扫码 | 800ms 动效 + `scanPaused` 标志双重保护 |

---

## 12. 性能考虑

- **ImageAnalysis 背压策略**：`STRATEGY_KEEP_ONLY_LATEST`，分析器忙时丢弃中间帧
- **@Volatile busy 标志**：轻量级帧跳过，无锁开销
- **debounce 机制**：避免每次键盘输入都写入栈，合并连续编辑
- **CameraX 生命周期绑定**：关闭相机时 `cameraProvider.unbindAll()` 释放资源
- **无持久化**：数据不落盘，扫码即用即走

---

## 13. 附录

### 13.1 文件清单

```
QRCode/
├── README.md
├── build.gradle.kts                         # 根构建脚本
├── settings.gradle.kts                      # 项目设置
├── gradle.properties                        # Gradle 配置
├── gradle/
│   └── libs.versions.toml                   # 版本目录
├── local.properties                         # 本地 SDK 路径
├── .gitignore
├── docs/
│   ├── system-design.md                     # 本文件
│   └── superpowers/specs/
│       └── 2026-07-10-qr-scanner-text-parser-design.md  # 原始设计文档
└── app/
    ├── build.gradle.kts                     # 应用构建脚本
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/example/qrcode/
        │   │   ├── MainActivity.kt
        │   │   ├── ui/
        │   │   │   ├── ScannerScreen.kt
        │   │   │   ├── CameraSection.kt
        │   │   │   ├── TextEditorSection.kt
        │   │   │   └── theme/Theme.kt
        │   │   ├── viewmodel/
        │   │   │   └── ScannerViewModel.kt
        │   │   └── util/
        │   │       └── BarcodeAnalyzer.kt
        │   └── res/
        │       ├── drawable/
        │       ├── mipmap-anydpi-v26/
        │       ├── values/
        │       │   ├── colors.xml
        │       │   ├── strings.xml
        │       │   └── themes.xml
        │       └── xml/
        └── test/java/com/example/qrcode/viewmodel/
            └── ScannerViewModelTest.kt
```

### 13.2 版本历史

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-10 | 1.0 | 初始设计文档 |
| 2026-07-13 | 1.0 | 生成本系统详细设计文档 |

---

> **设计原则**：简单、稳健、可测试。小而美的工具应用，没有过度设计。
