package com.example.qrcode.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.qrcode.R

/**
 * 文本编辑区域容器。
 */
@Composable
fun TextEditorSection(
    text: String,
    copied: Boolean,
    onTextChanged: (String) -> Unit,
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    onCopy: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        TextEditorHeader(
            onClear = onClear,
            onBackspace = onBackspace,
            canBackspace = text.isNotEmpty()
        )
        TextInputField(
            text = text,
            onTextChanged = onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
        TextEditorFooter(
            text = text,
            copied = copied,
            onCopy = onCopy,
            onUndo = onUndo,
            onRedo = onRedo,
            canUndo = canUndo,
            canRedo = canRedo
        )
    }
}

/**
 * 文本框头部：清空、退格按钮。
 */
@Composable
private fun TextEditorHeader(
    onClear: () -> Unit,
    onBackspace: () -> Unit,
    canBackspace: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackspace, enabled = canBackspace) {
            Icon(
                imageVector = Icons.Default.Backspace,
                contentDescription = stringResource(R.string.backspace)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = onClear, enabled = canBackspace) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = stringResource(R.string.clear_text),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 多行文本输入框。
 */
@Composable
private fun TextInputField(
    text: String,
    onTextChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChanged,
        modifier = modifier,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp
        ),
        placeholder = { Text(stringResource(R.string.text_editor_hint)) },
        minLines = 4,
        maxLines = Int.MAX_VALUE,
        keyboardOptions = KeyboardOptions.Default
    )
}

/**
 * 文本框底部：复制、撤销、恢复按钮。
 */
@Composable
private fun TextEditorFooter(
    text: String,
    copied: Boolean,
    onCopy: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                copyToClipboard(context, text)
                onCopy()
            }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy)
                )
            }
            AnimatedVisibility(visible = copied, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = stringResource(R.string.copied),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.undo)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = stringResource(R.string.redo)
                )
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("QRCode", text))
}
