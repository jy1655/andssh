package com.opencode.sshterminal.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle

@Composable
internal fun rememberTerminalCharSize(textMeasurer: TextMeasurer): Size =
    remember(textMeasurer) {
        val result =
            textMeasurer.measure(
                "W",
                style = TextStyle(fontFamily = TERMINAL_FONT_FAMILY, fontSize = TERMINAL_FONT_SIZE),
            )
        Size(result.size.width.toFloat(), result.size.height.toFloat())
    }
