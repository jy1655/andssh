package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.opencode.sshterminal.terminal.TermuxTerminalBridge
import kotlin.math.abs

internal data class TerminalGestureEnvironment(
    val bridge: TermuxTerminalBridge,
    val charWidthPx: Float,
    val charHeightPx: Float,
    val fontSizeSp: Int,
    val minFontSizeSp: Int,
    val maxFontSizeSp: Int,
    val onFontSizeChange: ((Int) -> Unit)? = null,
    val onTap: (() -> Unit)? = null,
)

internal data class TerminalGestureState(
    val scrollOffsetState: MutableState<Int>,
    val scrollPixelAccumulatorState: MutableState<Float>,
    val selectionState: MutableState<TerminalSelection?>,
)

internal fun Modifier.terminalGestureInput(
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
): Modifier =
    pointerInput(
        environment.bridge,
        environment.charWidthPx,
        environment.charHeightPx,
        environment.fontSizeSp,
        environment.minFontSizeSp,
        environment.maxFontSizeSp,
    ) {
        awaitEachGesture {
            processGesture(environment, state)
        }
    }

private suspend fun AwaitPointerEventScope.processGesture(
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
) {
    val down = awaitFirstDown(requireUnconsumed = false)
    val session =
        GestureSession(
            downPosition = down.position,
            longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis,
            touchSlop = viewConfiguration.touchSlop,
            pointerId = down.id,
            lastPosition = down.position,
        )

    while (!session.ended) {
        val event = awaitPointerEvent()
        session.ended = handleGestureEvent(event, session, environment, state)
    }

    finishGesture(session, state, environment.onTap)
}

@Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth")
private fun handleGestureEvent(
    event: PointerEvent,
    session: GestureSession,
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
): Boolean {
    var ended = false
    if (session.pinchMode) {
        val activePointers = event.changes.filter { it.pressed }
        if (activePointers.size >= 2) {
            val currentDistance = (activePointers[0].position - activePointers[1].position).getDistance()
            val newFontSize =
                computePinchFontSize(
                    initialFontSizeSp = session.pinchStartFontSizeSp,
                    initialDistancePx = session.pinchStartDistancePx,
                    currentDistancePx = currentDistance,
                    minFontSizeSp = environment.minFontSizeSp,
                    maxFontSizeSp = environment.maxFontSizeSp,
                )
            if (newFontSize != session.lastPinchFontSizeSp) {
                session.lastPinchFontSizeSp = newFontSize
                environment.onFontSizeChange?.invoke(newFontSize)
            }
        }
        event.changes.forEach { it.consume() }
        ended = event.changes.none { it.pressed }
    } else {
        val activePointers = event.changes.filter { it.pressed }
        val canStartPinch =
            environment.onFontSizeChange != null &&
                !session.selectionMode &&
                !session.scrollMode &&
                activePointers.size >= 2
        if (canStartPinch) {
            val distance = (activePointers[0].position - activePointers[1].position).getDistance()
            if (distance > 0f) {
                session.pinchMode = true
                session.scrollMode = true
                session.pinchStartDistancePx = distance
                session.pinchStartFontSizeSp = environment.fontSizeSp
                session.lastPinchFontSizeSp = environment.fontSizeSp
                state.selectionState.value = null
                state.scrollPixelAccumulatorState.value = 0f
                event.changes.forEach { it.consume() }
            }
        } else {
            val currentChange = event.changes.firstOrNull { it.id == session.pointerId } ?: event.changes.firstOrNull()
            if (currentChange == null) {
                ended = true
            } else {
                session.pointerId = currentChange.id
                if (currentChange.changedToUpIgnoreConsumed()) {
                    ended = event.changes.none { it.pressed }
                } else {
                    maybeStartSelection(currentChange.uptimeMillis, session, environment, state)
                    maybeStartScroll(currentChange.position, session)
                    applyGestureMode(currentChange.position, session, environment, state)
                    if (session.selectionMode || session.scrollMode) {
                        currentChange.consume()
                    }
                    session.lastPosition = currentChange.position
                }
            }
        }
    }
    return ended
}

private fun maybeStartSelection(
    uptimeMillis: Long,
    session: GestureSession,
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
) {
    if (session.selectionMode || session.scrollMode || uptimeMillis < session.longPressAt) {
        return
    }
    val (bufferRow, col) =
        offsetToCell(
            offset = session.downPosition,
            layout =
                TerminalCellLayout(
                    charWidthPx = environment.charWidthPx,
                    charHeightPx = environment.charHeightPx,
                    scrollOffset = state.scrollOffsetState.value,
                    cols = environment.bridge.termCols,
                    rows = environment.bridge.termRows,
                ),
        )
    state.selectionState.value =
        TerminalSelection(
            startRow = bufferRow,
            startCol = col,
            endRow = bufferRow,
            endCol = col,
        )
    session.selectionMode = true
}

private fun maybeStartScroll(
    position: Offset,
    session: GestureSession,
) {
    if (session.selectionMode || session.scrollMode) return
    val dragDistance = (position - session.downPosition).getDistance()
    if (dragDistance > session.touchSlop) {
        session.scrollMode = true
    }
}

private fun applyGestureMode(
    position: Offset,
    session: GestureSession,
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
) {
    when {
        session.selectionMode -> updateSelection(position, environment, state)
        session.scrollMode -> updateScroll(position, position.y - session.lastPosition.y, environment, state)
    }
}

private fun updateSelection(
    position: Offset,
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
) {
    val (bufferRow, col) =
        offsetToCell(
            offset = position,
            layout =
                TerminalCellLayout(
                    charWidthPx = environment.charWidthPx,
                    charHeightPx = environment.charHeightPx,
                    scrollOffset = state.scrollOffsetState.value,
                    cols = environment.bridge.termCols,
                    rows = environment.bridge.termRows,
                ),
        )
    state.selectionState.value =
        state.selectionState.value?.copy(
            endRow = bufferRow,
            endCol = (col + 1).coerceAtMost(environment.bridge.termCols),
        )
}

private fun updateScroll(
    position: Offset,
    dragAmount: Float,
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
) {
    if (environment.charHeightPx <= 0f) return
    val result =
        computeScrollUpdate(
            dragAmount = dragAmount,
            currentScrollOffset = state.scrollOffsetState.value,
            pixelAccumulator = state.scrollPixelAccumulatorState.value,
            charHeightPx = environment.charHeightPx,
            maxScroll = environment.bridge.screen.activeTranscriptRows,
        )
    state.scrollPixelAccumulatorState.value = result.newPixelAccumulator
    if (result.rowDelta == 0) return

    val bridge = environment.bridge
    if (bridge.isMouseTrackingActive()) {
        val (col, row) = visibleCellFromOffset(position, environment)
        bridge.sendMouseWheel(
            // Keep TUI wheel direction aligned with local terminal scrollback gesture direction.
            scrollUp = result.rowDelta > 0,
            col = col + 1,
            row = row + 1,
            repeatCount = abs(result.rowDelta),
        )
        state.scrollOffsetState.value = 0
        return
    }

    if (bridge.isAlternateBufferActive()) {
        val keyCode =
            if (result.rowDelta > 0) {
                android.view.KeyEvent.KEYCODE_DPAD_UP
            } else {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN
            }
        repeat(abs(result.rowDelta)) {
            bridge.sendKeyCode(keyCode)
        }
        state.scrollOffsetState.value = 0
        return
    }

    state.scrollOffsetState.value = result.newScrollOffset
}

private fun visibleCellFromOffset(
    offset: Offset,
    environment: TerminalGestureEnvironment,
): Pair<Int, Int> {
    val (row, col) =
        offsetToCell(
            offset = offset,
            layout =
                TerminalCellLayout(
                    charWidthPx = environment.charWidthPx,
                    charHeightPx = environment.charHeightPx,
                    scrollOffset = 0,
                    cols = environment.bridge.termCols,
                    rows = environment.bridge.termRows,
                ),
        )
    return Pair(col, row)
}

private fun finishGesture(
    session: GestureSession,
    state: TerminalGestureState,
    onTap: (() -> Unit)?,
) {
    if (session.scrollMode) {
        state.scrollPixelAccumulatorState.value = 0f
    }
    if (session.selectionMode || session.scrollMode || !session.ended) return
    val selection = state.selectionState.value
    if (selection != null) {
        state.selectionState.value = null
    } else {
        onTap?.invoke()
    }
}

private data class GestureSession(
    val downPosition: Offset,
    val longPressAt: Long,
    val touchSlop: Float,
    var pointerId: PointerId,
    var lastPosition: Offset,
    var selectionMode: Boolean = false,
    var scrollMode: Boolean = false,
    var pinchMode: Boolean = false,
    var pinchStartDistancePx: Float = 0f,
    var pinchStartFontSizeSp: Int = 0,
    var lastPinchFontSizeSp: Int = 0,
    var ended: Boolean = false,
)
