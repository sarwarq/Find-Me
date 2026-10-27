package com.example.findme.ui.nav

import com.example.findme.data.Geo
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.NavTrack
import com.example.findme.data.RouteVariant
import com.example.findme.data.TransitStep
import com.yandex.mapkit.geometry.Point
import kotlin.math.roundToInt

/** Guidance state shown in the navigation panel. */
data class NavProgress(
    val remainingMeters: Double,
    val remainingSeconds: Double,
    val fraction: Float,
    val speedKmh: Int,
    val limitKmh: Int?,
    val next: Maneuver,
    val nextInMeters: Double,
    /** Public transport: index of the current step in [RouteVariant.steps], or -1. */
    val step: Int = -1,
    /** Next stop on the way (index in the route's stops), or -1 when only the destination is left. */
    val nextStop: Int = -1,
    val toStopMeters: Double = 0.0,
    val toStopSeconds: Double = 0.0,
    /** Where I am on the route's track: segment index and how far along it (0..1). */
    val trackSegment: Int = 0,
    val trackT: Double = 0.0,
)

/** What one position fix changed in guidance. */
class NavUpdate(
    val progress: NavProgress,
    /** Direction of the road just ahead, or null when I'm off the route. */
    val routeHeading: Float?,
    /** Indexes of the stops reached with this fix, in order. */
    val reachedStops: List<Int>,
    val arrived: Boolean,
    /** Distance from the route start to where I am on it, in meters. */
    val alongMeters: Double = 0.0,
    /** Close enough to the route to count as on it. */
    val onRoute: Boolean = false,
)

/**
 * Follows me along one route variant: where I am on it, what is left, the next maneuver,
 * which stops I have passed. Plain math, no Android, so it can be unit-tested.
 */
class NavTracker(private val variant: RouteVariant) {

    private var segment = 0
    /** Stops already passed. */
    var reachedStops = 0
        private set

    /** Returns null when the variant has no usable track. */
    fun update(pos: Point, speedMps: Double?, nowMillis: Long): NavUpdate? {
        val track = variant.track ?: return null
        val pts = track.points
        if (pts.size < 2 || track.length <= 0) return null
        // Look near the last known segment only, so a route that passes the same street twice
        // doesn't make me jump ahead.
        val from = (segment - 5).coerceAtLeast(0)
        val to = (segment + 300).coerceAtMost(pts.size - 2)
        var best = segment.coerceIn(0, pts.size - 2)
        var bestDist = Double.MAX_VALUE
        var bestT = 0.0
        for (i in from..to) {
            val (d, t) = Geo.distanceToSegment(pos, pts[i], pts[i + 1])
            if (d < bestDist) {
                bestDist = d; best = i; bestT = t
            }
        }
        segment = best
        val segLen = track.cumulative[best + 1] - track.cumulative[best]
        val along = track.cumulative[best] + segLen * bestT
        val remaining = (track.length - along).coerceAtLeast(0.0)

        // On the route the map turns with the road ahead, like a navigator, even when
        // GPS gives no course (standing, slow, start of the trip).
        var routeHeading: Float? = null
        if (bestDist < ON_ROUTE_M) {
            val here = Geo.pointAlong(pts, track.cumulative, along)
            val ahead = Geo.pointAlong(pts, track.cumulative, along + HEADING_LOOKAHEAD_M)
            if (Geo.distanceMeters(here, ahead) > 2) routeHeading = Geo.bearing(here, ahead)
        }

        val next = track.maneuvers.firstOrNull { it.segment > best } ?: track.maneuvers.lastOrNull()
        val nextAt = next?.let { track.cumulative[it.segment.coerceIn(0, pts.size - 1)] } ?: track.length
        val steps = variant.steps
        val step = if (steps.isEmpty()) -1 else steps.indexOfLast { it.startSegment <= best }.coerceAtLeast(0)

        // Stops on the way.
        val stopAt = { i: Int -> track.cumulative[variant.stopSegments[i].coerceIn(0, pts.size - 1)] }
        val reached = ArrayList<Int>()
        while (reachedStops < variant.stopSegments.size && along >= stopAt(reachedStops) - STOP_REACHED_M) {
            reached += reachedStops
            reachedStops++
        }
        val nextStop = reachedStops.takeIf { it < variant.stopSegments.size } ?: -1
        val toStop = if (nextStop >= 0) (stopAt(nextStop) - along).coerceAtLeast(0.0) else 0.0

        val progress = NavProgress(
            nextStop = nextStop,
            toStopMeters = toStop,
            toStopSeconds = variant.durationSeconds * toStop / track.length,
            remainingMeters = remaining,
            remainingSeconds = if (step >= 0) transitSecondsLeft(steps, step, track, along, nowMillis)
            else variant.durationSeconds * remaining / track.length,
            step = step,
            fraction = (along / track.length).toFloat().coerceIn(0f, 1f),
            speedKmh = ((speedMps ?: 0.0) * 3.6).roundToInt(),
            limitKmh = track.speedLimits.getOrNull(best)?.let { (it * 3.6f).roundToInt() }?.takeIf { it > 0 },
            next = next?.maneuver ?: Maneuver.Straight,
            nextInMeters = (nextAt - along).coerceAtLeast(0.0),
            trackSegment = best,
            trackT = bestT,
        )
        return NavUpdate(
            progress, routeHeading, reached,
            arrived = remaining < ARRIVED_M,
            alongMeters = along,
            onRoute = bestDist < ON_ROUTE_M,
        )
    }

    /**
     * Time left on a public transport route. The timetable of the last ride is the best guess
     * (a bus does not go faster because I walk faster); without it, step times by distance.
     */
    private fun transitSecondsLeft(
        steps: List<TransitStep>, step: Int, track: NavTrack, along: Double, nowMillis: Long,
    ): Double {
        val after = { i: Int -> steps.drop(i + 1).sumOf { it.seconds } }
        val lastRide = (steps.size - 1 downTo step).firstOrNull {
            steps[it].kind == LegKind.Transport && steps[it].arrivalMillis != null
        }
        if (lastRide != null) {
            val left = (steps[lastRide].arrivalMillis!! - nowMillis) / 1000.0 + after(lastRide)
            if (left > 0) return left
        }
        val last = track.cumulative.size - 1
        val begin = track.cumulative[steps[step].startSegment.coerceIn(0, last)]
        val end = track.cumulative[(steps.getOrNull(step + 1)?.startSegment ?: last).coerceIn(0, last)]
        val partLeft = if (end > begin) ((end - along) / (end - begin)).coerceIn(0.0, 1.0) else 0.0
        return steps[step].seconds * partLeft + after(step)
    }

    private companion object {
        /** A stop counts as reached this close to it along the route. */
        const val STOP_REACHED_M = 30.0
        const val ARRIVED_M = 25.0
        /** Closer than this to the route, the map follows the road's direction. */
        const val ON_ROUTE_M = 40.0
        /** How far ahead on the route to aim the map, so it turns smoothly into bends. */
        const val HEADING_LOOKAHEAD_M = 25.0
    }
}
