package com.example.qrcode.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [ScannerViewModel] 撤销/恢复栈、扫码追加、debounce 行为单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: ScannerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ScannerViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initial_state_isEmpty() {
        val state = viewModel.uiState.value
        assertEquals("", state.text)
        assertFalse(state.canUndo)
        assertFalse(state.canRedo)
        assertFalse(state.scanSuccess)
    }

    @Test
    fun onScanSuccess_appendsText_andEnablesUndo() = runTest(testDispatcher) {
        viewModel.onScanSuccess("ABC")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("ABC", state.text)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
    }

    @Test
    fun onScanSuccess_consecutiveAppendsAndUndoRestoresPrevious() = runTest(testDispatcher) {
        viewModel.onScanSuccess("A")
        advanceUntilIdle()
        viewModel.onScanSuccess("B")
        advanceUntilIdle()

        assertEquals("AB", viewModel.uiState.value.text)

        viewModel.onUndo()
        assertEquals("A", viewModel.uiState.value.text)
        assertTrue(viewModel.uiState.value.canRedo)

        viewModel.onRedo()
        assertEquals("AB", viewModel.uiState.value.text)
    }

    @Test
    fun onScanSuccess_triggersAnimation_thenClearsFlag() = runTest(testDispatcher) {
        viewModel.onScanSuccess("X")
        assertTrue(viewModel.uiState.value.scanSuccess)

        // 动效 800ms 后应清除
        advanceTimeBy(801)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.scanSuccess)
    }

    @Test
    fun onScanSuccess_pausesDuringAnimation_ignoresNewScans() = runTest(testDispatcher) {
        viewModel.onScanSuccess("A")
        advanceUntilIdle() // 动效期间 scanPaused = true，但 advanceUntilIdle 会推进时间跳过动效
        // 重新触发一次动效，并在动效内尝试再次扫码
        viewModel.onScanSuccess("B")
        // 此时处于动效期，不应触发新扫码
        viewModel.onScanSuccess("C")

        // 只应追加 B，C 被忽略
        assertEquals("AB", viewModel.uiState.value.text)
    }

    @Test
    fun onTextChanged_debounceCommitsSnapshot() = runTest(testDispatcher) {
        viewModel.onTextChanged("hello")
        // 未到 500ms，不应入栈
        advanceTimeBy(499)
        assertEquals("hello", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.canUndo)

        // 到 500ms，应提交快照
        advanceTimeBy(2)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canUndo)
    }

    @Test
    fun onTextChanged_debounceResetsOnEachChange() = runTest(testDispatcher) {
        viewModel.onTextChanged("a")
        advanceTimeBy(300)
        viewModel.onTextChanged("ab")
        advanceTimeBy(300)
        // 仍未到 500ms（重置后只过了 300）
        assertFalse(viewModel.uiState.value.canUndo)

        advanceTimeBy(200)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canUndo)
    }

    @Test
    fun onClear_clearsText_andEnablesUndo() = runTest(testDispatcher) {
        viewModel.onScanSuccess("data")
        advanceUntilIdle()
        assertEquals("data", viewModel.uiState.value.text)

        viewModel.onClear()

        assertEquals("", viewModel.uiState.value.text)
        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun onClear_whenEmpty_doesNothing() = runTest(testDispatcher) {
        viewModel.onClear()
        assertEquals("", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.canUndo)
    }

    @Test
    fun onBackspace_removesLastChar_andDebounces() = runTest(testDispatcher) {
        viewModel.onScanSuccess("abc")
        advanceUntilIdle()
        assertEquals("abc", viewModel.uiState.value.text)

        viewModel.onBackspace()
        assertEquals("ab", viewModel.uiState.value.text)

        // 退格未到 500ms，不应提交新快照（undo 应返回上一次扫码快照前的状态）
        advanceTimeBy(499)
        viewModel.onUndo()
        // undo: 当前文本 ab 入 redo，undoStack.pop 设为当前
        // undoStack 在扫码时压入了 ""，因此 undo 后应回到 ""
        assertEquals("", viewModel.uiState.value.text)
        assertTrue(viewModel.uiState.value.canRedo)
    }

    @Test
    fun onUndo_whenStackEmpty_doesNothing() = runTest(testDispatcher) {
        viewModel.onUndo()
        assertEquals("", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun onRedo_whenStackEmpty_doesNothing() = runTest(testDispatcher) {
        viewModel.onRedo()
        assertEquals("", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun onUndo_redo_canUndoCanRedoFlagsCorrect() = runTest(testDispatcher) {
        viewModel.onScanSuccess("1")
        advanceUntilIdle()
        viewModel.onScanSuccess("2")
        advanceUntilIdle()

        // text="12", undoStack=["","1"], redo=[]
        viewModel.onUndo()
        // text="1", undoStack=[""], redo=["12"]
        assertEquals("1", viewModel.uiState.value.text)
        assertTrue(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.canRedo)

        viewModel.onUndo()
        // text="", undoStack=[], redo=["12","1"]
        assertEquals("", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.canRedo)

        viewModel.onRedo()
        // text="1", undoStack=[""], redo=["12"]
        assertEquals("1", viewModel.uiState.value.text)
        assertTrue(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.canRedo)
    }

    @Test
    fun newEdit_afterUndo_clearsRedoStack() = runTest(testDispatcher) {
        viewModel.onScanSuccess("A")
        advanceUntilIdle()
        viewModel.onScanSuccess("B")
        advanceUntilIdle()
        // text="AB", undoStack=["","A"]

        viewModel.onUndo()
        // text="A", redo=["AB"]
        assertTrue(viewModel.uiState.value.canRedo)

        viewModel.onScanSuccess("C")
        advanceUntilIdle()
        // 扫码清空 redo
        assertEquals("AC", viewModel.uiState.value.text)
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun onToggleCamera_flipsState() {
        val initial = viewModel.uiState.value.cameraEnabled
        viewModel.onToggleCamera()
        assertEquals(!initial, viewModel.uiState.value.cameraEnabled)
        viewModel.onToggleCamera()
        assertEquals(initial, viewModel.uiState.value.cameraEnabled)
    }

    @Test
    fun onCameraPermissionResult_updatesPermissionAndCameraState() {
        viewModel.onCameraPermissionResult(true)
        assertTrue(viewModel.uiState.value.hasCameraPermission)
        assertTrue(viewModel.uiState.value.cameraEnabled)

        viewModel.onCameraPermissionResult(false)
        assertFalse(viewModel.uiState.value.hasCameraPermission)
        assertFalse(viewModel.uiState.value.cameraEnabled)
    }

    @Test
    fun onCopy_setsCopiedFlag_thenClearsAfterDelay() = runTest(testDispatcher) {
        viewModel.onCopy()
        assertTrue(viewModel.uiState.value.copied)

        advanceTimeBy(1501)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.copied)
    }
}
