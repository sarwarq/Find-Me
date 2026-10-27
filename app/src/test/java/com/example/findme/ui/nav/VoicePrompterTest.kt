package com.example.findme.ui.nav

import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoicePrompterTest {

    private fun progress(next: Maneuver, inMeters: Double, segment: Int = 10, step: Int = -1) = NavProgress(
        remainingMeters = 5000.0, remainingSeconds = 600.0, fraction = 0.1f, speedKmh = 0, limitKmh = null,
        next = next, nextInMeters = inMeters, nextSegment = segment, step = step,
    )

    private fun VoicePrompter.at(next: Maneuver, m: Double, segment: Int = 10, speed: Double? = 10.0) =
        update(progress(next, m, segment), emptyList(), speed)

    @Test
    fun `car - early prompt then at the turn, each once`() {
        val p = VoicePrompter(TravelMode.Car)
        assertNull(p.at(Maneuver.Right, 900.0))
        assertEquals("In 400 meters, turn right", p.at(Maneuver.Right, 390.0))
        assertNull(p.at(Maneuver.Right, 300.0))
        assertNull(p.at(Maneuver.Right, 100.0))
        assertEquals("Turn right", p.at(Maneuver.Right, 45.0))
        assertNull(p.at(Maneuver.Right, 20.0))
    }

    @Test
    fun `faster means earlier`() {
        val p = VoicePrompter(TravelMode.Car)
        // 25 m/s (90 km/h): 30 s ahead is 750 m.
        assertEquals("In 700 meters, keep left", p.at(Maneuver.ForkLeft, 700.0, speed = 25.0))
        assertEquals("Keep left", p.at(Maneuver.ForkLeft, 120.0, speed = 25.0)) // 5 s = 125 m
    }

    @Test
    fun `the next maneuver is announced again`() {
        val p = VoicePrompter(TravelMode.Car)
        assertEquals("Turn left", p.at(Maneuver.Left, 30.0, segment = 10))
        assertEquals("In 300 meters, turn right", p.at(Maneuver.Right, 300.0, segment = 20))
    }

    @Test
    fun `nothing to say for going straight, the finish is said only early`() {
        val p = VoicePrompter(TravelMode.Car)
        assertNull(p.at(Maneuver.Straight, 100.0))
        assertNull(p.at(Maneuver.Straight, 10.0))
        val f = VoicePrompter(TravelMode.Car)
        assertEquals("In 300 meters, you will arrive at your destination", f.at(Maneuver.Finish, 300.0))
        assertNull(f.at(Maneuver.Finish, 20.0))
    }

    @Test
    fun `walking - short distances`() {
        val p = VoicePrompter(TravelMode.Walk)
        assertNull(p.at(Maneuver.Left, 150.0, speed = 1.4))
        assertEquals("In 50 meters, turn left", p.at(Maneuver.Left, 60.0, speed = 1.4))
        assertEquals("Turn left", p.at(Maneuver.Left, 12.0, speed = 1.4))
    }

    @Test
    fun `starting right at a turn says only the turn`() {
        val p = VoicePrompter(TravelMode.Car)
        assertEquals("Turn right", p.at(Maneuver.Right, 20.0))
        assertNull(p.at(Maneuver.Right, 10.0))
    }

    @Test
    fun `reset says it again on a new route`() {
        val p = VoicePrompter(TravelMode.Car)
        assertEquals("Turn right", p.at(Maneuver.Right, 20.0))
        p.reset()
        assertEquals("Turn right", p.at(Maneuver.Right, 20.0))
    }

    @Test
    fun `transit - each new step once`() {
        val steps = listOf(
            TransitStep(LegKind.Walk, 0, 300.0),
            TransitStep(LegKind.Transport, 5, 900.0, lineName = "51", vehicle = "bus", fromStop = "Chorsu", toStop = "Oybek"),
            TransitStep(LegKind.Walk, 20, 200.0),
        )
        val p = VoicePrompter(TravelMode.Transit)
        val at = { step: Int -> p.update(progress(Maneuver.Straight, 0.0, step = step), steps, 1.0) }
        assertEquals("Walk to the stop Chorsu", at(0))
        assertNull(at(0))
        assertEquals("Take bus 51 to Oybek", at(1))
        assertEquals("Walk to your destination", at(2))
    }

    @Test
    fun `distance words`() {
        assertEquals("50 meters", VoicePrompter.distanceWords(30.0))
        assertEquals("150 meters", VoicePrompter.distanceWords(140.0))
        assertEquals("400 meters", VoicePrompter.distanceWords(380.0))
        assertEquals("1 kilometer", VoicePrompter.distanceWords(980.0))
        assertEquals("1.5 kilometers", VoicePrompter.distanceWords(1450.0))
        assertEquals("2 kilometers", VoicePrompter.distanceWords(2100.0))
    }
}
