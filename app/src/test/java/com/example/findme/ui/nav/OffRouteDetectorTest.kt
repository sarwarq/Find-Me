package com.example.findme.ui.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteDetectorTest {

    @Test
    fun `fires after 5 seconds farther than 50 meters`() {
        val d = OffRouteDetector()
        assertFalse(d.update(80.0, 0))
        assertFalse(d.update(80.0, 4_000))
        assertTrue(d.update(80.0, 5_000))
    }

    @Test
    fun `a fix back on the route starts the count again`() {
        val d = OffRouteDetector()
        d.update(80.0, 0)
        d.update(10.0, 3_000) // GPS jumped back: was only noise
        assertFalse(d.update(80.0, 5_000))
        assertTrue(d.update(80.0, 10_000))
    }

    @Test
    fun `close to the route never fires`() {
        val d = OffRouteDetector()
        for (t in 0..60_000L step 1_000) assertFalse(d.update(45.0, t))
    }

    @Test
    fun `waits 15 seconds before firing again`() {
        val d = OffRouteDetector()
        d.update(80.0, 0)
        assertTrue(d.update(80.0, 5_000))
        assertFalse(d.update(80.0, 10_000))
        assertFalse(d.update(80.0, 15_000)) // 5 s off again, but within the cooldown
        assertTrue(d.update(80.0, 20_000))
    }
}
