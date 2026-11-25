package br.com.voleipro.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.voleipro.domain.Player
import br.com.voleipro.domain.PlayerPosition
import kotlin.math.abs

@Stable
internal class PlayerDragState {
    val sectionBounds = mutableStateMapOf<PlayerPosition, Rect>()
    var playerId by mutableStateOf<Long?>(null)
        private set
    var pointerY by mutableFloatStateOf(0f)
        private set
    var sourceBounds by mutableStateOf(Rect.Zero)
        private set
    private var startY = 0f
    var isMoving by mutableStateOf(false)
        private set

    fun start(id: Long, y: Float, bounds: Rect): Boolean {
        if (playerId != null) return false
        sourceBounds = bounds
        startY = y
        pointerY = y
        playerId = id
        return true
    }

    fun liftedTop(): Float = sourceBounds.top + (pointerY - startY)

    fun move(id: Long, y: Float, touchSlop: Float) {
        if (playerId != id) return
        pointerY = y
        if (abs(y - startY) > touchSlop) isMoving = true
    }

    fun stop(id: Long) {
        if (playerId != id) return
        isMoving = false
        playerId = null
    }

    fun target(positions: List<PlayerPosition>, viewport: Rect): PlayerPosition? {
        if (!isMoving || pointerY < viewport.top || pointerY >= viewport.bottom) return null
        return positions.firstOrNull { position ->
            sectionBounds[position]?.let { pointerY >= it.top && pointerY < it.bottom } == true
        }
    }
}

@Composable
internal fun ScrollWhileDragging(drag: PlayerDragState, scroll: ScrollState, viewport: Rect) {
    val (edge, step) = with(LocalDensity.current) { 56.dp.toPx() to 12.dp.toPx() }
    LaunchedEffect(drag, drag.isMoving, scroll, viewport, edge, step) {
        while (drag.isMoving && viewport.height > 0) {
            withFrameNanos { }
            val y = drag.pointerY
            val distance = minOf(edge, viewport.height / 2)
            val speed = when {
                y < viewport.top + distance -> -(viewport.top + distance - y) / distance
                y > viewport.bottom - distance -> (y - viewport.bottom + distance) / distance
                else -> 0f
            }
            if (speed != 0f) scroll.scrollBy(speed.coerceIn(-1f, 1f) * step)
        }
    }
}

@Composable
internal fun PlayerGestureRow(
    player: Player, drag: PlayerDragState, onDelete: () -> Unit, onDrop: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    val requestDelete by rememberUpdatedState(onDelete)
    val drop by rememberUpdatedState(onDrop)
    val haptic by rememberUpdatedState(LocalHapticFeedback.current)
    // Keep the row in the list while the confirmation dialog is open.
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
        val deleting = value == SwipeToDismissBoxValue.EndToStart
        if (deleting) requestDelete()
        !deleting
    })
    val sourceAlpha by animateFloatAsState(if (drag.playerId == player.id) 0.18f else 1f, label = "origem do arraste")
    var foregroundBounds by remember { mutableStateOf(Rect.Zero) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val dragModifier = Modifier.onGloballyPositioned { coordinates = it }.pointerInput(drag, player.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { point ->
                coordinates?.takeIf { it.isAttached }?.let { row ->
                    if (drag.start(player.id, row.localToRoot(point).y, foregroundBounds)) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
            },
            onDrag = { change, _ ->
                coordinates?.takeIf { it.isAttached }?.let { row ->
                    drag.move(player.id, row.localToRoot(change.position).y, viewConfiguration.touchSlop)
                }
            },
            onDragEnd = {
                if (drag.playerId == player.id) drop()
                drag.stop(player.id)
            },
            onDragCancel = { drag.stop(player.id) },
        )
    }
    DisposableEffect(drag, player.id) {
        onDispose { drag.stop(player.id) }
    }

    SwipeToDismissBox(state = swipe, enableDismissFromStartToEnd = false, gesturesEnabled = drag.playerId == null,
        modifier = Modifier.graphicsLayer { alpha = sourceAlpha }.semantics {
            customActions = listOf(CustomAccessibilityAction("Excluir ${player.name}") {
                requestDelete()
                true
            })
        }, backgroundContent = {
            Surface(Modifier.fillMaxSize(), shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer) {
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }) {
        Surface(Modifier.fillMaxWidth().onGloballyPositioned { row ->
            foregroundBounds = Rect(row.localToRoot(Offset.Zero), Size(row.size.width.toFloat(), row.size.height.toFloat()))
        }, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            content(dragModifier)
        }
    }
}
