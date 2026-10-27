package com.example.findme.ui.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverspeedAlarmTest {

    @Test
    fun `beeps more than 10 km per hour over the limit`() {
        val a = OverspeedAlarm()
        assertFalse(a.update(68, 60, 0))
        assertFalse(a.update(70, 60, 1_000))
        assertTrue(a.update(71, 60, 2_000))
    }

    @Test
    fun `repeats every 20 s while still over`() {
        val a = OverspeedAlarm()
        assertTrue(a.update(80, 60, 0))
        assertFalse(a.update(80, 60, 10_000))
        assertTrue(a.update(80, 60, 20_000))
    }

    @Test
    fun `hovering around the line does not beep again and again`() {
        val a = OverspeedAlarm()
        assertTrue(a.update(72, 60, 0))
        assertFalse(a.update(68, 60, 1_000)) // still over by more than 5: counts as speeding
        assertFalse(a.update(72, 60, 2_000))
        assertFalse(a.update(64, 60, 3_000)) // slowed down
        assertTrue(a.update(72, 60, 4_000)) // over again: beeps at once
    }

    @Test
    fun `no limit, no beep`() {
        val a = OverspeedAlarm()
        assertFalse(a.update(150, null, 0))
        assertFalse(a.update(150, 0, 1_000))
    }
}
