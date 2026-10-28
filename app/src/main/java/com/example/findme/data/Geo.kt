package com.example.findme.data

import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Point
import java.util.Calendar
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    /** One Earth model for the whole app: a sphere, so all distances agree with each other. */
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Meters in one degree of latitude on that sphere (~111 195 m). */
    const val M_PER_DEG = EARTH_RADIUS_M * Math.PI / 180.0

    fun distanceMeters(a: Point, b: Point): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Compass direction from [a] to [b] in degrees, 0 = north, 90 = east. */
    fun bearing(a: Point, b: Point): Float {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val deg = Math.toDegrees(kotlin.math.atan2(y, x))
        return ((deg + 360) % 360).toFloat()
    }

    /** The point [d] meters from the start of a polyline with [cumulative] distances. */
    fun pointAlong(points: List<Point>, cumulative: DoubleArray, d: Double): Point {
        if (d <= 0 || points.size < 2) return points.first()
        if (d >= cumulative.last()) return points.last()
        var i = java.util.Arrays.binarySearch(cumulative, d)
        if (i >= 0) return points[i]
        i = -i - 2 // segment i..i+1 holds d
        val len = cumulative[i + 1] - cumulative[i]
        val t = if (len > 0) (d - cumulative[i]) / len else 0.0
        val a = points[i]
        val b = points[i + 1]
        return Point(a.latitude + (b.latitude - a.latitude) * t, a.longitude + (b.longitude - a.longitude) * t)
    }

    /** Miles instead of kilometers (the Units setting); read on every call, so screens follow it. */
    var imperialSource: () -> Boolean = { false }
    val imperial: Boolean get() = imperialSource()

    /** "850 m", "2.4 km", "12 km"; in miles "500 ft", "1.5 mi", "12 mi". */
    fun formatDistance(meters: Double): String {
        if (imperial) {
            val miles = meters / METERS_PER_MILE
            return when {
                miles < 0.1 -> "${((meters * FEET_PER_METER) / 50).roundToInt() * 50} ft"
                miles < 10 -> String.format(Locale.US, "%.1f mi", miles)
                else -> "${miles.roundToInt()} mi"
            }
        }
        return when {
            meters < 1000 -> "${(meters / 10).roundToInt() * 10} m"
            meters < 10_000 -> String.format(Locale.US, "%.1f km", meters / 1000)
            else -> "${(meters / 1000).roundToInt()} km"
        }
    }

    /** A speed in km/h shown in the chosen units: km/h or mph. */
    fun speed(kmh: Int): Int = if (imperial) (kmh / KM_PER_MILE).roundToInt() else kmh

    val speedUnit: String get() = if (imperial) "mph" else "km/h"

    const val METERS_PER_MILE = 1609.344
    const val KM_PER_MILE = 1.609344
    const val FEET_PER_METER = 3.28084

    fun distanceText(from: Point?, to: Point): String? =
        from?.let { formatDistance(distanceMeters(it, to)) }

    fun boundsOf(points: List<Point>): BoundingBox? {
        if (points.isEmpty()) return null
        var minLat = 90.0
        var maxLat = -90.0
        var minLon = 180.0
        var maxLon = -180.0
        points.forEach {
            minLat = min(minLat, it.latitude); maxLat = max(maxLat, it.latitude)
            minLon = min(minLon, it.longitude); maxLon = max(maxLon, it.longitude)
        }
        return BoundingBox(Point(minLat, minLon), Point(maxLat, maxLon))
    }

    /** A box around [p] roughly [radiusM] meters in each direction. */
    fun boxAround(p: Point, radiusM: Double): BoundingBox {
        val dLat = radiusM / M_PER_DEG
        val dLon = radiusM / (M_PER_DEG * cos(Math.toRadians(p.latitude)).coerceAtLeast(0.01))
        return BoundingBox(
            Point(p.latitude - dLat, p.longitude - dLon),
            Point(p.latitude + dLat, p.longitude + dLon),
        )
    }

    /** Arrival time ("14:35") for a trip that starts now. */
    fun arrivalText(durationSeconds: Double): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.SECOND, durationSeconds.roundToInt())
        return String.format(Locale.US, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    /**
     * Distance from [p] to the segment [a]–[b] in meters, and where the closest point lies
     * on the segment (0 at [a], 1 at [b]). Uses a flat projection, fine for short segments.
     */
    fun distanceToSegment(p: Point, a: Point, b: Point): Pair<Double, Double> {
        val kx = M_PER_DEG * cos(Math.toRadians(p.latitude))
        val ky = M_PER_DEG
        val ax = (a.longitude - p.longitude) * kx
        val ay = (a.latitude - p.latitude) * ky
        val bx = (b.longitude - p.longitude) * kx
        val by = (b.latitude - p.latitude) * ky
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + dx * t
        val cy = ay + dy * t
        return sqrt(cx * cx + cy * cy) to t
    }

    /** "21 min", "1 hr 5 min", the way Yandex Maps writes durations in English. */
    fun durationText(seconds: Double): String {
        val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
        return if (minutes < 60) "$minutes min" else {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0) "$h hr" else "$h hr $m min"
        }
    }

    /** Distance for the navigation panel: "850 m", "10 km" (the same as [formatDistance]). */
    fun shortDistance(meters: Double): String = formatDistance(meters)

    fun coordinatesText(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.6f, %.6f", lat, lon)
}
