package com.example.findme.ui.nav

import com.example.findme.data.Geo
import com.example.findme.data.NavTrack
import com.yandex.mapkit.geometry.Point
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Where to draw the guidance arrow in one frame. [bearing]: 0 = north. */
class ArrowPose(val point: Point, val bearing: Float)

/**
 * Moves the guidance arrow every frame from GPS fixes that come about once a second.
 *
 * On the route the arrow travels along the road, by the distance driven `s`:
 * - between fixes `s` is predicted with uniformly accelerated motion,
 *   `s(t) = s0 + v·t + a·t²/2`, with the speed `v` from GPS (or Δs/Δt) and the acceleration `a`
 *   from the last two speeds, for at most [HORIZON_S] seconds;
 * - a new fix never makes the arrow jump: the shown position catches up with the prediction
 *   by moving at `v(t) + e / CATCH_UP_S`, where `e` is how far behind (or ahead) it is, so the
 *   error fades in about [CATCH_UP_S] seconds; it never moves backwards;
 * - the point and the direction come from the road itself, so the arrow follows its curves.
 * Off the route the same is done in a plane, along the GPS direction.
 *
 * Plain math, no Android: the time is passed in, so it can be unit-tested.
 *
 * The same motion glides meet-up members' pins between their much rarer updates, with a longer
 * [horizonS] and [maxGapS].
 */
class ArrowMotion(
    private val track: NavTrack?,
    /** Predict at most this far after a fix. */
    private val horizonS: Double = HORIZON_S,
    /** Farther than this from the prediction, the arrow is moved at once. */
    private val snapM: Double = SNAP_M,
    /** Two fixes further apart than this don't give a speed. */
    private val maxGapS: Double = 5.0,
) {

    // ---------- The last fix ----------
    private var fixMs = 0L
    private var fixPoint: Point? = null
    private var fixAlong: Double? = null
    private var fixHeading: Float? = null
    private var v = 0.0
    private var a = 0.0
    /** [v] was measured (GPS speed or two fixes), not just assumed. */
    private var vKnown = false

    // ---------- What is shown ----------
    private var shownAlong: Double? = null
    /** Off the route: the shown position in meters east/north of [fixPoint]. */
    private var shownE = 0.0
    private var shownN = 0.0
    private var shownOff = false
    private var bearing = 0f
    private var frameMs = 0L

    /** How far along the route the arrow is drawn now; null off the route. */
    val shownAlongMeters: Double? get() = if (shownOff) null else shownAlong

    /**
     * A new GPS fix. [along] is the distance from the route start, null when I'm off the route;
     * [speedMps] / [heading] are null when GPS doesn't give them.
     */
    fun onFix(nowMs: Long, point: Point, along: Double?, speedMps: Double?, heading: Float?) {
        val prevPoint = fixPoint
        val prevAlong = fixAlong
        val prevV = v
        val prevKnown = vKnown
        val dt = (nowMs - fixMs) / 1000.0
        val usable = prevPoint != null && dt in 0.2..maxGapS
        val onRoute = along != null && track != null

        // Speed: GPS, else distance over time between the last two fixes.
        val measured = speedMps ?: if (usable) {
            if (onRoute && prevAlong != null) abs(along!! - prevAlong) / dt
            else Geo.distanceMeters(prevPoint!!, point) / dt
        } else 0.0
        v = measured.coerceIn(0.0, MAX_SPEED)
        vKnown = speedMps != null || usable
        // Acceleration only from two known speeds: an unknown one is not "standing".
        a = if (usable && prevKnown && vKnown) ((v - prevV) / dt).coerceIn(-MAX_ACCEL, MAX_ACCEL) else 0.0
        if (v < STOPPED_SPEED) {
            v = 0.0
            a = 0.0
        }

        if (!onRoute) {
            if (shownOff && prevPoint != null) {
                // The plane is centered on the newest fix: re-center what is shown.
                val (de, dn) = toMeters(prevPoint, point)
                shownE += de
                shownN += dn
            } else {
                // Leaving the route: the plane starts where the arrow is now.
                val now = shownAlong?.let { s -> track?.let { Geo.pointAlong(it.points, it.cumulative, s) } }
                val (e, n) = if (now != null) toMeters(now, point) else 0.0 to 0.0
                shownE = e
                shownN = n
            }
        } else if (shownOff) {
            shownAlong = null // back on the route: start from the fix
        }

        fixMs = nowMs
        fixPoint = point
        fixAlong = if (onRoute) along else null
        fixHeading = heading ?: fixHeading
        shownOff = !onRoute
    }

    /** Long after the last fix nothing moves any more: frames can stop until the next one. */
    fun settled(nowMs: Long): Boolean =
        fixPoint == null || nowMs - fixMs > ((horizonS + 4 * CATCH_UP_S) * 1000).toLong()

    /** The arrow for the frame at [nowMs]; null before the first fix. */
    fun pose(nowMs: Long): ArrowPose? {
        val fix = fixPoint ?: return null
        val dt = if (frameMs == 0L) 0.0 else ((nowMs - frameMs) / 1000.0).coerceIn(0.0, MAX_FRAME_S)
        frameMs = nowMs
        val sinceFix = (nowMs - fixMs) / 1000.0
        val (dist, predicted) = predict(sinceFix.coerceIn(0.0, horizonS))
        // Past the horizon the prediction stands still, so must the arrow.
        val speed = if (sinceFix > horizonS) 0.0 else predicted

        val along = fixAlong
        val tr = track
        if (along != null && tr != null) {
            val target = (along + dist).coerceAtMost(tr.length)
            val shown = shownAlong
            val next = if (shown == null || abs(target - shown) > snapM) target
            else shown + (speed + (target - shown) / CATCH_UP_S).coerceAtLeast(0.0) * dt
            val s = next.coerceIn(0.0, tr.length)
            shownAlong = s
            val p = Geo.pointAlong(tr.points, tr.cumulative, s)
            val ahead = Geo.pointAlong(tr.points, tr.cumulative, s + LOOKAHEAD_M)
            if (Geo.distanceMeters(p, ahead) > 1) bearing = Geo.bearing(p, ahead)
            return ArrowPose(p, bearing)
        }

        // Off the route: the same in a plane, along the GPS direction.
        val dir = fixHeading
        val rad = Math.toRadians((dir ?: 0f).toDouble())
        val ue = if (dir == null) 0.0 else sin(rad)
        val un = if (dir == null) 0.0 else cos(rad)
        val te = ue * dist
        val tn = un * dist
        val ee = te - shownE
        val en = tn - shownN
        if (hypot(ee, en) > snapM) {
            shownE = te
            shownN = tn
        } else {
            shownE += (ue * speed + ee / CATCH_UP_S) * dt
            shownN += (un * speed + en / CATCH_UP_S) * dt
        }
        dir?.let { bearing = it }
        return ArrowPose(fromMeters(fix, shownE, shownN), bearing)
    }

    /** Distance and speed [t] seconds after the fix; a car that brakes stops, it doesn't reverse. */
    private fun predict(t: Double): Pair<Double, Double> {
        if (a < 0 && v + a * t < 0) {
            val stopT = -v / a
            return (v * stopT + a * stopT * stopT / 2) to 0.0
        }
        return (v * t + a * t * t / 2) to (v + a * t)
    }

    /** [p] in meters east/north of [origin] (flat, fine for a few hundred meters). */
    private fun toMeters(p: Point, origin: Point): Pair<Double, Double> {
        val kx = Geo.M_PER_DEG * cos(Math.toRadians(origin.latitude))
        return (p.longitude - origin.longitude) * kx to (p.latitude - origin.latitude) * Geo.M_PER_DEG
    }

    private fun fromMeters(origin: Point, e: Double, n: Double): Point {
        val kx = Geo.M_PER_DEG * cos(Math.toRadians(origin.latitude))
        return Point(origin.latitude + n / Geo.M_PER_DEG, origin.longitude + e / kx)
    }

    companion object {
        /** Predict at most this far after a fix (GPS lost: the arrow stops, it doesn't wander). */
        const val HORIZON_S = 2.5
        /** A prediction error fades in about this time. */
        const val CATCH_UP_S = 0.8
        /** Farther than this from the prediction, the arrow is moved at once. */
        const val SNAP_M = 80.0
        /** The road this far ahead sets the direction, so it turns smoothly into bends. */
        const val LOOKAHEAD_M = 15.0
        private const val MAX_ACCEL = 4.0
        private const val MAX_SPEED = 70.0
        private const val STOPPED_SPEED = 0.5
        private const val MAX_FRAME_S = 0.1
    }
}
