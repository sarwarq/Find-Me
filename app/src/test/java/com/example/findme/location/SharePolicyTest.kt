package com.example.findme.location

import com.example.findme.TestGeo.pt
import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Test

class SharePolicyTest {

    private val start = 1_700_000_000_000L
    private val place = pt(0.0, 2_000.0) // the meeting place, 2 km north

    private fun decide(
        here: Point,
        now: Long = start + 60_000,
        accuracy: Float = 10f,
        lastWrite: Point? = pt(0.0, 0.0),
        lastWriteMillis: Long = start + 50_000,
    ) = SharePolicy.decide(now, start, here, accuracy, place, lastWrite, lastWriteMillis)

    @Test
    fun `the first fix is sent at once`() {
        assertEquals(ShareDecision.Write, decide(pt(0.0, 0.0), lastWrite = null, lastWriteMillis = 0))
    }

    @Test
    fun `a real move is sent`() {
        assertEquals(ShareDecision.Write, decide(pt(0.0, 50.0)))
    }

    @Test
    fun `small moves are not sent`() {
        assertEquals(ShareDecision.Skip, decide(pt(0.0, 10.0)))
    }

    @Test
    fun `not more often than every 5 seconds`() {
        assertEquals(ShareDecision.Skip, decide(pt(0.0, 50.0), now = start + 53_000))
        assertEquals(ShareDecision.Write, decide(pt(0.0, 50.0), now = start + 55_000))
    }

    @Test
    fun `standing still is sent every 30 seconds`() {
        assertEquals(ShareDecision.Skip, decide(pt(0.0, 0.0), now = start + 80_000))
        assertEquals(ShareDecision.Write, decide(pt(0.0, 0.0), now = start + 80_001))
    }

    @Test
    fun `arrived within 60 m of the place`() {
        assertEquals(ShareDecision.Arrived, decide(pt(20.0, 1_960.0)))
        assertEquals(ShareDecision.Write, decide(pt(0.0, 1_920.0))) // 80 m: not yet
    }

    @Test
    fun `a rough fix can't say I've arrived`() {
        assertEquals(ShareDecision.Skip, decide(pt(0.0, 2_000.0), accuracy = 150f))
        assertEquals(ShareDecision.Write, decide(pt(0.0, 2_000.0), accuracy = 150f, lastWrite = null, lastWriteMillis = 0))
    }

    @Test
    fun `a rough fix is not sent, however far it jumps`() {
        assertEquals(ShareDecision.Skip, decide(pt(0.0, 900.0), accuracy = 500f))
    }

    @Test
    fun `while only rough fixes come, the last good position is sent again every 30 seconds`() {
        assertEquals(ShareDecision.Skip, decide(pt(0.0, 900.0), accuracy = 500f, now = start + 80_000))
        assertEquals(ShareDecision.Resend, decide(pt(0.0, 900.0), accuracy = 500f, now = start + 80_001))
    }

    @Test
    fun `the first fix is sent even when rough, so the others see me at all`() {
        assertEquals(ShareDecision.Write, decide(pt(0.0, 0.0), accuracy = 800f, lastWrite = null, lastWriteMillis = 0))
    }

    @Test
    fun `100 m accuracy still counts, and unknown accuracy is trusted`() {
        assertEquals(ShareDecision.Write, decide(pt(0.0, 50.0), accuracy = 100f))
        assertEquals(ShareDecision.Write, decide(pt(0.0, 50.0), accuracy = 0f))
    }

    @Test
    fun `a position is gone 2 hours after its last update`() {
        val updated = start
        assertEquals(false, SharePolicy.isGone(updated, updated + 2 * 60 * 60 * 1000L))
        assertEquals(true, SharePolicy.isGone(updated, updated + 2 * 60 * 60 * 1000L + 1))
        assertEquals(false, SharePolicy.isGone(null, updated)) // a write still on its way
    }

    @Test
    fun `arrival is sent even right after another write`() {
        assertEquals(ShareDecision.Arrived, decide(pt(0.0, 2_000.0), now = start + 50_500))
    }

    @Test
    fun `stops after 4 hours, even at the place`() {
        val late = start + 4 * 60 * 60 * 1000L + 1
        assertEquals(ShareDecision.Expired, decide(pt(0.0, 2_000.0), now = late))
        val justInTime = start + 4 * 60 * 60 * 1000L
        assertEquals(ShareDecision.Arrived, decide(pt(0.0, 2_000.0), now = justInTime))
    }
}
