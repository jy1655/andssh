package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun TerminalDirectInput(
    controller: TerminalInputController,
    focusSignal: Int,
    onHardwareKeyEvent: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusSignal) {
        if (focusSignal > 0) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    BasicTextField(
        value = controller.textFieldValue,
        onValueChange = controller::onTextFieldValueChange,
        modifier =
            modifier
                .size(1.dp)
                .alpha(0f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent(onHardwareKeyEvent),
        keyboardOptions =
            KeyboardOptions(
                imeAction = ImeAction.Send,
                keyboardType = KeyboardType.Text,
                autoCorrect = false,
            ),
        keyboardActions =
            KeyboardActions(
                onSend = { controller.submitInput() },
                onDone = { controller.submitInput() },
            ),
        singleLine = true,
    )
}
