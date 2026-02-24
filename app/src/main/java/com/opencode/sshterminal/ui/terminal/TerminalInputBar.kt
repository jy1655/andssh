package com.opencode.sshterminal.ui.terminal

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.opencode.sshterminal.R

@Composable
fun TerminalInputBar(
    onSendBytes: (ByteArray) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onSnippetClick: (() -> Unit)? = null,
    onPageScroll: ((Int) -> Unit)? = null,
    isHapticFeedbackEnabled: Boolean = true,
    focusSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
    val controller = rememberTerminalInputController(onSendBytes)
    val hapticFeedback = LocalHapticFeedback.current
    val onKeyTap =
        remember(hapticFeedback, isHapticFeedbackEnabled) {
            {
                if (isHapticFeedbackEnabled) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }

    val shortcutActions =
        TerminalShortcutActions(
            onMenuClick = onMenuClick,
            onSnippetClick = onSnippetClick,
            onPageScroll = onPageScroll,
            onToggleCtrl = controller::toggleCtrl,
            onToggleAlt = controller::toggleAlt,
            onShortcut = controller::onShortcut,
        )

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            TerminalShortcutRow(
                state =
                    TerminalModifierState(
                        ctrlArmed = controller.ctrlArmed,
                        altArmed = controller.altArmed,
                    ),
                actions = shortcutActions,
                onSendBytes = onSendBytes,
                onKeyTap = onKeyTap,
            )
            TerminalTextInputRow(
                focusSignal = focusSignal,
                textFieldValue = controller.textFieldValue,
                onValueChange = controller::onTextFieldValueChange,
                onSubmit = controller::submitInput,
                onKeyTap = onKeyTap,
            )
        }
    }
}

private data class TerminalModifierState(
    val ctrlArmed: Boolean,
    val altArmed: Boolean,
)

private data class TerminalShortcutActions(
    val onMenuClick: (() -> Unit)?,
    val onSnippetClick: (() -> Unit)?,
    val onPageScroll: ((Int) -> Unit)?,
    val onToggleCtrl: () -> Unit,
    val onToggleAlt: () -> Unit,
    val onShortcut: (TerminalShortcut) -> Unit,
)

@Composable
private fun TerminalShortcutRow(
    state: TerminalModifierState,
    actions: TerminalShortcutActions,
    onSendBytes: (ByteArray) -> Unit,
    onKeyTap: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.onMenuClick?.let { onMenuClick ->
            KeyChip("\u2630", onTap = onKeyTap, onClick = onMenuClick)
        }
        actions.onSnippetClick?.let { onSnippetClick ->
            KeyChip(
                label = stringResource(R.string.terminal_snippets_short),
                onTap = onKeyTap,
                onClick = onSnippetClick,
            )
        }
        KeyChip("ESC", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ESC) }
        KeyChip("TAB", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.TAB) }
        ToggleKeyChip("Ctrl", state.ctrlArmed, onTap = onKeyTap, onClick = actions.onToggleCtrl)
        ToggleKeyChip("Alt", state.altArmed, onTap = onKeyTap, onClick = actions.onToggleAlt)
        KeyChip("\u2191", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_UP) }
        KeyChip("\u2193", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_DOWN) }
        KeyChip("\u2190", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_LEFT) }
        KeyChip("\u2192", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_RIGHT) }
        KeyChip("\u232B", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.BACKSPACE) }
        KeyChip("PgUp", onTap = onKeyTap) { actions.onPageScroll?.invoke(1) }
        KeyChip("PgDn", onTap = onKeyTap) { actions.onPageScroll?.invoke(-1) }
        KeyChip("^C", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.CTRL_C) }
        KeyChip("^D", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.CTRL_D) }
        KeyChip(stringResource(R.string.terminal_paste), onTap = onKeyTap) {
            val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val text = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
            if (!text.isNullOrEmpty()) {
                onSendBytes(text.toByteArray(Charsets.UTF_8))
            }
        }
    }
}

@Composable
private fun TerminalTextInputRow(
    focusSignal: Int,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onKeyTap: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .focusRequester(focusRequester)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            textStyle =
                TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions =
                KeyboardActions(
                    onSend = { onSubmit() },
                    onDone = { onSubmit() },
                ),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            "...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    innerTextField()
                }
            },
        )

        KeyChip("\u23CE", onTap = onKeyTap, onClick = onSubmit)
    }
}

@Composable
private fun KeyChip(
    label: String,
    onTap: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Surface(
        modifier =
            Modifier
                .height(34.dp)
                .widthIn(min = 36.dp)
                .clickable {
                    onTap?.invoke()
                    onClick()
                },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = label,
                style =
                    TextStyle(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        }
    }
}

@Composable
private fun ToggleKeyChip(
    label: String,
    armed: Boolean,
    onTap: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val bg = if (armed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val fg = if (armed) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier =
            Modifier
                .height(34.dp)
                .widthIn(min = 40.dp)
                .clickable {
                    onTap?.invoke()
                    onClick()
                },
        shape = RoundedCornerShape(6.dp),
        color = bg,
        tonalElevation = 1.dp,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                text = label,
                style =
                    TextStyle(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = fg,
                    ),
            )
        }
    }
}
