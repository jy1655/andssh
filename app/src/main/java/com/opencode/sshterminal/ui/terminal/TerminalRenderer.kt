package com.opencode.sshterminal.ui.terminal

import android.graphics.Typeface
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.core.content.res.ResourcesCompat
import com.opencode.sshterminal.terminal.TerminalColorSchemePreset
import com.opencode.sshterminal.terminal.TerminalFontPreset
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import com.opencode.sshterminal.terminal.applyColorScheme
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.WcWidth

internal const val DEFAULT_FONT_SIZE_SP = 12

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

@Suppress("LongParameterList")
@Composable
fun TerminalRenderer(
    bridge: TermuxTerminalBridge,
    terminalColorSchemeId: String,
    terminalFontId: String = TerminalFontPreset.MESLO_NERD.id,
    terminalCursorStyle: Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE,
    modifier: Modifier = Modifier,
    scrollCounters: TerminalScrollCounters = TerminalScrollCounters(),
    callbacks: TerminalRendererCallbacks = TerminalRendererCallbacks(),
) {
    val context = LocalContext.current
    val renderVersion by bridge.renderVersion.collectAsState()
    val termuxRenderer =
        remember(context, terminalFontId) {
            val preset = TerminalFontPreset.fromId(terminalFontId)
            val typeface = ResourcesCompat.getFont(context, preset.fontResId) ?: Typeface.MONOSPACE
            com.termux.view.TerminalRenderer(DEFAULT_FONT_SIZE_SP, typeface)
        }
    val charSize = remember(termuxRenderer) { Size(termuxRenderer.fontWidth, termuxRenderer.fontLineSpacing.toFloat()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var colorSchemeRenderVersion by remember(bridge) { mutableStateOf(0L) }
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
                renderVersion = renderVersion + colorSchemeRenderVersion,
                terminalColorSchemeId = terminalColorSchemeId,
                terminalCursorStyle = terminalCursorStyle,
                onResize = callbacks.onResize,
                onColorSchemeApplied = { colorSchemeRenderVersion++ },
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
        config = TerminalRenderConfig(charSize, termuxRenderer, callbacks.onCopyText),
    )
}

@Composable
private fun TerminalRendererEffects(
    config: TerminalEffectsConfig,
    state: TerminalEffectsState,
) {
    val bridge = config.bridge

    LaunchedEffect(bridge, config.terminalColorSchemeId) {
        val preset = TerminalColorSchemePreset.fromId(config.terminalColorSchemeId)
        applyColorScheme(bridge.emulator, preset)
        config.onColorSchemeApplied()
    }

    LaunchedEffect(bridge, config.terminalCursorStyle) {
        bridge.setTerminalCursorStyle(config.terminalCursorStyle)
    }

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
    val terminalColorSchemeId: String,
    val terminalCursorStyle: Int,
    val onResize: ((cols: Int, rows: Int) -> Unit)?,
    val onColorSchemeApplied: () -> Unit,
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

/**
 * Pure scroll-offset calculation extracted from the gesture handler.
 *
 * Convention:
 *  - scrollOffset 0 = live (bottom) view; >0 = scrolled back into history.
 *  - Swipe UP   → dragAmount < 0 → rowDelta < 0 → offset decreases (toward live).
 *  - Swipe DOWN → dragAmount > 0 → rowDelta > 0 → offset increases (history).
 */
internal data class ScrollUpdate(
    val rowDelta: Int,
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
            rowDelta = rowDelta,
            newScrollOffset = (currentScrollOffset + rowDelta).coerceIn(0, maxScroll),
            newPixelAccumulator = newAccumulator - rowDelta * charHeightPx,
        )
    } else {
        ScrollUpdate(
            rowDelta = 0,
            newScrollOffset = currentScrollOffset,
            newPixelAccumulator = newAccumulator,
        )
    }
}
