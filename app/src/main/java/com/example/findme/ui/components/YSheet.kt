package com.example.findme.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.findme.ui.theme.yPalette
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SheetStop { Collapsed, Half, Expanded }

/** State of the Yandex-style bottom sheet: its top edge and the stop it rests at. */
@Stable
class YSheetState {
    /** Top edge of the sheet in pixels from the top of the screen. */
    var top by mutableFloatStateOf(Float.NaN)
        private set
    var current by mutableStateOf(SheetStop.Collapsed)
        private set
    internal var anchors: Map<SheetStop, Float> = emptyMap()
    private val mutex = MutatorMutex()

    private val minTop get() = anchors.values.minOrNull() ?: 0f
    private val maxTop get() = anchors.values.maxOrNull() ?: 0f

    val isExpanded get() = current == SheetStop.Expanded
    internal val isAtTop get() = anchors.isNotEmpty() && top <= minTop + 0.5f
    internal val isAtAnchor get() = anchors.values.any { abs(it - top) < 1f }

    internal fun dragBy(delta: Float): Float {
        if (anchors.isEmpty() || top.isNaN()) return 0f
        val new = (top + delta).coerceIn(minTop, maxTop)
        val consumed = new - top
        top = new
        return consumed
    }

    internal fun snapTo(stop: SheetStop) {
        val target = anchors[stop] ?: return
        current = stop
        top = target
    }

    suspend fun animateTo(stop: SheetStop, velocity: Float = 0f) {
        val target = anchors[stop] ?: return
        current = stop
        mutex.mutate {
            if (top.isNaN()) {
                top = target
            } else {
                animate(top, target, velocity, spring(dampingRatio = 1f, stiffness = 600f)) { v, _ -> top = v }
            }
        }
    }

    internal suspend fun settle(velocity: Float) {
        if (anchors.isEmpty()) return
        val sorted = anchors.entries.sortedBy { it.value }
        val target = when {
            velocity > 1200f -> sorted.firstOrNull { it.value > top + 1f } ?: sorted.last()
            velocity < -1200f -> sorted.lastOrNull { it.value < top - 1f } ?: sorted.first()
            else -> sorted.minBy { abs(it.value - top) }
        }
        animateTo(target.key, velocity)
    }

    internal suspend fun stopAnimation() = mutex.mutate(MutatePriority.UserInput) {}
}

/**
 * Bottom sheet that rests at [stops] and hands scrolling over to inner lists the way
 * the Yandex Maps sheet does. [contentKey] changes reset the sheet to [initialStop].
 */
@Composable
fun YSheet(
    state: YSheetState,
    contentKey: Any,
    peekHeight: Dp,
    stops: List<SheetStop>,
    initialStop: SheetStop,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val statusTop = WindowInsets.statusBars.getTop(density)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val fullHeight = constraints.maxHeight.toFloat()
        val expandedTop = statusTop + with(density) { 8.dp.toPx() }
        val peekPx = with(density) { peekHeight.toPx() } + navBottom
        val anchors = remember(fullHeight, peekPx, stops, expandedTop) {
            buildMap {
                if (SheetStop.Collapsed in stops) put(SheetStop.Collapsed, (fullHeight - peekPx).coerceAtLeast(expandedTop))
                if (SheetStop.Half in stops) put(SheetStop.Half, fullHeight * 0.45f)
                if (SheetStop.Expanded in stops) put(SheetStop.Expanded, expandedTop)
            }
        }

        val lastKey = remember { arrayOfNulls<Any>(1) }
        LaunchedEffect(contentKey, anchors) {
            state.anchors = anchors
            val keyChanged = lastKey[0] != contentKey
            lastKey[0] = contentKey
            val target = if (keyChanged || state.current !in anchors) initialStop else state.current
            if (state.top.isNaN()) state.snapTo(target) else state.animateTo(target)
        }

        val nested = remember(state) { SheetNestedScroll(state) }
        val sheetHeight = with(density) { (fullHeight - expandedTop).toDp() }

        Surface(
            modifier = Modifier
                .offset { IntOffset(0, (if (state.top.isNaN()) fullHeight else state.top).roundToInt()) }
                .fillMaxWidth()
                .height(sheetHeight)
                .nestedScroll(nested)
                .draggable(
                    state = rememberDraggableState { state.dragBy(it) },
                    orientation = Orientation.Vertical,
                    onDragStarted = { state.stopAnimation() },
                    onDragStopped = { velocity -> state.settle(velocity) },
                ),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = yPalette.sheet,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.fillMaxSize()) {
                Grabber()
                content()
            }
        }
    }
}

private class SheetNestedScroll(private val state: YSheetState) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val dy = available.y
        return if (dy < 0 && source == NestedScrollSource.UserInput && !state.isAtTop) {
            Offset(0f, state.dragBy(dy))
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput && available.y > 0) {
            Offset(0f, state.dragBy(available.y))
        } else {
            Offset.Zero
        }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (state.isAtAnchor) return Velocity.Zero
        state.settle(available.y)
        return available
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (state.isAtAnchor) return Velocity.Zero
        state.settle(available.y)
        return available
    }
}
