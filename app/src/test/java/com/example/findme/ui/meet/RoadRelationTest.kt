package com.example.findme.ui.meet

import com.example.findme.TestGeo.lineNorth
import com.example.findme.TestGeo.pt
import com.example.findme.data.meet.RouteGeom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadRelationTest {

    private val myRoad = RouteGeom(lineNorth(1000.0))
    /** Their road goes west along 600 m north and joins mine there. */
    private val theirRoad = RouteGeom((5 downTo 0).map { pt(it * 100.0, 600.0) })
    private val join: (RouteGeom, RouteGeom, Double) -> Double? = { a, b, from -> a.firstMeeting(b, from) }

    private fun meters(r: RoadRelation?) = when (r) {
        is RoadRelation.Ahead -> r.meters
        is RoadRelation.Behind -> r.meters
        is RoadRelation.JoinsIn -> r.meters
        else -> error("no distance in $r")
    }

    @Test
    fun `ahead of me on my road`() {
        val r = roadRelation(pt(0.0, 200.0), pt(3.0, 500.0), myRoad, null, join)
        assertTrue(r is RoadRelation.Ahead)
        assertEquals(300.0, meters(r), 3.0)
    }

    @Test
    fun `behind me on my road`() {
        val r = roadRelation(pt(0.0, 500.0), pt(0.0, 200.0), myRoad, null, join)
        assertTrue(r is RoadRelation.Behind)
        assertEquals(300.0, meters(r), 3.0)
    }

    @Test
    fun `closer than 50 m is together`() {
        assertEquals(RoadRelation.Together, roadRelation(pt(0.0, 200.0), pt(0.0, 230.0), myRoad, null, join))
    }

    @Test
    fun `on their road when I have no route`() {
        val r = roadRelation(pt(100.0, 600.0), pt(400.0, 600.0), null, theirRoad, join)
        assertTrue(r is RoadRelation.Behind) // their road goes west: 400 is before 100
        assertEquals(300.0, meters(r), 3.0)
    }

    @Test
    fun `different roads that meet ahead`() {
        // I'm 100 m up my road, they are on theirs; the roads meet 600 m up mine.
        val r = roadRelation(pt(0.0, 100.0), pt(450.0, 600.0), myRoad, theirRoad, join)
        assertTrue("got $r", r is RoadRelation.JoinsIn)
        assertEquals(500.0, meters(r), 5.0)
    }

    @Test
    fun `nothing to say when one of us is off the road`() {
        assertNull(roadRelation(pt(0.0, 200.0), pt(300.0, 400.0), myRoad, null, join))
    }

    @Test
    fun `where roads meet is looked for from where I am`() {
        // Their road crosses mine at 600 m, loops west and crosses it again at 900 m.
        val loop = RouteGeom(listOf(pt(500.0, 600.0), pt(-200.0, 600.0), pt(-200.0, 900.0), pt(300.0, 900.0)))
        // I'm at 800 m, past the first crossing: we meet at the second one, 100 m ahead.
        val r = roadRelation(pt(0.0, 800.0), pt(450.0, 600.0), myRoad, loop, join)
        assertTrue(r is RoadRelation.JoinsIn)
        assertEquals(100.0, meters(r), 50.0)
    }

    @Test
    fun `texts`() {
        assertEquals("1.2 km ahead of you", RoadRelation.Ahead(1_200.0).text())
        assertEquals("340 m behind", RoadRelation.Behind(340.0).short())
        assertEquals("joins your road in 500 m", RoadRelation.JoinsIn(500.0).text())
    }
}
