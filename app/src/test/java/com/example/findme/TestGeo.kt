package com.example.findme

import com.yandex.mapkit.geometry.Point
import com.example.findme.data.Geo
import kotlin.math.cos

/**
 * Test routes are drawn in meters east/north of a point in Tashkent,
 * so "300 m along the road" is easy to read in a test.
 */
object TestGeo {
    private const val LAT0 = 41.3111
    private const val LON0 = 69.2797
    private const val M_PER_DEG_LAT = Geo.M_PER_DEG
    private val mPerDegLon = M_PER_DEG_LAT * cos(Math.toRadians(LAT0))

    /** The point [east] meters east and [north] meters north of the origin. */
    fun pt(east: Double, north: Double) = Point(LAT0 + north / M_PER_DEG_LAT, LON0 + east / mPerDegLon)

    /** A straight line north from the origin, a point every [step] meters. */
    fun lineNorth(length: Double, step: Double = 100.0): List<Point> =
        (0..(length / step).toInt()).map { pt(0.0, it * step) }
}
