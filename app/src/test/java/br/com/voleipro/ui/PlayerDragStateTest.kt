package br.com.voleipro.ui

import androidx.compose.ui.geometry.Rect
import br.com.voleipro.domain.PlayerPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerDragStateTest {
    private val viewport = Rect(0f, 0f, 200f, 100f)
    private val positions = listOf(PlayerPosition.SETTER, PlayerPosition.LIBERO)

    @Test fun longPressAndSmallVerticalMovementDoNotSelectDropTarget() {
        val drag = dragFromSetterSection(y = 47f)

        drag.move(1L, 53f, touchSlop = 8f)

        assertNull(drag.target(positions, viewport))
    }

    @Test fun dropRequiresAValidVisibleSectionAndClearsOnStop() {
        val drag = dragFromSetterSection(y = 20f)

        drag.move(1L, 60f, touchSlop = 8f)
        assertEquals(PlayerPosition.LIBERO, drag.target(positions, viewport))
        assertNull(drag.target(listOf(PlayerPosition.SETTER), viewport))

        drag.move(1L, 110f, touchSlop = 8f)
        assertNull(drag.target(positions, viewport))

        drag.move(1L, 60f, touchSlop = 8f)
        drag.stop(1L)
        assertNull(drag.target(positions, viewport))
    }

    @Test fun liftedRowKeepsItsOffsetFromTheFinger() {
        val drag = dragFromSetterSection(y = 20f)
        assertEquals(10f, drag.liftedTop(), 0f)

        drag.move(1L, 65f, touchSlop = 8f)

        assertEquals(55f, drag.liftedTop(), 0f)
    }

    private fun dragFromSetterSection(y: Float) = PlayerDragState().apply {
        sectionBounds[PlayerPosition.SETTER] = Rect(0f, 0f, 200f, 50f)
        sectionBounds[PlayerPosition.LIBERO] = Rect(0f, 50f, 200f, 150f)
        start(1L, y, Rect(0f, 10f, 200f, 40f))
    }
}
