package com.opencode.sshterminal.ui.terminal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
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

internal val TERMINAL_FONT_SIZE = 12.sp
internal val TERMINAL_FONT_FAMILY = FontFamily(Font(R.font.meslo_lgs_nf_regular))

// ANSI 16-color base palette (0-15).
private val ANSI_COLORS =
    arrayOf(
        Color(0xFF000000),
        Color(0xFFCD0000),
        Color(0xFF00CD00),
        Color(0xFFCDCD00),
        Color(0xFF0000EE),
        Color(0xFFCD00CD),
        Color(0xFF00CDCD),
        Color(0xFFE5E5E5),
        Color(0xFF7F7F7F),
        Color(0xFFFF0000),
        Color(0xFF00FF00),
        Color(0xFFFFFF00),
        Color(0xFF5C5CFF),
        Color(0xFFFF00FF),
        Color(0xFF00FFFF),
        Color(0xFFFFFFFF),
    )

private val DEFAULT_FG = TerminalForeground
internal val DEFAULT_BG = TerminalBackground
internal val CURSOR_COLOR = TerminalCursor

data class TerminalSelection(
    val startRow: Int,
    val startCol: Int,
    val endRow: Int,
    val endCol: Int,
) {
    val normalized: TerminalSelection
        get() {
            return if (startRow < endRow || (startRow == endRow && startCol <= endCol)) {
                this
            } else {
                TerminalSelection(endRow, endCol, startRow, startCol)
            }
        }

    val isEmpty: Boolean
        get() = startRow == endRow && startCol == endCol
}

data class TerminalScrollCounters(
    val pageUpCount: Int = 0,
    val pageDownCount: Int = 0,
)

data class TerminalRendererCallbacks(
    val onTap: (() -> Unit)? = null,
    val onResize: ((cols: Int, rows: Int) -> Unit)? = null,
    val onCopyText: ((String) -> Unit)? = null,
)

@Composable
fun TerminalRenderer(
    bridge: TermuxTerminalBridge,
    modifier: Modifier = Modifier,
    scrollCounters: TerminalScrollCounters = TerminalScrollCounters(),
    callbacks: TerminalRendererCallbacks = TerminalRendererCallbacks(),
) {
    val renderVersion by bridge.renderVersion.collectAsState()
    val textMeasurer = rememberTextMeasurer()
    val charSize = rememberTerminalCharSize(textMeasurer)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val scrollOffsetState = remember(bridge) { mutableStateOf(0) }
    val scrollPixelAccumulatorState = remember(bridge) { mutableStateOf(0f) }
    val selectionState = remember { mutableStateOf<TerminalSelection?>(null) }
    val gestureState = TerminalGestureState(scrollOffsetState, scrollPixelAccumulatorState, selectionState)
    val effectsState = TerminalEffectsState(scrollOffsetState, scrollPixelAccumulatorState)
    val gestureEnvironment =
        TerminalGestureEnvironment(
            bridge = bridge,
            charWidthPx = charSize.width,
            charHeightPx = charSize.height,
            onTap = callbacks.onTap,
        )
    TerminalRendererEffects(
        config =
            TerminalEffectsConfig(
                bridge = bridge,
                canvasSize = canvasSize,
                charSize = charSize,
                scrollCounters = scrollCounters,
                renderVersion = renderVersion,
                onResize = callbacks.onResize,
            ),
        state = effectsState,
    )

    val surfaceModifier =
        modifier
            .onSizeChanged { canvasSize = it }
            .terminalGestureInput(environment = gestureEnvironment, state = gestureState)
    TerminalRenderSurface(
        bridge = bridge,
        modifier = surfaceModifier,
        state = TerminalRenderState(scrollOffsetState.value, selectionState.value),
        config = TerminalRenderConfig(charSize, textMeasurer, callbacks.onCopyText),
    )
}

@Composable
private fun TerminalRendererEffects(
    config: TerminalEffectsConfig,
    state: TerminalEffectsState,
) {
    val bridge = config.bridge

    LaunchedEffect(config.canvasSize, config.charSize) {
        if (config.canvasSize.width <= 0 || config.canvasSize.height <= 0) return@LaunchedEffect
        if (config.charSize.width <= 0f || config.charSize.height <= 0f) return@LaunchedEffect
        val cols = (config.canvasSize.width / config.charSize.width).toInt().coerceAtLeast(1)
        val rows = (config.canvasSize.height / config.charSize.height).toInt().coerceAtLeast(1)
        if (cols != bridge.termCols || rows != bridge.termRows) config.onResize?.invoke(cols, rows)
    }

    LaunchedEffect(config.renderVersion) {
        if (state.scrollOffsetState.value == 0) {
            state.scrollPixelAccumulatorState.value = 0f
        }
    }

    LaunchedEffect(config.scrollCounters.pageUpCount) {
        if (config.scrollCounters.pageUpCount > 0) {
            val pageRows = bridge.termRows.coerceAtLeast(1)
            val maxScroll = bridge.screen.activeTranscriptRows
            state.scrollOffsetState.value = (state.scrollOffsetState.value + pageRows).coerceIn(0, maxScroll)
        }
    }

    LaunchedEffect(config.scrollCounters.pageDownCount) {
        if (config.scrollCounters.pageDownCount > 0) {
            state.scrollOffsetState.value =
                (state.scrollOffsetState.value - bridge.termRows.coerceAtLeast(1)).coerceAtLeast(0)
        }
    }
}

private data class TerminalEffectsConfig(
    val bridge: TermuxTerminalBridge,
    val canvasSize: IntSize,
    val charSize: Size,
    val scrollCounters: TerminalScrollCounters,
    val renderVersion: Long,
    val onResize: ((cols: Int, rows: Int) -> Unit)?,
)

private data class TerminalEffectsState(
    val scrollOffsetState: MutableState<Int>,
    val scrollPixelAccumulatorState: MutableState<Float>,
)

internal data class TerminalCellLayout(
    val charWidthPx: Float,
    val charHeightPx: Float,
    val scrollOffset: Int,
    val cols: Int,
    val rows: Int,
)

internal fun offsetToCell(
    offset: Offset,
    layout: TerminalCellLayout,
): Pair<Int, Int> {
    val safeCols = layout.cols.coerceAtLeast(1)
    val safeRows = layout.rows.coerceAtLeast(1)
    val safeCharWidth = layout.charWidthPx.coerceAtLeast(1f)
    val safeCharHeight = layout.charHeightPx.coerceAtLeast(1f)
    val screenRow = (offset.y / safeCharHeight).toInt().coerceIn(0, safeRows - 1)
    val col = (offset.x / safeCharWidth).toInt().coerceIn(0, safeCols - 1)
    val bufferRow = screenRow - layout.scrollOffset
    return Pair(bufferRow, col)
}

internal fun extractSelectedText(
    screen: TerminalBuffer,
    selection: TerminalSelection,
    cols: Int,
): String {
    val sel = selection.normalized
    val safeCols = cols.coerceAtLeast(1)
    val sb = StringBuilder()
    for (row in sel.startRow..sel.endRow) {
        val internalRow = screen.externalToInternalRow(row)
        val termRow = screen.allocateFullLineIfNecessary(internalRow)
        val colStart = if (row == sel.startRow) sel.startCol else 0
        val colEnd = if (row == sel.endRow) sel.endCol else safeCols
        var col = colStart
        while (col < colEnd) {
            val startOfCol = termRow.findStartOfColumn(col)
            val codePoint =
                if (startOfCol >= 0 && startOfCol < termRow.spaceUsed) {
                    Character.codePointAt(termRow.mText, startOfCol)
                } else {
                    ' '.code
                }
            if (codePoint in 0x20..0x10FFFF) {
                sb.appendCodePoint(codePoint)
            }
            val width = if (codePoint > 0x7F) WcWidth.width(codePoint).coerceAtLeast(1) else 1
            col += width
        }
        if (row < sel.endRow) sb.append('\n')
    }
    return sb.toString()
}

internal fun DrawScope.drawTerminalRow(
    screen: TerminalBuffer,
    bufferRow: Int,
    screenRow: Int,
    config: TerminalRowRenderConfig,
) {
    val cols = config.cols
    val charSize = config.charSize
    val textMeasurer = config.textMeasurer
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
            val tmp = fg
            fg = bg
            bg = tmp
        }

        val x = col * charSize.width
        if (x >= size.width) break
        val startCol = termRow.findStartOfColumn(col)
        val codePoint =
            if (startCol >= 0 && startCol < termRow.spaceUsed) {
                Character.codePointAt(termRow.mText, startCol)
            } else {
                ' '.code
            }
        val ch =
            if (codePoint in 0x20..0x10FFFF) {
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
                style =
                    TextStyle(
                        color = fg,
                        fontFamily = TERMINAL_FONT_FAMILY,
                        fontSize = TERMINAL_FONT_SIZE,
                        fontWeight = if (bold) androidx.compose.ui.text.font.FontWeight.Bold else null,
                    ),
            )
        }
        col += charWidth
    }
}

internal data class TerminalRowRenderConfig(
    val cols: Int,
    val charSize: Size,
    val textMeasurer: TextMeasurer,
)

private fun resolveColor(
    index: Int,
    default: Color,
): Color {
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

/**
 * Pure scroll-offset calculation extracted from the gesture handler.
 *
 * Convention:
 *  - scrollOffset 0 = live (bottom) view; >0 = scrolled back into history.
 *  - Swipe UP   → dragAmount < 0 → rowDelta < 0 → offset decreases (toward live).
 *  - Swipe DOWN → dragAmount > 0 → rowDelta > 0 → offset increases (history).
 */
internal data class ScrollUpdate(
    val newScrollOffset: Int,
    val newPixelAccumulator: Float,
)

internal fun computeScrollUpdate(
    dragAmount: Float,
    currentScrollOffset: Int,
    pixelAccumulator: Float,
    charHeightPx: Float,
    maxScroll: Int,
): ScrollUpdate {
    val newAccumulator = pixelAccumulator + dragAmount
    val rowDelta = (newAccumulator / charHeightPx).toInt()
    return if (rowDelta != 0) {
        ScrollUpdate(
            newScrollOffset = (currentScrollOffset + rowDelta).coerceIn(0, maxScroll),
            newPixelAccumulator = newAccumulator - rowDelta * charHeightPx,
        )
    } else {
        ScrollUpdate(
            newScrollOffset = currentScrollOffset,
            newPixelAccumulator = newAccumulator,
        )
    }
}
