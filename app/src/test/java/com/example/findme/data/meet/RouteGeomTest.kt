package com.example.findme.data.meet

import com.example.findme.TestGeo.lineNorth
import com.example.findme.TestGeo.pt
import com.example.findme.data.Geo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteGeomTest {

    private val road = RouteGeom(lineNorth(1000.0))

    @Test
    fun `length of the route`() {
        assertEquals(1000.0, road.length, 1.0)
    }

    @Test
    fun `where a point is on the route`() {
        val p = road.project(pt(5.0, 250.0))!!
        assertEquals(5.0, p.offRoute, 0.5)
        assertEquals(250.0, p.along, 2.0)
        assertEquals(2, p.segment)
    }

    @Test
    fun `a single point is not a route`() {
        assertNull(RouteGeom(listOf(pt(0.0, 0.0))).project(pt(0.0, 0.0)))
    }

    @Test
    fun `the rest of the route starts where I am`() {
        val p = road.project(pt(0.0, 250.0))!!
        val rest = road.rest(p)
        assertEquals(9, rest.size) // my point + 300, 400 ... 1000
        assertEquals(0.0, Geo.distanceMeters(rest.first(), p.onRoute), 0.01)
        assertEquals(0.0, Geo.distanceMeters(rest.last(), pt(0.0, 1000.0)), 0.01)
    }

    @Test
    fun `where two routes first meet`() {
        // Their road goes west along 600 m north and crosses mine there.
        val theirs = RouteGeom((5 downTo 0).map { pt(it * 100.0, 600.0) })
        assertEquals(600.0, road.firstMeeting(theirs)!!, 1.0)
    }

    @Test
    fun `parallel roads never meet`() {
        val theirs = RouteGeom((0..10).map { pt(200.0, it * 100.0) })
        assertNull(road.firstMeeting(theirs))
    }

    @Test
    fun `distance from a point to the route`() {
        assertEquals(40.0, road.distanceTo(pt(40.0, 500.0)), 0.5)
    }
}
