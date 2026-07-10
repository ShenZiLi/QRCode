package com.example.qrcode.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI 状态。
 */
data class ScannerUiState(
    val text: String = "",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val cameraEnabled: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val scanSuccess: Boolean = false,
    val copied: Boolean = false
)

/**
 * 扫码文本解析器 ViewModel。
 *
 * 采用文本快照栈方案：扫码与手动编辑统一管理 undo/redo。
 */
class ScannerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    /** 历史快照栈。 */
    private val undoStack = ArrayDeque<String>()
    /** 恢复快照栈。 */
    private val redoStack = ArrayDeque<String>()

    /** 上次入栈的稳定文本，用于 debounce 判定。 */
    private var lastCommittedText: String = ""

    /** 手动编辑 debounce 定时器。 */
    private var debounceJob: Job? = null

    /** 扫码成功动效定时器。 */
    private var scanAnimationJob: Job? = null

    /** 复制提示定时器。 */
    private var copiedJob: Job? = null

    /** 当前是否处于扫码暂停期（动效期间）。 */
    private var scanPaused: Boolean = false

    // region 扫码

    /**
     * 扫码成功回调。将「当前文本」入栈，清空 redo，追加扫码文本，触发 800ms 动效。
     */
    fun onScanSuccess(scannedText: String) {
        if (scanPaused) return
        val current = _uiState.value.text
        pushUndo(current)
        redoStack.clear()
        lastCommittedText = current + scannedText
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = current + scannedText,
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
        triggerScanAnimation()
    }

    /**
     * 触发 800ms 扫码成功动效，期间暂停扫码分析。
     */
    private fun triggerScanAnimation() {
        scanAnimationJob?.cancel()
        scanPaused = true
        _uiState.update { it.copy(scanSuccess = true) }
        scanAnimationJob = viewModelScope.launch {
            delay(ANIMATION_DURATION_MS)
            scanPaused = false
            _uiState.update { it.copy(scanSuccess = false) }
        }
    }

    // endregion

    // region 文本输入

    /**
     * 手动编辑回调。维护 debounce 500ms 定时器。
     */
    fun onTextChanged(newText: String) {
        _uiState.update { it.copy(text = newText) }
        scheduleDebounce(newText)
    }

    private fun scheduleDebounce(newText: String) {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(DEBOUNCE_DURATION_MS)
            if (newText != lastCommittedText) {
                pushUndo(lastCommittedText)
                redoStack.clear()
                lastCommittedText = newText
                _uiState.update {
                    it.copy(
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = false
                    )
                }
            }
        }
    }

    private fun cancelDebounce() {
        debounceJob?.cancel()
        debounceJob = null
    }

    /**
     * 清空整个文本框（进入撤销栈）。
     */
    fun onClear() {
        val current = _uiState.value.text
        if (current.isEmpty()) return
        pushUndo(current)
        redoStack.clear()
        lastCommittedText = ""
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = "",
                canUndo = undoStack.isNotEmpty(),
                canRedo = false
            )
        }
    }

    /**
     * 退格：逐字删除末尾一个字符，走 debounce 逻辑。
     */
    fun onBackspace() {
        val current = _uiState.value.text
        if (current.isEmpty()) return
        val newText = current.dropLast(1)
        onTextChanged(newText)
    }

    /**
     * 复制到剪贴板后短暂提示。
     */
    fun onCopy() {
        _uiState.update { it.copy(copied = true) }
        copiedJob?.cancel()
        copiedJob = viewModelScope.launch {
            delay(COPY_HINT_DURATION_MS)
            _uiState.update { it.copy(copied = false) }
        }
    }

    // endregion

    // region 撤销/恢复

    /**
     * 撤销：将「当前文本」push 到 redoStack，从 undoStack pop 设为当前文本。
     */
    fun onUndo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.text
        redoStack.addLast(current)
        val previous = undoStack.removeLast()
        lastCommittedText = previous
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = previous,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    /**
     * 恢复：将「当前文本」push 到 undoStack，从 redoStack pop 设为当前文本。
     */
    fun onRedo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.text
        undoStack.addLast(current)
        val next = redoStack.removeLast()
        lastCommittedText = next
        cancelDebounce()
        _uiState.update {
            it.copy(
                text = next,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
    }

    // endregion

    // region 相机

    /**
     * 切换摄像头开关。
     */
    fun onToggleCamera() {
        _uiState.update { it.copy(cameraEnabled = !it.cameraEnabled) }
    }

    /**
     * 更新相机权限状态。
     */
    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasCameraPermission = granted,
                cameraEnabled = granted
            )
        }
    }

    // endregion

    private fun pushUndo(snapshot: String) {
        undoStack.addLast(snapshot)
    }

    companion object {
        private const val DEBOUNCE_DURATION_MS = 500L
        private const val ANIMATION_DURATION_MS = 800L
        private const val COPY_HINT_DURATION_MS = 1500L
    }
}
