package com.example.findme.ui.nav

import com.example.findme.TestGeo.lineNorth
import com.example.findme.TestGeo.pt
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.ManeuverPoint
import com.example.findme.data.NavTrack
import com.example.findme.data.RouteVariant
import com.example.findme.data.TransitStep
import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavTrackerTest {

    private val now = 1_700_000_000_000L

    private fun variant(
        points: List<Point>,
        seconds: Double = 600.0,
        maneuvers: List<ManeuverPoint> = emptyList(),
        speedLimits: List<Float?> = emptyList(),
        stopSegments: List<Int> = emptyList(),
        steps: List<TransitStep> = emptyList(),
        withTrack: Boolean = true,
    ) = RouteVariant(
        durationText = "", durationSeconds = seconds, distanceText = null, details = null,
        lines = emptyList(), legs = emptyList(), bbox = null,
        track = if (withTrack) NavTrack(points, speedLimits, maneuvers, null) else null,
        stopSegments = stopSegments, steps = steps,
    )

    /** One position fix [east]/[north] meters from the origin. */
    private fun NavTracker.at(east: Double, north: Double, speed: Double? = null): NavUpdate =
        requireNotNull(update(pt(east, north), speed, now))

    // ---------- Distance and time ----------

    @Test
    fun `remaining distance and time on a straight road`() {
        val t = NavTracker(variant(lineNorth(1000.0), seconds = 600.0))
        val u = t.at(5.0, 300.0)
        assertEquals(700.0, u.progress.remainingMeters, 3.0)
        assertEquals(420.0, u.progress.remainingSeconds, 3.0) // 70 % of 10 min
        assertEquals(0.3f, u.progress.fraction, 0.01f)
        // Where the arrow is on the track: the map hides the line behind it.
        assertEquals(3.0, u.progress.trackSegment + u.progress.trackT, 0.05) // 300 m = start of segment 3
        assertFalse(u.arrived)
    }

    @Test
    fun `distance from the route line, for rebuilding the route`() {
        val t = NavTracker(variant(lineNorth(1000.0)))
        assertEquals(5.0, t.at(5.0, 300.0).offRouteMeters, 0.5)
        assertEquals(80.0, t.at(-80.0, 320.0).offRouteMeters, 0.5)
    }

    @Test
    fun `arrived in the last 25 meters`() {
        val t = NavTracker(variant(lineNorth(1000.0)))
        assertFalse(t.at(0.0, 960.0).arrived)
        assertTrue(t.at(0.0, 990.0).arrived)
    }

    @Test
    fun `no track, no guidance`() {
        assertNull(NavTracker(variant(lineNorth(1000.0), withTrack = false)).update(pt(0.0, 0.0), null, now))
    }

    @Test
    fun `speed and speed limit in km per hour`() {
        val t = NavTracker(variant(lineNorth(1000.0), speedLimits = List(10) { 13.89f }))
        val p = t.at(0.0, 250.0, speed = 10.0).progress
        assertEquals(36, p.speedKmh)
        assertEquals(50, p.limitKmh)
    }

    @Test
    fun `next maneuver and the distance to it`() {
        val t = NavTracker(variant(lineNorth(1000.0), maneuvers = listOf(ManeuverPoint(5, Maneuver.Right))))
        val p = t.at(0.0, 200.0).progress
        assertEquals(Maneuver.Right, p.next)
        assertEquals(300.0, p.nextInMeters, 3.0)
    }

    // ---------- Direction of the map (the bug from 2026-10-27) ----------

    @Test
    fun `heading follows the road even when standing still`() {
        val north = NavTracker(variant(lineNorth(1000.0)))
        assertEquals(0f, north.at(0.0, 200.0, speed = 0.0).routeHeading!!, 1f)

        val south = NavTracker(variant(lineNorth(1000.0).reversed()))
        assertEquals(180f, south.at(0.0, 800.0, speed = 0.0).routeHeading!!, 1f)
    }

    @Test
    fun `heading turns into a bend before reaching it`() {
        // 500 m north, then 500 m east.
        val road = lineNorth(500.0) + (1..5).map { pt(it * 100.0, 500.0) }
        val t = NavTracker(variant(road))
        assertEquals(0f, t.at(0.0, 200.0).routeHeading!!, 1f)
        val nearCorner = t.at(0.0, 490.0).routeHeading!!
        assertTrue("turning, got $nearCorner", nearCorner > 10f && nearCorner < 80f)
        assertEquals(90f, t.at(200.0, 500.0).routeHeading!!, 1f)
    }

    @Test
    fun `no road heading when far from the route`() {
        val t = NavTracker(variant(lineNorth(1000.0)))
        assertNull(t.at(100.0, 300.0).routeHeading)
    }

    @Test
    fun `does not jump to a later part of the route that passes nearby`() {
        // There and back: north 500 m, then south again 10 m to the east (1 point per meter).
        val road = (0..500).map { pt(0.0, it.toDouble()) } + (500 downTo 0).map { pt(10.0, it.toDouble()) }
        val t = NavTracker(variant(road))
        // Closer to the way back, but I've only just started: still on the way there.
        val u = t.at(8.0, 100.0)
        assertTrue("remaining ${u.progress.remainingMeters}", u.progress.remainingMeters > 800)
    }

    // ---------- Stops on the way ----------

    @Test
    fun `stops are reached one by one`() {
        val t = NavTracker(variant(lineNorth(1000.0), stopSegments = listOf(3, 6)))

        val before = t.at(0.0, 200.0)
        assertEquals(emptyList<Int>(), before.reachedStops)
        assertEquals(0, before.progress.nextStop)
        assertEquals(100.0, before.progress.toStopMeters, 3.0)

        val first = t.at(0.0, 280.0) // within 30 m counts as reached
        assertEquals(listOf(0), first.reachedStops)
        assertEquals(1, first.progress.nextStop)

        val again = t.at(0.0, 300.0)
        assertEquals("not reported twice", emptyList<Int>(), again.reachedStops)

        val second = t.at(0.0, 650.0)
        assertEquals(listOf(1), second.reachedStops)
        assertEquals(-1, second.progress.nextStop)
        assertEquals(2, t.reachedStops)
    }

    @Test
    fun `passing several stops at once reports all of them`() {
        val t = NavTracker(variant(lineNorth(1000.0), stopSegments = listOf(3, 6)))
        assertEquals(listOf(0, 1), t.at(0.0, 650.0).reachedStops)
    }

    // ---------- Public transport ----------

    @Test
    fun `transit time comes from the timetable of the last ride`() {
        val steps = listOf(
            TransitStep(LegKind.Walk, startSegment = 0, seconds = 120.0),
            TransitStep(LegKind.Transport, startSegment = 3, seconds = 600.0, arrivalMillis = now + 500_000),
            TransitStep(LegKind.Walk, startSegment = 8, seconds = 60.0),
        )
        val p = NavTracker(variant(lineNorth(1000.0), steps = steps)).at(0.0, 50.0).progress
        assertEquals(0, p.step)
        assertEquals(560.0, p.remainingSeconds, 0.5) // bus arrives in 500 s + 60 s walk
    }

    @Test
    fun `transit time without a timetable is split by distance`() {
        val steps = listOf(
            TransitStep(LegKind.Walk, startSegment = 0, seconds = 120.0),
            TransitStep(LegKind.Transport, startSegment = 5, seconds = 300.0),
        )
        val p = NavTracker(variant(lineNorth(1000.0), steps = steps)).at(0.0, 250.0).progress
        assertEquals(0, p.step)
        assertEquals(360.0, p.remainingSeconds, 3.0) // half of the walk + the ride
    }
}
