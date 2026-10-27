package com.example.findme.ui.nav

/**
 * Decides when I have left the route for real: farther than [thresholdM] from it for
 * [holdMs] in a row (one bad GPS fix is not enough), and not again within [cooldownMs]
 * of the last time (the new route needs a moment to arrive and settle).
 */
class OffRouteDetector(
    private val thresholdM: Double = 50.0,
    private val holdMs: Long = 5_000,
    private val cooldownMs: Long = 15_000,
) {
    private var offSince: Long? = null
    private var lastFired: Long? = null

    /** Returns true once when the route should be rebuilt. */
    fun update(distanceM: Double, nowMillis: Long): Boolean {
        if (distanceM <= thresholdM) {
            offSince = null
            return false
        }
        val since = offSince ?: nowMillis.also { offSince = it }
        if (nowMillis - since < holdMs) return false
        lastFired?.let { if (nowMillis - it < cooldownMs) return false }
        lastFired = nowMillis
        offSince = null
        return true
    }

    /** A new route: count from zero. */
    fun reset() {
        offSince = null
    }
}
