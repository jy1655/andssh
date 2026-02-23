package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.unit.dp
import com.opencode.sshterminal.R
import com.opencode.sshterminal.terminal.TermuxTerminalBridge

@Composable
internal fun TerminalRenderSurface(
    bridge: TermuxTerminalBridge,
    modifier: Modifier,
    state: TerminalRenderState,
    config: TerminalRenderConfig,
) {
    Box(modifier = modifier.focusable()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTerminalSurface(
                bridge = bridge,
                scrollOffset = state.scrollOffset,
                selection = state.selection,
                charSize = config.charSize,
                textMeasurer = config.textMeasurer,
            )
        }
        val selection = state.selection
        val onCopyText = config.onCopyText
        if (selection != null && !selection.isEmpty && onCopyText != null) {
            Button(
                onClick = {
                    bridge.withReadLock {
                        val text = extractSelectedText(bridge.screen, selection, bridge.termCols)
                        if (text.isNotEmpty()) {
                            onCopyText(text)
                        }
                    }
                },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
            ) {
                Text(stringResource(R.string.terminal_copy))
            }
        }
    }
}

private fun DrawScope.drawTerminalSurface(
    bridge: TermuxTerminalBridge,
    scrollOffset: Int,
    selection: TerminalSelection?,
    charSize: Size,
    textMeasurer: TextMeasurer,
) {
    drawRect(DEFAULT_BG, Offset.Zero, size)

    bridge.withReadLock {
        val screen = bridge.screen
        val rows = bridge.termRows
        val cols = bridge.termCols
        val effectiveScroll = scrollOffset.coerceIn(0, screen.activeTranscriptRows)
        val rowConfig =
            TerminalRowRenderConfig(
                cols = cols,
                charSize = charSize,
                textMeasurer = textMeasurer,
            )

        for (screenRow in 0 until rows) {
            val bufferRow = screenRow - effectiveScroll
            drawTerminalRow(
                screen = screen,
                bufferRow = bufferRow,
                screenRow = screenRow,
                config = rowConfig,
            )
        }
        drawCursor(bridge, effectiveScroll, rows, charSize)
        drawSelection(selection, rows, cols, effectiveScroll, charSize)
    }
}

private fun DrawScope.drawCursor(
    bridge: TermuxTerminalBridge,
    effectiveScroll: Int,
    rows: Int,
    charSize: Size,
) {
    val cursorScreenRow = bridge.cursorRow + effectiveScroll
    if (cursorScreenRow !in 0 until rows || !bridge.emulator.shouldCursorBeVisible()) return
    val cx = bridge.cursorCol * charSize.width
    val cy = cursorScreenRow * charSize.height
    drawRect(CURSOR_COLOR, Offset(cx, cy), Size(charSize.width, charSize.height), alpha = 0.5f)
}

private fun DrawScope.drawSelection(
    selection: TerminalSelection?,
    rows: Int,
    cols: Int,
    effectiveScroll: Int,
    charSize: Size,
) {
    val normalized = selection?.normalized ?: return
    for (screenRow in 0 until rows) {
        val bufferRow = screenRow - effectiveScroll
        if (bufferRow >= normalized.startRow && bufferRow <= normalized.endRow) {
            val startCol = if (bufferRow == normalized.startRow) normalized.startCol else 0
            val endCol = if (bufferRow == normalized.endRow) normalized.endCol else cols
            if (endCol > startCol) {
                val x = startCol * charSize.width
                val w = (endCol - startCol) * charSize.width
                val y = screenRow * charSize.height
                drawRect(
                    color = Color(0x5033B5E5),
                    topLeft = Offset(x, y),
                    size = Size(w, charSize.height),
                )
            }
        }
    }
}

internal data class TerminalRenderState(
    val scrollOffset: Int,
    val selection: TerminalSelection?,
)

internal data class TerminalRenderConfig(
    val charSize: Size,
    val textMeasurer: TextMeasurer,
    val onCopyText: ((String) -> Unit)?,
)
