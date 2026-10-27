package com.example.findme.data.location

import com.example.findme.data.Geo
import com.yandex.mapkit.geometry.Point

/**
 * Where I'm going (0 = north) from a stream of position fixes: the GPS course when moving
 * fast enough, else the direction of my last move of at least [minMoveM]; while I stand
 * still it keeps the last value. Plain math, no Android, so it can be unit-tested.
 */
class HeadingEstimator(
    /** Below this speed the GPS course is noise. */
    private val minCourseSpeedMps: Double = 1.5,
    /** Without a GPS course, how far I must move before the direction is updated. */
    private val minMoveM: Double = 8.0,
) {
    var heading: Float? = null
        private set
    private var from: Point? = null

    /** Feeds one fix; returns the direction after it, or null while it is not known yet. */
    fun update(pos: Point, speedMps: Double?, course: Double?): Float? {
        if (course != null && (speedMps ?: 0.0) > minCourseSpeedMps) {
            heading = course.toFloat()
            from = pos
            return heading
        }
        val start = from
        if (start == null) {
            from = pos
        } else if (Geo.distanceMeters(start, pos) >= minMoveM) {
            heading = Geo.bearing(start, pos)
            from = pos
        }
        return heading
    }
}
