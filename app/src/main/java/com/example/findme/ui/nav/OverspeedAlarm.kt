package com.example.findme.ui.nav

/**
 * When to beep for speeding: more than [overKmh] above the limit. It beeps on crossing that
 * line and again every [repeatMs] while still over; it counts as slowed down only below
 * half that margin, so hovering around the line doesn't beep on and off.
 */
class OverspeedAlarm(
    private val overKmh: Int = 10,
    private val repeatMs: Long = 20_000,
) {
    private var over = false
    private var lastBeep = 0L

    /** True when a beep is due now. */
    fun update(speedKmh: Int, limitKmh: Int?, nowMillis: Long, overKmh: Int = this.overKmh): Boolean {
        if (limitKmh == null || limitKmh <= 0) {
            over = false
            return false
        }
        val wasOver = over
        over = speedKmh > limitKmh + if (wasOver) overKmh / 2 else overKmh
        if (!over) return false
        if (wasOver && nowMillis - lastBeep < repeatMs) return false
        lastBeep = nowMillis
        return true
    }
}
