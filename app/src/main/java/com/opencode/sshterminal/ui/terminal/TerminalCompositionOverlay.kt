package com.opencode.sshterminal.ui.terminal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.opencode.sshterminal.terminal.resolveTerminalComposeFontFamily
import kotlin.math.roundToInt

@Suppress("LongParameterList")
@Composable
internal fun TerminalCompositionOverlay(
    composingText: String,
    cursorOffsetX: Float,
    cursorOffsetY: Float,
    charHeight: Float,
    terminalSize: IntSize,
    terminalFontId: String,
) {
    if (composingText.isEmpty() || terminalSize.width <= 0) {
        return
    }

    val popupPositionProvider =
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val cursorX = anchorBounds.left + cursorOffsetX.roundToInt()
                val cursorY = anchorBounds.top + cursorOffsetY.roundToInt()
                val verticalGap = 4
                val cursorHeight = charHeight.roundToInt().coerceAtLeast(0)
                val shouldFlipAbove = cursorOffsetY > (terminalSize.height * (2f / 3f))

                val rawX = if (layoutDirection == LayoutDirection.Rtl) cursorX - popupContentSize.width else cursorX
                val rawY =
                    if (shouldFlipAbove) {
                        cursorY - popupContentSize.height - verticalGap
                    } else {
                        cursorY + cursorHeight + verticalGap
                    }

                val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
                val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)

                return IntOffset(
                    x = rawX.coerceIn(0, maxX),
                    y = rawY.coerceIn(0, maxY),
                )
            }
        }

    val backgroundColor = MaterialTheme.colorScheme.inverseSurface
    val contentColor = MaterialTheme.colorScheme.inverseOnSurface
    val compositionFontFamily =
        remember(terminalFontId) {
            resolveTerminalComposeFontFamily(terminalFontId)
        }
    Popup(
        popupPositionProvider = popupPositionProvider,
        properties =
            PopupProperties(
                focusable = false,
                dismissOnClickOutside = false,
            ),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            tonalElevation = 4.dp,
            color = backgroundColor,
            contentColor = contentColor,
            modifier = Modifier.border(1.dp, contentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp)),
        ) {
            Box(
                modifier = Modifier.size(width = 45.dp, height = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = composingText,
                    fontFamily = compositionFontFamily,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
