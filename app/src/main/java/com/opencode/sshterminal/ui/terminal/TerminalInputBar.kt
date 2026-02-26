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
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.opencode.sshterminal.data.TerminalHardwareKeyAction
import com.opencode.sshterminal.data.TerminalShortcutLayoutItem
import com.opencode.sshterminal.data.parseTerminalHardwareKeyBindings
import com.opencode.sshterminal.data.parseTerminalShortcutLayout

@Composable
@Suppress("LongParameterList", "LongMethod")
internal fun TerminalInputBar(
    controller: TerminalInputController,
    inputMode: TerminalInputMode,
    onToggleInputMode: () -> Unit,
    onSendBytes: (ByteArray) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onSnippetClick: (() -> Unit)? = null,
    onHistoryClick: (() -> Unit)? = null,
    onPageScroll: ((Int) -> Unit)? = null,
    isHapticFeedbackEnabled: Boolean = true,
    shortcutLayout: String,
    hardwareKeyBindings: String,
    showShortcutRow: Boolean = true,
    focusSignal: Int = 0,
    modifier: Modifier = Modifier,
) {
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
            onHistoryClick = onHistoryClick,
            onPageScroll = onPageScroll,
            onToggleCtrl = controller::toggleCtrl,
            onToggleAlt = controller::toggleAlt,
            onShortcut = controller::onShortcut,
        )
    val parsedHardwareKeyBindings =
        remember(hardwareKeyBindings) {
            parseTerminalHardwareKeyBindings(hardwareKeyBindings)
        }
    val onHardwareKeyEvent =
        remember(parsedHardwareKeyBindings, onPageScroll, controller) {
            { keyEvent: KeyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) {
                    false
                } else {
                    val keyToken = mapAndroidKeyCodeToHardwareKeyToken(keyEvent.nativeKeyEvent.keyCode)
                    val action =
                        keyToken?.let { token ->
                            resolveTerminalHardwareKeyAction(
                                bindings = parsedHardwareKeyBindings,
                                key = token,
                                ctrl = keyEvent.isCtrlPressed,
                                alt = keyEvent.isAltPressed,
                                shift = keyEvent.isShiftPressed,
                                meta = keyEvent.isMetaPressed,
                            )
                        }
                    if (action == null) {
                        false
                    } else {
                        dispatchHardwareKeyAction(
                            action = action,
                            controller = controller,
                            onPageScroll = onPageScroll,
                        )
                    }
                }
            }
        }

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
            if (showShortcutRow) {
                TerminalShortcutRow(
                    state =
                        TerminalModifierState(
                            ctrlArmed = controller.ctrlArmed,
                            altArmed = controller.altArmed,
                        ),
                    actions = shortcutActions,
                    onSendBytes = onSendBytes,
                    onKeyTap = onKeyTap,
                    shortcutLayout = shortcutLayout,
                )
            }
            when (inputMode) {
                TerminalInputMode.TEXT_BAR -> {
                    TerminalTextInputRow(
                        focusSignal = focusSignal,
                        textFieldValue = controller.textFieldValue,
                        onValueChange = controller::onTextFieldValueChange,
                        onSubmit = controller::submitInput,
                        onHardwareKeyEvent = onHardwareKeyEvent,
                        onKeyTap = onKeyTap,
                    )
                    TerminalInputModeToggleRow(
                        label = stringResource(R.string.terminal_input_mode_direct),
                        onToggle = onToggleInputMode,
                        onKeyTap = onKeyTap,
                    )
                }

                TerminalInputMode.DIRECT -> {
                    TerminalInputModeToggleRow(
                        label = stringResource(R.string.terminal_input_mode_text_bar),
                        onToggle = onToggleInputMode,
                        onKeyTap = onKeyTap,
                    )
                }
            }
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
    val onHistoryClick: (() -> Unit)?,
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
    shortcutLayout: String,
) {
    val context = LocalContext.current
    val shortcutLayoutItems = remember(shortcutLayout) { parseTerminalShortcutLayout(shortcutLayout) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shortcutLayoutItems.forEach { item ->
            ShortcutLayoutChip(
                item = item,
                state = state,
                actions = actions,
                onSendBytes = onSendBytes,
                onKeyTap = onKeyTap,
                context = context,
            )
        }
    }
}

@Composable
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
private fun ShortcutLayoutChip(
    item: TerminalShortcutLayoutItem,
    state: TerminalModifierState,
    actions: TerminalShortcutActions,
    onSendBytes: (ByteArray) -> Unit,
    onKeyTap: () -> Unit,
    context: Context,
) {
    when (item) {
        TerminalShortcutLayoutItem.MENU -> {
            actions.onMenuClick?.let { onMenuClick ->
                KeyChip("\u2630", onTap = onKeyTap, onClick = onMenuClick)
            }
        }

        TerminalShortcutLayoutItem.SNIPPETS -> {
            actions.onSnippetClick?.let { onSnippetClick ->
                KeyChip(
                    label = stringResource(R.string.terminal_snippets_short),
                    onTap = onKeyTap,
                    onClick = onSnippetClick,
                )
            }
        }

        TerminalShortcutLayoutItem.HISTORY -> {
            actions.onHistoryClick?.let { onHistoryClick ->
                KeyChip(
                    label = stringResource(R.string.terminal_history_short),
                    onTap = onKeyTap,
                    onClick = onHistoryClick,
                )
            }
        }

        TerminalShortcutLayoutItem.ESC -> {
            KeyChip("ESC", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ESC) }
        }

        TerminalShortcutLayoutItem.TAB -> {
            KeyChip("TAB", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.TAB) }
        }

        TerminalShortcutLayoutItem.CTRL -> {
            ToggleKeyChip("Ctrl", state.ctrlArmed, onTap = onKeyTap, onClick = actions.onToggleCtrl)
        }

        TerminalShortcutLayoutItem.ALT -> {
            ToggleKeyChip("Alt", state.altArmed, onTap = onKeyTap, onClick = actions.onToggleAlt)
        }

        TerminalShortcutLayoutItem.ARROW_UP -> {
            KeyChip("\u2191", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_UP) }
        }

        TerminalShortcutLayoutItem.ARROW_DOWN -> {
            KeyChip("\u2193", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_DOWN) }
        }

        TerminalShortcutLayoutItem.ARROW_LEFT -> {
            KeyChip("\u2190", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_LEFT) }
        }

        TerminalShortcutLayoutItem.ARROW_RIGHT -> {
            KeyChip("\u2192", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.ARROW_RIGHT) }
        }

        TerminalShortcutLayoutItem.HOME -> {
            KeyChip("Home", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.HOME) }
        }

        TerminalShortcutLayoutItem.END -> {
            KeyChip("End", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.END) }
        }

        TerminalShortcutLayoutItem.INSERT -> {
            KeyChip("Ins", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.INSERT) }
        }

        TerminalShortcutLayoutItem.DELETE -> {
            KeyChip("Del", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.DELETE) }
        }

        TerminalShortcutLayoutItem.BACKSPACE -> {
            KeyChip("\u232B", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.BACKSPACE) }
        }

        TerminalShortcutLayoutItem.PAGE_UP -> {
            KeyChip("PgUp", onTap = onKeyTap) { actions.onPageScroll?.invoke(1) }
        }

        TerminalShortcutLayoutItem.PAGE_DOWN -> {
            KeyChip("PgDn", onTap = onKeyTap) { actions.onPageScroll?.invoke(-1) }
        }

        TerminalShortcutLayoutItem.F1 -> {
            KeyChip("F1", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F1) }
        }

        TerminalShortcutLayoutItem.F2 -> {
            KeyChip("F2", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F2) }
        }

        TerminalShortcutLayoutItem.F3 -> {
            KeyChip("F3", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F3) }
        }

        TerminalShortcutLayoutItem.F4 -> {
            KeyChip("F4", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F4) }
        }

        TerminalShortcutLayoutItem.F5 -> {
            KeyChip("F5", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F5) }
        }

        TerminalShortcutLayoutItem.F6 -> {
            KeyChip("F6", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F6) }
        }

        TerminalShortcutLayoutItem.F7 -> {
            KeyChip("F7", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F7) }
        }

        TerminalShortcutLayoutItem.F8 -> {
            KeyChip("F8", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F8) }
        }

        TerminalShortcutLayoutItem.F9 -> {
            KeyChip("F9", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F9) }
        }

        TerminalShortcutLayoutItem.F10 -> {
            KeyChip("F10", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F10) }
        }

        TerminalShortcutLayoutItem.F11 -> {
            KeyChip("F11", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F11) }
        }

        TerminalShortcutLayoutItem.F12 -> {
            KeyChip("F12", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.F12) }
        }

        TerminalShortcutLayoutItem.CTRL_C -> {
            KeyChip("^C", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.CTRL_C) }
        }

        TerminalShortcutLayoutItem.CTRL_D -> {
            KeyChip("^D", onTap = onKeyTap) { actions.onShortcut(TerminalShortcut.CTRL_D) }
        }

        TerminalShortcutLayoutItem.PASTE -> {
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
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun dispatchHardwareKeyAction(
    action: TerminalHardwareKeyAction,
    controller: TerminalInputController,
    onPageScroll: ((Int) -> Unit)?,
): Boolean {
    return when (action) {
        TerminalHardwareKeyAction.ESC -> {
            controller.onShortcut(TerminalShortcut.ESC)
            true
        }
        TerminalHardwareKeyAction.TAB -> {
            controller.onShortcut(TerminalShortcut.TAB)
            true
        }
        TerminalHardwareKeyAction.ENTER -> {
            controller.submitInput()
            true
        }
        TerminalHardwareKeyAction.ARROW_UP -> {
            controller.onShortcut(TerminalShortcut.ARROW_UP)
            true
        }
        TerminalHardwareKeyAction.ARROW_DOWN -> {
            controller.onShortcut(TerminalShortcut.ARROW_DOWN)
            true
        }
        TerminalHardwareKeyAction.ARROW_LEFT -> {
            controller.onShortcut(TerminalShortcut.ARROW_LEFT)
            true
        }
        TerminalHardwareKeyAction.ARROW_RIGHT -> {
            controller.onShortcut(TerminalShortcut.ARROW_RIGHT)
            true
        }
        TerminalHardwareKeyAction.BACKSPACE -> {
            controller.onShortcut(TerminalShortcut.BACKSPACE)
            true
        }
        TerminalHardwareKeyAction.CTRL_C -> {
            controller.onShortcut(TerminalShortcut.CTRL_C)
            true
        }
        TerminalHardwareKeyAction.CTRL_D -> {
            controller.onShortcut(TerminalShortcut.CTRL_D)
            true
        }
        TerminalHardwareKeyAction.PAGE_UP -> {
            if (onPageScroll == null) {
                false
            } else {
                onPageScroll(1)
                true
            }
        }
        TerminalHardwareKeyAction.PAGE_DOWN -> {
            if (onPageScroll == null) {
                false
            } else {
                onPageScroll(-1)
                true
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun TerminalTextInputRow(
    focusSignal: Int,
    textFieldValue: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSubmit: () -> Unit,
    onHardwareKeyEvent: (KeyEvent) -> Boolean,
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
                    .onPreviewKeyEvent(onHardwareKeyEvent)
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
private fun TerminalInputModeToggleRow(
    label: String,
    onToggle: () -> Unit,
    onKeyTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        KeyChip(label = label, onTap = onKeyTap, onClick = onToggle)
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
