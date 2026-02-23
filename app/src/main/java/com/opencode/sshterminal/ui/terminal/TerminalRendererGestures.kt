package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import com.opencode.sshterminal.terminal.TermuxTerminalBridge

internal data class TerminalGestureEnvironment(
    val bridge: TermuxTerminalBridge,
    val charWidthPx: Float,
    val charHeightPx: Float,
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
    pointerInput(environment.bridge, environment.charWidthPx, environment.charHeightPx) {
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
        val change = nextPrimaryChange(session.pointerId)
        session.ended = handleGestureChange(change, session, environment, state)
    }

    finishGesture(session, state, environment.onTap)
}

private suspend fun AwaitPointerEventScope.nextPrimaryChange(pointerId: PointerId) =
    awaitPointerEvent().changes.let { changes ->
        changes.firstOrNull { it.id == pointerId } ?: changes.firstOrNull()
    }

private fun handleGestureChange(
    change: PointerInputChange?,
    session: GestureSession,
    environment: TerminalGestureEnvironment,
    state: TerminalGestureState,
): Boolean {
    var ended = change == null
    if (!ended) {
        val currentChange = requireNotNull(change)
        session.pointerId = currentChange.id
        ended = currentChange.changedToUpIgnoreConsumed()
        if (!ended) {
            maybeStartSelection(currentChange.uptimeMillis, session, environment, state)
            maybeStartScroll(currentChange.position, session)
            applyGestureMode(currentChange.position, session, environment, state)
            if (session.selectionMode || session.scrollMode) {
                currentChange.consume()
            }
            session.lastPosition = currentChange.position
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
        session.scrollMode -> updateScroll(position.y - session.lastPosition.y, environment, state)
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
    state.scrollOffsetState.value = result.newScrollOffset
    state.scrollPixelAccumulatorState.value = result.newPixelAccumulator
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
    var ended: Boolean = false,
)
