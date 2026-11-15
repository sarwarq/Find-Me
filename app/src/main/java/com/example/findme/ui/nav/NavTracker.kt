package com.example.findme.ui.nav

import com.example.findme.data.Geo
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.NavTrack
import com.example.findme.data.RoadLane
import com.example.findme.data.RouteEvent
import com.example.findme.data.RouteVariant
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import com.yandex.mapkit.geometry.Point
import kotlin.math.abs
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
    /** Track segment where [next] starts: tells one maneuver from the next. */
    val nextSegment: Int = -1,
    /** The road [next] leads onto, when the route knows it. */
    val nextStreet: String? = null,
    /** The maneuver after [next] and how far it is from here, or null after the last one. */
    val then: Maneuver? = null,
    val thenInMeters: Double = 0.0,
    /** Public transport: index of the current step in [RouteVariant.steps], or -1. */
    val step: Int = -1,
    /** Next stop on the way (index in the route's stops), or -1 when only the destination is left. */
    val nextStop: Int = -1,
    val toStopMeters: Double = 0.0,
    val toStopSeconds: Double = 0.0,
    /** Where I am on the route's track: segment index and how far along it (0..1). */
    val trackSegment: Int = 0,
    val trackT: Double = 0.0,
    /** Lanes to show: the lane sign just ahead, or null. */
    val lanes: List<RoadLane>? = null,
    /** The next camera, accident... ahead, and how far. */
    val event: RouteEvent? = null,
    val eventInMeters: Double = 0.0,
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
    /** How far I am from the route line, in meters. */
    val offRouteMeters: Double = 0.0,
)

/**
 * Follows me along one route variant: where I am on it, what is left, the next maneuver,
 * which stops I have passed. Plain math, no Android, so it can be unit-tested.
 *
 * Matching a fix to the route follows Organic Maps (`FollowedPolyline::GetBestProjection`):
 * - only forward from where I was, the next [NEAR_SEGMENTS] segments first: the usual case;
 * - only when none of them is close enough, further along the route (lost GPS in a tunnel,
 *   short segments), taking the first stretch close enough, never the one that happens to be
 *   nearest: a route that comes back along the other side of the road doesn't pull me ahead;
 *   there a part going against my course (more than [MAX_COURSE_DIFF] off) doesn't count;
 * - "close enough" is per travel mode, like OM (car 50 m, transit 40, bike 30, walk 20),
 *   widened to the GPS accuracy, and a little narrower to get back on the route than to stay
 *   on it, so the arrow doesn't flicker between the road and the raw GPS point at the edge.
 */
class NavTracker(private val variant: RouteVariant, mode: TravelMode = TravelMode.Car) {

    // Where each lane sign and road event is, in meters from the start.
    private val laneAlong = variant.track?.let { t -> t.laneSigns.map { t.alongAt(it.segment, it.t) } }.orEmpty()
    private val eventAlong = variant.track?.let { t -> t.events.map { t.alongAt(it.segment, it.t) } }.orEmpty()

    /** Closer than this to the route (before GPS accuracy), I'm on it. */
    private val matchM = matchingMeters(mode)

    /**
     * Whether the arrow is drawn on the road. Like OM, walkers and riders are shown where GPS
     * puts them: their paths cut across squares and pavements the route line doesn't follow.
     */
    val snapsArrow = mode == TravelMode.Car || mode == TravelMode.Transit

    private var segment = 0
    private var segmentT = 0.0
    /** The last fix was on the route. */
    private var matched = false
    /** Stops already passed. */
    var reachedStops = 0
        private set

    /**
     * Returns null when the variant has no usable track. [accuracyM]: GPS accuracy radius;
     * [courseDeg]: where I'm going (0 = north), both null when unknown.
     */
    fun update(
        pos: Point, speedMps: Double?, nowMillis: Long,
        accuracyM: Double? = null, courseDeg: Float? = null,
    ): NavUpdate? {
        val track = variant.track ?: return null
        val pts = track.points
        if (pts.size < 2 || track.length <= 0) return null

        val radius = maxOf(matchM, accuracyM ?: 0.0) * (if (matched) 1.0 else ENTER_SHARE)
        val course = courseDeg.takeIf { (speedMps ?: 0.0) >= COURSE_MIN_SPEED }
        val from = segment.coerceIn(0, pts.size - 2)
        val near = nearest(pts, pos, from, NEAR_SEGMENTS)
        val hit = near.takeIf { it.dist <= radius } ?: firstCloseAhead(pts, pos, from + NEAR_SEGMENTS, radius, course)
        // Off the route the progress still moves with the nearest segments ahead (the turns
        // left, the stops); the route is rebuilt soon anyway.
        val m = hit ?: near
        matched = hit != null
        // GPS jitter doesn't take me back within the segment I'm on.
        val bestT = if (m.segment == segment) maxOf(m.t, segmentT) else m.t
        segment = m.segment
        segmentT = bestT
        val best = m.segment
        val bestDist = m.dist
        val segLen = track.cumulative[best + 1] - track.cumulative[best]
        val along = track.cumulative[best] + segLen * bestT
        val remaining = (track.length - along).coerceAtLeast(0.0)

        // On the route the map turns with the road ahead, like a navigator, even when
        // GPS gives no course (standing, slow, start of the trip).
        var routeHeading: Float? = null
        if (matched) {
            val here = Geo.pointAlong(pts, track.cumulative, along)
            val ahead = Geo.pointAlong(pts, track.cumulative, along + HEADING_LOOKAHEAD_M)
            if (Geo.distanceMeters(here, ahead) > 2) routeHeading = Geo.bearing(here, ahead)
        }

        val next = track.maneuvers.firstOrNull { it.segment > best } ?: track.maneuvers.lastOrNull()
        val nextAt = next?.let { track.cumulative[it.segment.coerceIn(0, pts.size - 1)] } ?: track.length
        val after = next?.let { n -> track.maneuvers.firstOrNull { it.segment > n.segment } }
        val afterAt = after?.let { track.cumulative[it.segment.coerceIn(0, pts.size - 1)] } ?: 0.0
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

        // Share of the time already driven: a jam ahead counts for more than an empty road.
        val done = track.timeShare(along)
        val eventIndex = eventAlong.indices.firstOrNull { eventAlong[it] - along in -EVENT_BEHIND_M..EVENT_AHEAD_M }
        val progress = NavProgress(
            nextStop = nextStop,
            toStopMeters = toStop,
            toStopSeconds = variant.durationSeconds * (track.timeShare(along + toStop) - done).coerceAtLeast(0.0),
            remainingMeters = remaining,
            remainingSeconds = if (step >= 0) transitSecondsLeft(steps, step, track, along, nowMillis)
            else variant.durationSeconds * (1 - done),
            step = step,
            fraction = (along / track.length).toFloat().coerceIn(0f, 1f),
            speedKmh = ((speedMps ?: 0.0) * 3.6).roundToInt(),
            limitKmh = track.speedLimits.getOrNull(best)?.let { (it * 3.6f).roundToInt() }?.takeIf { it > 0 },
            next = next?.maneuver ?: Maneuver.Straight,
            nextInMeters = (nextAt - along).coerceAtLeast(0.0),
            nextSegment = next?.segment ?: -1,
            nextStreet = next?.street,
            then = after?.maneuver,
            thenInMeters = (afterAt - along).coerceAtLeast(0.0),
            trackSegment = best,
            trackT = bestT,
            lanes = laneAlong.indices.firstOrNull { laneAlong[it] - along in -LANES_BEHIND_M..LANES_AHEAD_M }
                ?.let { track.laneSigns[it].lanes },
            event = eventIndex?.let { track.events[it] },
            eventInMeters = eventIndex?.let { (eventAlong[it] - along).coerceAtLeast(0.0) } ?: 0.0,
        )
        return NavUpdate(
            progress, routeHeading, reached,
            arrived = remaining < ARRIVED_M,
            alongMeters = along,
            onRoute = matched,
            offRouteMeters = bestDist,
        )
    }

    /** Where a fix lands on the track: segment, how far along it (0..1), distance from it. */
    private class Hit(val segment: Int, val t: Double, val dist: Double)

    /** The nearest of [count] segments from [from] on. */
    private fun nearest(pts: List<Point>, pos: Point, from: Int, count: Int): Hit {
        var best = Hit(from, 0.0, Double.MAX_VALUE)
        for (i in from..minOf(from + count - 1, pts.size - 2)) {
            val (d, t) = Geo.distanceToSegment(pos, pts[i], pts[i + 1])
            if (d < best.dist) best = Hit(i, t, d)
        }
        return best
    }

    /**
     * The first stretch of the route from segment [from] on that is within [radius] of me,
     * and the nearest point of it; null when there is none. With a [course], segments
     * going the other way are skipped.
     */
    private fun firstCloseAhead(pts: List<Point>, pos: Point, from: Int, radius: Double, course: Float?): Hit? {
        var best: Hit? = null
        for (i in from..pts.size - 2) {
            val (d, t) = Geo.distanceToSegment(pos, pts[i], pts[i + 1])
            val close = d <= radius && (course == null || !against(course, pts[i], pts[i + 1]))
            if (!close) {
                if (best != null) break // the first close stretch has ended
                continue
            }
            if (best == null || d < best.dist) best = Hit(i, t, d)
        }
        return best
    }

    /** The segment [a]→[b] goes against [course]. A point-like segment goes nowhere. */
    private fun against(course: Float, a: Point, b: Point): Boolean {
        if (Geo.distanceMeters(a, b) < 0.1) return false
        val diff = abs(((Geo.bearing(a, b) - course) % 360 + 540) % 360 - 180)
        return diff > MAX_COURSE_DIFF
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
        /** Closer than this to the route, I'm on it; the values are Organic Maps' ones. */
        fun matchingMeters(mode: TravelMode) = when (mode) {
            TravelMode.Car -> 50.0
            TravelMode.Transit -> 40.0
            TravelMode.Bike, TravelMode.Scooter -> 30.0
            TravelMode.Walk -> 20.0
        }
        /** Getting back on the route takes this share of the matching distance. */
        const val ENTER_SHARE = 0.8
        /** Segments ahead looked at first (OM: the current one and the next two). */
        const val NEAR_SEGMENTS = 3
        /** Slower than this, the GPS course is not trusted (m/s, ~11 km/h). */
        const val COURSE_MIN_SPEED = 3.0
        /** A segment further than this off my course goes the other way, degrees. */
        const val MAX_COURSE_DIFF = 90f
        /** A stop counts as reached this close to it along the route. */
        const val STOP_REACHED_M = 30.0
        const val ARRIVED_M = 25.0
        /** How far ahead on the route to aim the map, so it turns smoothly into bends. */
        const val HEADING_LOOKAHEAD_M = 25.0
        // Lanes show from this far before their sign until just past it.
        const val LANES_AHEAD_M = 300.0
        const val LANES_BEHIND_M = 10.0
        // A camera or accident shows from this far ahead until just past it.
        const val EVENT_AHEAD_M = 800.0
        const val EVENT_BEHIND_M = 10.0
    }
}
