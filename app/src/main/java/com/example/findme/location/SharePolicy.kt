package com.example.findme.location

import com.example.findme.data.Geo
import com.yandex.mapkit.geometry.Point

/** What the sharing service does with one location fix. */
enum class ShareDecision {
    /** Nothing new for the others: don't write. */
    Skip,
    /** Send my position. */
    Write,
    /** I'm at the meeting place: send it marked "arrived" and stop sharing. */
    Arrived,
    /** Sharing has been on too long: stop and disappear from the map. */
    Expired,
}

/**
 * When the sharing service writes my position: on arrival, after a real move (but not more
 * often than every [MIN_WRITE_MILLIS]), and every [IDLE_WRITE_MILLIS] while standing still,
 * so the others see I'm still online. Plain math, no Android, so it can be unit-tested.
 */
object SharePolicy {
    const val MIN_WRITE_MILLIS = 5_000L
    const val IDLE_WRITE_MILLIS = 30_000L
    const val MIN_MOVE_METERS = 15.0
    /** Closer than this to the place counts as arrived... */
    const val ARRIVE_METERS = 60.0
    /** ...but only with a fix at least this accurate (a 300 m guess can't say "here"). */
    const val ARRIVE_ACCURACY = 100f
    const val MAX_MILLIS = 4 * 60 * 60 * 1000L

    /**
     * [accuracyM] is the fix's accuracy radius (0 when unknown, as Android reports it);
     * [lastWrite] / [lastWriteMillis] describe the last position sent (null / 0 before the first).
     */
    fun decide(
        nowMillis: Long,
        startedMillis: Long,
        here: Point,
        accuracyM: Float,
        destination: Point,
        lastWrite: Point?,
        lastWriteMillis: Long,
    ): ShareDecision {
        if (nowMillis - startedMillis > MAX_MILLIS) return ShareDecision.Expired
        if (Geo.distanceMeters(here, destination) < ARRIVE_METERS && accuracyM < ARRIVE_ACCURACY) {
            return ShareDecision.Arrived
        }
        val moved = lastWrite == null || Geo.distanceMeters(lastWrite, here) > MIN_MOVE_METERS
        val sinceWrite = nowMillis - lastWriteMillis
        return if ((moved && sinceWrite >= MIN_WRITE_MILLIS) || sinceWrite > IDLE_WRITE_MILLIS) {
            ShareDecision.Write
        } else {
            ShareDecision.Skip
        }
    }
}
