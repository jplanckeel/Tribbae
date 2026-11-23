package data

import kotlin.test.Test
import kotlin.test.assertEquals

/** Mêmes cas que le web (frontend/src/lib/zoom.test.ts). */
class ZoomTest {
    @Test
    fun staysBetweenTheWholeImageAndFourTimes() {
        assertEquals(1f, Zoom.clampScale(0.5f))
        assertEquals(2f, Zoom.clampScale(2f))
        assertEquals(4f, Zoom.clampScale(9f))
    }

    @Test
    fun doubleTapZoomsInThenBackToTheWholeImage() {
        assertEquals(2.5f, Zoom.toggleScale(1f))
        assertEquals(1f, Zoom.toggleScale(2.5f))
        assertEquals(1f, Zoom.toggleScale(1.5f))
    }

    @Test
    fun neverPansBeyondTheEdges() {
        assertEquals(0f to 0f, Zoom.clampPan(50f, -50f, 1f, 400f, 800f))
        assertEquals(50f to -50f, Zoom.clampPan(50f, -50f, 2f, 400f, 800f))
        assertEquals(200f to -400f, Zoom.clampPan(500f, -900f, 2f, 400f, 800f))
    }
}
