package com.example.findme.ui.nav

import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import com.example.findme.data.rideName
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What to say in guidance and when, like a car navigator: "In 400 meters, turn right" early,
 * "Turn right" at the turn, each once. For public transport, each new step once.
 * Plain logic, no Android: the speaking is done elsewhere.
 */
class VoicePrompter(private val mode: TravelMode) {

    /** The maneuver the prompts below are about (its track segment). */
    private var maneuverSegment = -1
    private var saidEarly = false
    private var saidNow = false
    private var transitStep = -1

    /** New route: everything may be said again. */
    fun reset() {
        maneuverSegment = -1
        saidEarly = false
        saidNow = false
        transitStep = -1
    }

    /** The phrase to say for this position, or null. */
    fun update(p: NavProgress, steps: List<TransitStep>, speedMps: Double?): String? {
        if (mode == TravelMode.Transit) return transit(p, steps)
        if (p.nextSegment != maneuverSegment) {
            maneuverSegment = p.nextSegment
            saidEarly = false
            saidNow = false
        }
        val action = actionText(p.next) ?: return null
        val speed = speedMps ?: 0.0
        val (earlyM, earlyS, nowM, nowS) = when (mode) {
            TravelMode.Car -> Limits(400.0, 30.0, 50.0, 5.0)
            TravelMode.Bike, TravelMode.Scooter -> Limits(150.0, 20.0, 25.0, 5.0)
            else -> Limits(80.0, 0.0, 15.0, 0.0)
        }
        val now = max(nowM, speed * nowS)
        val early = max(earlyM, speed * earlyS)
        val d = p.nextInMeters
        return when {
            // The arrival itself is announced by the guidance when it ends.
            d <= now -> if (saidNow || p.next == Maneuver.Finish) null else {
                saidNow = true
                saidEarly = true
                action.replaceFirstChar { it.uppercase() }
            }
            d <= early && !saidEarly -> {
                saidEarly = true
                "In ${distanceWords(d)}, $action"
            }
            else -> null
        }
    }

    private fun transit(p: NavProgress, steps: List<TransitStep>): String? {
        if (p.step < 0 || p.step == transitStep) return null
        transitStep = p.step
        val step = steps.getOrNull(p.step) ?: return null
        if (step.kind == LegKind.Transport) {
            val ride = rideName(step).replaceFirst("Ride", "line").replaceFirstChar { it.lowercase() }
            return "Take $ride" + step.toStop?.let { " to $it" }.orEmpty()
        }
        val stop = steps.getOrNull(p.step + 1)?.fromStop
        return if (stop != null) "Walk to the stop $stop" else "Walk to your destination"
    }

    private data class Limits(val earlyM: Double, val earlyS: Double, val nowM: Double, val nowS: Double)

    companion object {
        /** "turn right", or null for going straight on (nothing to say). */
        fun actionText(m: Maneuver): String? = when (m) {
            Maneuver.Straight -> null
            Maneuver.SlightLeft -> "bear left"
            Maneuver.SlightRight -> "bear right"
            Maneuver.Left -> "turn left"
            Maneuver.Right -> "turn right"
            Maneuver.HardLeft -> "turn sharp left"
            Maneuver.HardRight -> "turn sharp right"
            Maneuver.ForkLeft -> "keep left"
            Maneuver.ForkRight -> "keep right"
            Maneuver.UturnLeft, Maneuver.UturnRight -> "make a U-turn"
            Maneuver.Roundabout -> "enter the roundabout"
            Maneuver.Finish -> "you will arrive at your destination"
        }

        /** "300 meters", "1.5 kilometers": rounded the way people say it. */
        fun distanceWords(meters: Double): String {
            val step = if (meters < 200) 50 else 100
            val m = ((meters / step).roundToInt() * step).coerceAtLeast(50)
            if (m < 1000) return "$m meters"
            val km = (meters / 500).roundToInt() / 2.0
            return when {
                km == 1.0 -> "1 kilometer"
                km % 1.0 == 0.0 -> "${km.toInt()} kilometers"
                else -> "$km kilometers"
            }
        }
    }
}
