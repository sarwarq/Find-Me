package com.example.findme.ui.nav

import com.example.findme.data.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OffRouteDetectorTest {

    /** Fixes once a second off the route, moving away 10 m each; the second of the one that fired, or null. */
    private fun OffRouteDetector.firesAfter(fixes: Int, speed: Double?, startMs: Long = 0): Int? =
        (1..fixes).firstOrNull { update(false, 50.0 + it * 10, speed, startMs + it * 1_000L) }

    @Test
    fun `a car leaving the route is noticed after 6 fixes`() {
        assertEquals(6, OffRouteDetector.forMode(TravelMode.Car).firesAfter(20, speed = 10.0))
    }

    @Test
    fun `without speed it takes 11 fixes`() {
        assertEquals(11, OffRouteDetector.forMode(TravelMode.Car).firesAfter(20, speed = null))
    }

    @Test
    fun `a crawling car counts once per fix, a walker twice`() {
        assertEquals(11, OffRouteDetector.forMode(TravelMode.Car).firesAfter(20, speed = 0.5))
        assertEquals(6, OffRouteDetector.forMode(TravelMode.Walk).firesAfter(20, speed = 0.5))
    }

    @Test
    fun `standing still off the route is not leaving it`() {
        val d = OffRouteDetector.forMode(TravelMode.Car)
        for (t in 0..60_000L step 1_000) assertFalse(d.update(false, 70.0, 0.0, t))
    }

    @Test
    fun `a fix back on the route starts the count again`() {
        val d = OffRouteDetector.forMode(TravelMode.Car)
        assertEquals(null, d.firesAfter(5, speed = 10.0))
        d.update(true, 5.0, 10.0, 5_500) // GPS jumped back: was only noise
        assertEquals(6, d.firesAfter(20, speed = 10.0, startMs = 6_000))
    }

    @Test
    fun `waits 15 seconds before firing again`() {
        val d = OffRouteDetector.forMode(TravelMode.Car)
        assertEquals(6, d.firesAfter(6, speed = 10.0)) // at 6 s
        // Still off: counted again by 12 s, but held back until 21 s.
        val next = (7..40).first { d.update(false, 1_000.0 + it, 10.0, it * 1_000L) }
        assertEquals(21, next)
    }
}
