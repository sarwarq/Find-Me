package com.example.findme.data

import com.example.findme.TestGeo.lineNorth
import com.example.findme.TestGeo.pt
import com.yandex.mapkit.geometry.Point
import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {

    @Test
    fun `one degree of latitude is about 111 km`() {
        assertEquals(111_195.0, Geo.distanceMeters(Point(0.0, 0.0), Point(1.0, 0.0)), 50.0)
    }

    @Test
    fun `distance between test points matches meters`() {
        assertEquals(500.0, Geo.distanceMeters(pt(0.0, 0.0), pt(300.0, 400.0)), 1.0)
    }

    @Test
    fun `bearing gives compass directions`() {
        val o = pt(0.0, 0.0)
        assertEquals(0f, Geo.bearing(o, pt(0.0, 100.0)), 0.5f)
        assertEquals(90f, Geo.bearing(o, pt(100.0, 0.0)), 0.5f)
        assertEquals(180f, Geo.bearing(o, pt(0.0, -100.0)), 0.5f)
        assertEquals(270f, Geo.bearing(o, pt(-100.0, 0.0)), 0.5f)
        assertEquals(45f, Geo.bearing(o, pt(100.0, 100.0)), 0.5f)
    }

    @Test
    fun `bearing is never negative`() {
        val b = Geo.bearing(pt(0.0, 0.0), pt(-100.0, 100.0))
        assertEquals(315f, b, 0.5f)
    }

    @Test
    fun `distance to segment and where the closest point is`() {
        val (d, t) = Geo.distanceToSegment(pt(20.0, 30.0), pt(0.0, 0.0), pt(0.0, 100.0))
        assertEquals(20.0, d, 0.5)
        assertEquals(0.3, t, 0.01)
    }

    @Test
    fun `distance to segment past its end is to the end point`() {
        val (d, t) = Geo.distanceToSegment(pt(0.0, 130.0), pt(0.0, 0.0), pt(0.0, 100.0))
        assertEquals(30.0, d, 0.5)
        assertEquals(1.0, t, 0.0)
    }

    @Test
    fun `point along a polyline`() {
        val line = lineNorth(500.0)
        val cumulative = NavTrack(line, emptyList(), emptyList(), null).cumulative
        val p = Geo.pointAlong(line, cumulative, 250.0)
        assertEquals(250.0, Geo.distanceMeters(line.first(), p), 1.0)
        // Before the start and past the end it stays on the ends.
        assertEquals(line.first(), Geo.pointAlong(line, cumulative, -10.0))
        assertEquals(line.last(), Geo.pointAlong(line, cumulative, 9_999.0))
    }

    @Test
    fun `distance text`() {
        assertEquals("340 m", Geo.formatDistance(337.0))
        assertEquals("1.2 km", Geo.formatDistance(1_234.0))
        assertEquals("15 km", Geo.formatDistance(15_400.0))
    }

    @Test
    fun `distances and speeds in miles`() {
        Geo.imperialSource = { true }
        try {
            assertEquals("500 ft", Geo.formatDistance(152.0)) // 499 ft
            assertEquals("1.2 mi", Geo.formatDistance(1_931.0))
            assertEquals("15 mi", Geo.formatDistance(24_140.0))
            assertEquals(37, Geo.speed(60))
            assertEquals("mph", Geo.speedUnit)
        } finally {
            Geo.imperialSource = { false }
        }
        assertEquals(60, Geo.speed(60))
    }

    @Test
    fun `duration text`() {
        assertEquals("1 min", Geo.durationText(10.0))
        assertEquals("21 min", Geo.durationText(21 * 60.0))
        assertEquals("1 hr 5 min", Geo.durationText(65 * 60.0))
    }
}
