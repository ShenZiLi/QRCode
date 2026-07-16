# QRCode — QR Scanner & Text Editor

A lightweight Android QR code scanning tool that combines real-time camera recognition with text editing capabilities. Scan results are automatically appended to an editable text field, supporting undo/redo, copy, clear, and other operations — ideal for continuous scanning scenarios.

## ✨ Features

- **Real-time Scanning** — CameraX + ML Kit Barcode, continuous scanning without interruption
- **Text Editor** — Scan results auto-appended, supports manual editing
- **Undo/Redo** — Unified snapshot stack, shared between scan and manual edits
- **Copy to Clipboard** — One-tap copy with brief toast feedback
- **Camera Control** — One-tap toggle camera on/off, permission guidance
- **Scan Animation** — Green border flash + checkmark animation on successful scan
- **Vibration Feedback** — 30ms short vibration on scan success (VibratorManager on API 31+, fallback to Vibrator on older versions, silently skipped on devices without vibration motor)
- **QR Generation** — "Generate QR" button (below camera toggle) converts text content to QR code using ZXing, overlaid on the viewfinder area
- **Dark Theme** — Automatically switches between light/dark themes following system settings

## 🏗 Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.0.21 |
| UI Framework | Jetpack Compose + Material 3 | BOM 2024.10.00 |
| Scanning | CameraX + ML Kit Barcode Scanning | CameraX 1.4.0 / MLKit 17.3.0 |
| QR Generation | ZXing core | 3.5.3 |
| Vibration | Vibrator / VibratorManager | API 31+ branch |
| Architecture | MVVM + Single Activity | — |
| Build System | Gradle + Version Catalog | AGP 8.13.2 |
| Min SDK | 34 | targetSdk 36 |
| Testing | JUnit 4 + kotlinx-coroutines-test | — |

## 🚀 Quick Start

### Prerequisites

- Android Studio Ladybug (2024.x) or higher
- JDK 17+
- An Android 14+ (API 34) device or emulator with camera

### Clone & Build

```bash
git clone https://github.com/your-username/QRCode.git
cd QRCode
./gradlew assembleDebug
```

Or open the project root in Android Studio, wait for Gradle Sync, then click Run.

### Run Tests

```bash
./gradlew test
```

Includes **21 unit tests** covering ViewModel core logic (undo/redo stack, debounce, animation timing, scan pause, QR generation toggle, scan exclusivity, etc.).

## 📖 Usage

1. Grant camera permission on first launch
2. Point camera at QR code — text is auto-appended to the editor below
3. Use toolbar: undo/redo, copy, clear, backspace
4. Text field supports direct editing, sharing undo/redo stack with scan input
5. Toggle camera on/off with the top-right button
6. Enter text and tap the QR icon (second from top-right) to generate a QR code overlaid on the viewfinder; tap again or tap the overlay to hide
7. Camera is auto-disabled while QR overlay is shown (black placeholder); scanning resumes when overlay is hidden

## 📁 Project Structure

```
app/
├── src/main/java/com/example/qrcode/
│   ├── MainActivity.kt                 # Single Activity entry
│   ├── ui/
│   │   ├── ScannerScreen.kt            # Root layout (top-bottom split)
│   │   ├── CameraSection.kt            # Camera area (preview/permission/animation/toggle)
│   │   ├── TextEditorSection.kt        # Text editor area
│   │   └── theme/Theme.kt              # Material3 light/dark theme
│   ├── viewmodel/
│   │   └── ScannerViewModel.kt         # Core state management & business logic
│   └── util/
│       └── BarcodeAnalyzer.kt          # ML Kit barcode analyzer (ImageAnalysis.Analyzer)
├── src/test/java/.../
│   └── ScannerViewModelTest.kt         # ViewModel unit tests
└── src/main/res/                       # Resources (strings/icons/themes/xml)
```

## 🧠 Design Highlights

- **Unified Snapshot Stack**: Scan and manual edits share the same undo/redo stack with 500ms debounce
- **Scan Pause Protection**: 800ms animation pause after successful scan to prevent duplicate triggers
- **Pure Compose Animation**: Scan success animation entirely implemented with Compose animation system
- **Resourceful Camera Management**: Releases CameraX resources when camera is off to reduce power consumption
- **Generate & Preview Mutual Exclusion**: QR overlay disables camera via `when` branch routing to `CameraOffPlaceholder`, preventing CameraX binding conflicts
- **Toggle Interaction**: Single button handles both "generate" and "hide" — state driven by single source of truth `showGeneratedQr`

## ⚖️ License

```
MIT License

Copyright (c) 2026
```

---

> Built with CameraX and ML Kit, tailored for continuous scanning scenarios.
