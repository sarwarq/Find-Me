package com.example.findme.ui.nav

import com.example.findme.data.TravelMode
import kotlin.math.abs

/**
 * Decides when I have left the route for real, the way Organic Maps does
 * (`RoutingSession::OnLocationPositionChanged`):
 * - every fix off the route counts, but only when I moved (the distance to the route changed):
 *   standing at a red light next to the route is not leaving it;
 * - a fix counts twice when I'm moving faster than [fastMps], once otherwise or without speed:
 *   a car leaves its route in seconds, a lost GPS course or a walk takes longer;
 * - more than [MISSED] counted means leaving: about 6 fixes on the move, 11 without speed;
 * - one fix back on the route starts the count again (one bad GPS fix was only noise);
 * - after firing, not again within [cooldownMs], in case the new route could not be built.
 */
class OffRouteDetector(
    private val fastMps: Double = -1.0,
    private val cooldownMs: Long = 15_000,
) {
    private var count = 0
    private var lastDistance = 0.0
    private var lastFired: Long? = null

    /**
     * [onRoute]: the fix was matched to the route; [distanceM]: how far from it I am.
     * Returns true once when the route should be rebuilt.
     */
    fun update(onRoute: Boolean, distanceM: Double, speedMps: Double?, nowMillis: Long): Boolean {
        if (onRoute) {
            reset()
            return false
        }
        if (abs(distanceM - lastDistance) < SAME_M) return false
        count += if (speedMps == null || speedMps < fastMps) 1 else 2
        lastDistance = distanceM
        if (count <= MISSED) return false
        lastFired?.let { if (nowMillis - it < cooldownMs) return false }
        lastFired = nowMillis
        reset()
        return true
    }

    /** A new route: count from zero. */
    fun reset() {
        count = 0
        lastDistance = 0.0
    }

    companion object {
        /** Counted fixes up to this many are still not leaving (OM `kOnRouteMissedCount`). */
        const val MISSED = 10
        /** A distance that changed less than this: I didn't move (OM: 1 cm). */
        private const val SAME_M = 0.01

        /** OM: a car counts twice from 3 km/h; walks, bikes and rides whenever the speed is known. */
        fun forMode(mode: TravelMode) = OffRouteDetector(fastMps = if (mode == TravelMode.Car) 3 / 3.6 else -1.0)
    }
}
