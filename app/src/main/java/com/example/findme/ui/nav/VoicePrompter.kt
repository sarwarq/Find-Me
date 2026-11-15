package com.example.findme.ui.nav

import com.example.findme.data.Geo
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.RoadEventKind
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import kotlin.math.max

/**
 * What to say in guidance and when, like a car navigator: "In 400 meters, turn right" early,
 * "Turn right" at the turn, each once. For public transport, each new step once.
 * Plain logic, no Android: the speaking is done elsewhere.
 */
class VoicePrompter(
    private val mode: TravelMode,
    /** The words, read for every phrase: the Voice language setting may change on the way. */
    private val phrases: () -> Phrases = { EnglishPhrases },
) {

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
        val say = phrases()
        val action = say.action(p.next) ?: return null
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
                say.inDistance(d, action)
            }
            else -> null
        }
    }

    /** Road events already announced; kept after a reroute, the camera is still the same one. */
    private val saidEvents = HashSet<String>()

    /** "Speed camera ahead, limit 60", once per event, some 20 s before it (300 m at least). */
    fun event(p: NavProgress, speedMps: Double?): String? {
        val e = p.event ?: return null
        if (e.id in saidEvents) return null
        if (p.eventInMeters > max(EVENT_EARLY_M, (speedMps ?: 0.0) * EVENT_EARLY_S)) return null
        saidEvents += e.id
        val say = phrases()
        return say.event(e.kind) + if (e.kind == RoadEventKind.SpeedCamera && e.limitKmh != null) say.limit(Geo.speed(e.limitKmh)) else ""
    }

    private fun transit(p: NavProgress, steps: List<TransitStep>): String? {
        if (p.step < 0 || p.step == transitStep) return null
        transitStep = p.step
        val step = steps.getOrNull(p.step) ?: return null
        if (step.kind == LegKind.Transport) return phrases().takeRide(step)
        return phrases().walkTo(steps.getOrNull(p.step + 1)?.fromStop)
    }

    private data class Limits(val earlyM: Double, val earlyS: Double, val nowM: Double, val nowS: Double)

    companion object {
        private const val EVENT_EARLY_M = 300.0
        private const val EVENT_EARLY_S = 20.0

        /** English, for the screens and the notification (the app's own language). */
        fun eventText(kind: RoadEventKind): String = EnglishPhrases.event(kind)

        /** "turn right", or null for going straight on (nothing to say). */
        fun actionText(m: Maneuver): String? = EnglishPhrases.action(m)

        /** "300 meters", "1.5 kilometers": rounded the way people say it. */
        fun distanceWords(meters: Double): String = EnglishPhrases.distanceWords(meters)
    }
}
