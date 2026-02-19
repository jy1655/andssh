package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.opencode.sshterminal.R
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import com.opencode.sshterminal.ui.theme.TerminalBackground
import com.opencode.sshterminal.ui.theme.TerminalCursor
import com.opencode.sshterminal.ui.theme.TerminalForeground
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.WcWidth
import com.termux.terminal.TextStyle as TermuxTextStyle

private val TERMINAL_FONT_SIZE = 12.sp
private val TERMINAL_FONT_FAMILY = FontFamily(Font(R.font.meslo_lgs_nf_regular))

private val ANSI_COLORS = arrayOf(
    Color(0xFF000000), // 0 black
    Color(0xFFCD0000), // 1 red
    Color(0xFF00CD00), // 2 green
    Color(0xFFCDCD00), // 3 yellow
    Color(0xFF0000EE), // 4 blue
    Color(0xFFCD00CD), // 5 magenta
    Color(0xFF00CDCD), // 6 cyan
    Color(0xFFE5E5E5), // 7 white
    Color(0xFF7F7F7F), // 8 bright black
    Color(0xFFFF0000), // 9 bright red
    Color(0xFF00FF00), // 10 bright green
    Color(0xFFFFFF00), // 11 bright yellow
    Color(0xFF5C5CFF), // 12 bright blue
    Color(0xFFFF00FF), // 13 bright magenta
    Color(0xFF00FFFF), // 14 bright cyan
    Color(0xFFFFFFFF)  // 15 bright white
)

private val DEFAULT_FG = TerminalForeground
private val DEFAULT_BG = TerminalBackground
private val CURSOR_COLOR = TerminalCursor

@Composable
fun TerminalRenderer(
    bridge: TermuxTerminalBridge,
    modifier: Modifier = Modifier,
    onTap: (() -> Unit)? = null,
    onResize: ((cols: Int, rows: Int) -> Unit)? = null
) {
    val renderVersion by bridge.renderVersion.collectAsState()
    val textMeasurer = rememberTextMeasurer()

    val charSize = remember(textMeasurer) {
        val result = textMeasurer.measure(
            "W",
            style = TextStyle(fontFamily = TERMINAL_FONT_FAMILY, fontSize = TERMINAL_FONT_SIZE)
        )
        Size(result.size.width.toFloat(), result.size.height.toFloat())
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var scrollOffset by remember { mutableStateOf(0) }
    var scrollPixelAccumulator by remember { mutableStateOf(0f) }

    LaunchedEffect(canvasSize, charSize) {
        if (canvasSize.width > 0 && canvasSize.height > 0 && charSize.width > 0 && charSize.height > 0) {
            val cols = (canvasSize.width / charSize.width).toInt().coerceAtLeast(1)
            val rows = (canvasSize.height / charSize.height).toInt().coerceAtLeast(1)
            if (cols != bridge.termCols || rows != bridge.termRows) {
                onResize?.invoke(cols, rows)
            }
        }
    }

    // Auto-scroll: only snap to bottom when user is already at bottom
    LaunchedEffect(renderVersion) {
        if (scrollOffset == 0) {
            scrollPixelAccumulator = 0f
        }
    }

    Canvas(
        modifier = modifier
            .focusable()
            .onSizeChanged { canvasSize = it }
            .pointerInput(charSize.height) {
                detectVerticalDragGestures(
                    onDragEnd = { scrollPixelAccumulator = 0f },
                    onDragCancel = { scrollPixelAccumulator = 0f }
                ) { _, dragAmount ->
                    scrollPixelAccumulator += dragAmount
                    val rowDelta = (scrollPixelAccumulator / charSize.height).toInt()
                    if (rowDelta != 0) {
                        scrollPixelAccumulator -= rowDelta * charSize.height
                        val maxScroll = bridge.screen.activeTranscriptRows
                        scrollOffset = (scrollOffset - rowDelta).coerceIn(0, maxScroll)
                    }
                }
            }
            .pointerInput(onTap) {
                if (onTap != null) {
                    detectTapGestures { onTap() }
                }
            }
    ) {
        drawRect(DEFAULT_BG, Offset.Zero, size)

        bridge.withReadLock {
            val screen = bridge.screen
            val rows = bridge.termRows
            val cols = bridge.termCols
            val effectiveScroll = scrollOffset.coerceIn(0, screen.activeTranscriptRows)

            for (screenRow in 0 until rows) {
                val bufferRow = screenRow - effectiveScroll
                drawTerminalRow(screen, bufferRow, screenRow, cols, charSize, textMeasurer)
            }

            val cursorScreenRow = bridge.cursorRow + effectiveScroll
            if (cursorScreenRow in 0 until rows && bridge.emulator.shouldCursorBeVisible()) {
                val cx = bridge.cursorCol * charSize.width
                val cy = cursorScreenRow * charSize.height
                drawRect(CURSOR_COLOR, Offset(cx, cy), Size(charSize.width, charSize.height), alpha = 0.5f)
            }
        }
    }
}

private fun DrawScope.drawTerminalRow(
    screen: TerminalBuffer,
    bufferRow: Int,
    screenRow: Int,
    cols: Int,
    charSize: Size,
    textMeasurer: TextMeasurer
) {
    val termRow = screen.allocateFullLineIfNecessary(screen.externalToInternalRow(bufferRow))
    val y = screenRow * charSize.height
    if (y >= size.height) return

    var col = 0
    while (col < cols) {
        val style = termRow.getStyle(col)
        val effect = TermuxTextStyle.decodeEffect(style)
        val fgIndex = TermuxTextStyle.decodeForeColor(style)
        val bgIndex = TermuxTextStyle.decodeBackColor(style)

        val inverse = (effect and com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_INVERSE) != 0
        var fg = resolveColor(fgIndex, DEFAULT_FG)
        var bg = resolveColor(bgIndex, DEFAULT_BG)
        if (inverse) {
            val tmp = fg; fg = bg; bg = tmp
        }

        val x = col * charSize.width
        if (x >= size.width) break
        val startCol = termRow.findStartOfColumn(col)
        val codePoint = if (startCol >= 0 && startCol < termRow.spaceUsed) {
            Character.codePointAt(termRow.mText, startCol)
        } else {
            ' '.code
        }
        val ch = if (codePoint in 0x20..0x10FFFF) {
            String(Character.toChars(codePoint))
        } else {
            " "
        }
        val charWidth = if (codePoint > 0x7F) WcWidth.width(codePoint).coerceAtLeast(1) else 1

        if (bg != DEFAULT_BG) {
            drawRect(bg, Offset(x, y), Size(charSize.width * charWidth, charSize.height))
        }

        if (ch.isNotBlank()) {
            val bold = (effect and com.termux.terminal.TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
            val safeX = x.coerceAtMost((size.width - 1f).coerceAtLeast(0f))
            drawText(
                textMeasurer,
                ch,
                topLeft = Offset(safeX, y),
                style = TextStyle(
                    color = fg,
                    fontFamily = TERMINAL_FONT_FAMILY,
                    fontSize = TERMINAL_FONT_SIZE,
                    fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null
                )
            )
        }
        col += charWidth
    }
}

private fun resolveColor(index: Int, default: Color): Color {
    return when {
        index == TermuxTextStyle.COLOR_INDEX_FOREGROUND -> DEFAULT_FG
        index == TermuxTextStyle.COLOR_INDEX_BACKGROUND -> DEFAULT_BG
        (index and 0xFF000000.toInt()) == 0xFF000000.toInt() -> argbToColor(index)
        index in 0..15 -> ANSI_COLORS[index]
        index in 16..255 -> color256(index)
        else -> default
    }
}

private fun argbToColor(argb: Int): Color {
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return Color(r, g, b)
}

private fun color256(index: Int): Color {
    if (index < 16) return ANSI_COLORS[index]
    if (index < 232) {
        val i = index - 16
        val r = (i / 36) * 51
        val g = ((i % 36) / 6) * 51
        val b = (i % 6) * 51
        return Color(r, g, b)
    }
    val gray = 8 + (index - 232) * 10
    return Color(gray, gray, gray)
}
