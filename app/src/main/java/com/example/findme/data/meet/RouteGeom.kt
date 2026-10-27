package com.example.findme.data.meet

import com.example.findme.data.Geo
import com.yandex.mapkit.geometry.Point

/** Where a point lies relative to a route. */
data class RouteProjection(
    /** Distance from the route in meters. */
    val offRoute: Double,
    /** Distance from the route start to the closest route point, in meters. */
    val along: Double,
    val segment: Int,
    val onRoute: Point,
)

/** A route line with distances along it, for "where is this person on their way". */
class RouteGeom(val points: List<Point>) {

    /** Distance from the start to every point, in meters. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + Geo.distanceMeters(points[i - 1], points[i])
    }

    val length: Double get() = cumulative.lastOrNull() ?: 0.0

    fun project(pos: Point): RouteProjection? {
        if (points.size < 2) return null
        var best = Double.MAX_VALUE
        var bestIndex = 0
        var bestT = 0.0
        for (i in 0 until points.size - 1) {
            val (d, t) = Geo.distanceToSegment(pos, points[i], points[i + 1])
            if (d < best) {
                best = d
                bestIndex = i
                bestT = t
            }
        }
        val a = points[bestIndex]
        val b = points[bestIndex + 1]
        val onRoute = Point(a.latitude + (b.latitude - a.latitude) * bestT, a.longitude + (b.longitude - a.longitude) * bestT)
        val along = cumulative[bestIndex] + (cumulative[bestIndex + 1] - cumulative[bestIndex]) * bestT
        return RouteProjection(best, along, bestIndex, onRoute)
    }

    /** The part still to drive: from the projected point to the end. */
    fun rest(p: RouteProjection): List<Point> = ArrayList<Point>(points.size - p.segment).apply {
        add(p.onRoute)
        addAll(points.subList(p.segment + 1, points.size))
    }

    /**
     * Distance along this route to the first place where it runs within [nearMeters] of [other],
     * looking every [stepMeters]; null when they never meet.
     */
    fun firstMeeting(other: RouteGeom, nearMeters: Double = 30.0, stepMeters: Double = 50.0): Double? {
        if (points.size < 2 || other.points.size < 2) return null
        var nextCheck = 0.0
        for (i in points.indices) {
            if (cumulative[i] < nextCheck) continue
            nextCheck = cumulative[i] + stepMeters
            if (other.distanceTo(points[i]) < nearMeters) return cumulative[i]
        }
        return null
    }

    /** Shortest distance from [pos] to this route, in meters. */
    fun distanceTo(pos: Point): Double {
        var best = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val d = Geo.distanceToSegment(pos, points[i], points[i + 1]).first
            if (d < best) best = d
        }
        return best
    }
}
