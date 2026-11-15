package com.example.findme.location

import com.example.findme.data.Geo
import com.yandex.mapkit.geometry.Point

/** What the sharing service does with one location fix. */
enum class ShareDecision {
    /** Nothing new for the others: don't write. */
    Skip,
    /** Send my position. */
    Write,
    /**
     * Still online, but this fix is too rough to show: send the last position again, so the
     * others don't think I'm gone and my marker doesn't jump (OwnTracks/Ferrostar drop such fixes too).
     */
    Resend,
    /** I'm at the meeting place: send it marked "arrived" and stop sharing. */
    Arrived,
    /** Sharing has been on too long: stop and disappear from the map. */
    Expired,
}

/**
 * When the sharing service writes my position: on arrival, after a real move (but not more
 * often than every [MIN_WRITE_MILLIS]), and every [IDLE_WRITE_MILLIS] while standing still,
 * so the others see I'm still online. A fix rougher than [MAX_ACCURACY] is not shown (only the
 * very first one, so the others see me at all): the last good position is sent again instead.
 * Plain math, no Android, so it can be unit-tested.
 */
object SharePolicy {
    const val MIN_WRITE_MILLIS = 5_000L
    const val IDLE_WRITE_MILLIS = 30_000L
    const val MIN_MOVE_METERS = 15.0
    /** Closer than this to the place counts as arrived... */
    const val ARRIVE_METERS = 60.0
    /** ...but only with a fix at least this accurate (a 300 m guess can't say "here"). */
    const val ARRIVE_ACCURACY = 100f
    /** Fixes rougher than this (a cell tower's guess) would make my marker jump: not sent. */
    const val MAX_ACCURACY = 100f
    const val MAX_MILLIS = 4 * 60 * 60 * 1000L
    /**
     * A position not updated for this long is gone: hidden on the others' maps and deleted by the
     * members allowed to (MeetViewModel.deleteGone). Covers a sharing phone that died or whose app
     * was killed, and the "arrived" point, which is left on purpose.
     */
    const val KEEP_MILLIS = 2 * 60 * 60 * 1000L

    /**
     * A position last updated at [updatedMillis] (null: just written) is no longer shown.
     */
    fun isGone(updatedMillis: Long?, nowMillis: Long) =
        updatedMillis != null && nowMillis - updatedMillis > KEEP_MILLIS

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
        val sinceWrite = nowMillis - lastWriteMillis
        if (lastWrite != null && accuracyM > MAX_ACCURACY) {
            return if (sinceWrite > IDLE_WRITE_MILLIS) ShareDecision.Resend else ShareDecision.Skip
        }
        val moved = lastWrite == null || Geo.distanceMeters(lastWrite, here) > MIN_MOVE_METERS
        return if ((moved && sinceWrite >= MIN_WRITE_MILLIS) || sinceWrite > IDLE_WRITE_MILLIS) {
            ShareDecision.Write
        } else {
            ShareDecision.Skip
        }
    }
}
