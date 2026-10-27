package com.example.findme.ui.nav

import com.example.findme.TestGeo.lineNorth
import com.example.findme.TestGeo.pt
import com.example.findme.data.Geo
import com.example.findme.data.NavTrack
import com.example.findme.data.meet.RouteGeom
import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ArrowMotionTest {

    private val road = lineNorth(2_000.0)
    private val track = NavTrack(road, emptyList(), emptyList(), null)
    private val start = 1_000_000L
    private val frameMs = 16L // 60 frames a second

    /** Draws frames from [fromMs] to [toMs]; returns the distance along the road in each. */
    private fun ArrowMotion.frames(fromMs: Long, toMs: Long): List<Double> {
        val out = ArrayList<Double>()
        var t = fromMs
        while (t <= toMs) {
            pose(t)
            out += shownAlongMeters!!
            t += frameMs
        }
        return out
    }

    private fun fix(m: ArrowMotion, ms: Long, along: Double, speed: Double?) =
        m.onFix(ms, Geo.pointAlong(track.points, track.cumulative, along), along, speed, null)

    private fun maxStep(s: List<Double>) = s.zipWithNext { a, b -> abs(b - a) }.maxOrNull() ?: 0.0

    // ---------- Distance = speed × time ----------

    @Test
    fun `at constant speed the arrow covers v times t`() {
        val m = ArrowMotion(track)
        fix(m, start, 100.0, 10.0)
        val s = m.frames(start, start + 1_000)
        assertEquals(110.0, s.last(), 0.3) // 10 m/s for 1 s
        assertEquals(100.0 + 10.0 * 0.5, s[s.size / 2], 0.3)
    }

    @Test
    fun `speed comes from distance over time when GPS has none`() {
        val m = ArrowMotion(track)
        fix(m, start, 0.0, null)
        fix(m, start + 1_000, 12.0, null) // 12 m in 1 s
        val s = m.frames(start + 1_000, start + 1_500)
        assertEquals(12.0 + 12.0 * 0.5, s.last(), 0.3)
    }

    @Test
    fun `braking is predicted with the deceleration and the arrow stops, never reverses`() {
        val m = ArrowMotion(track)
        fix(m, start, 0.0, 14.0)
        fix(m, start + 1_000, 14.0, 10.0) // a = -4 m/s²: stops 2.5 s later
        val s = m.frames(start + 1_000, start + 5_000)
        // s = v·t + a·t²/2 until v = 0: 10·2.5 − 4·2.5²/2 = 12.5 m after the fix.
        assertEquals(14.0 + 12.5, s.last(), 0.3)
        assertTrue("never backwards", s.zipWithNext().all { (a, b) -> b >= a - 1e-9 })
    }

    @Test
    fun `GPS lost - the arrow stops after the prediction horizon`() {
        val m = ArrowMotion(track)
        fix(m, start, 100.0, 10.0)
        val s = m.frames(start, start + 10_000)
        assertEquals(100.0 + 10.0 * ArrowMotion.HORIZON_S, s.last(), 0.3)
    }

    // ---------- A new fix never makes it jump ----------

    @Test
    fun `fix behind the prediction - the arrow slows down, no jump back`() {
        val m = ArrowMotion(track)
        fix(m, start, 100.0, 10.0)
        val before = m.frames(start, start + 1_000) // about 110 m
        fix(m, start + 1_000, 104.0, 10.0) // 6 m behind the prediction, same speed
        val after = m.frames(start + 1_016, start + 4_000)
        val all = before + after
        assertTrue("never backwards", all.zipWithNext().all { (a, b) -> b >= a - 1e-9 })
        assertTrue("no jump, max step ${maxStep(all)}", maxStep(all) < 0.5)
        // Slower for a while, then on the prediction again: 104 + 10·2.5 = 129.
        assertEquals(129.0, after.last(), 0.5)
    }

    @Test
    fun `fix ahead of the prediction - the arrow speeds up and catches up`() {
        val m = ArrowMotion(track)
        fix(m, start, 100.0, 10.0)
        m.frames(start, start + 1_000) // about 110 m
        fix(m, start + 1_000, 120.0, 10.0) // 10 m further than predicted
        val after = m.frames(start + 1_016, start + 2_000)
        assertTrue("no jump, max step ${maxStep(after)}", maxStep(after) < 0.6)
        // Error fades like e^(−t/0.8): after 1 s about 3 m of the 10 are left.
        assertEquals(130.0, after.last(), 3.5)
        assertTrue(after.last() > 125.0)
    }

    @Test
    fun `a far jump moves the arrow at once`() {
        val m = ArrowMotion(track)
        fix(m, start, 100.0, 10.0)
        m.frames(start, start + 1_000)
        fix(m, start + 1_000, 600.0, 10.0) // bad GPS or a shortcut
        assertEquals(600.0, m.frames(start + 1_016, start + 1_016).last(), 0.5)
    }

    // ---------- Along the road ----------

    @Test
    fun `the arrow follows the road's bend, turning smoothly`() {
        // 500 m north, then 500 m east.
        val bend = lineNorth(500.0) + (1..5).map { pt(it * 100.0, 500.0) }
        val bendTrack = NavTrack(bend, emptyList(), emptyList(), null)
        val geom = RouteGeom(bend)
        val m = ArrowMotion(bendTrack)
        m.onFix(start, pt(0.0, 480.0), 480.0, 15.0, null)
        val poses = (0..125).map { m.pose(start + it * 16L)!! } // 2 s at 15 m/s: 480 -> 510 m, round the corner
        poses.forEach { assertTrue("on the road", geom.distanceTo(it.point) < 0.5) }
        assertEquals(0f, poses.first().bearing, 1f)
        assertEquals(90f, poses.last().bearing, 1f)
        val turnSteps = poses.zipWithNext { a, b -> abs(b.bearing - a.bearing) }
        assertTrue("turns gradually, max ${turnSteps.max()}°/frame", turnSteps.max() < 10f)
    }

    @Test
    fun `off the route it moves along the GPS direction`() {
        val m = ArrowMotion(track)
        val here = pt(300.0, 300.0) // far from the road
        m.onFix(start, here, null, 10.0, 90f) // heading east
        m.pose(start)
        var p: Point? = null
        var t = start
        while (t <= start + 1_000) {
            p = m.pose(t)!!.point
            t += frameMs
        }
        assertEquals(10.0, Geo.distanceMeters(here, p!!), 0.5)
        assertEquals(90f, Geo.bearing(here, p), 2f)
    }
}
