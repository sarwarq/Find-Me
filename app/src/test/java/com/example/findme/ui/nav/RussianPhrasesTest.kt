package com.example.findme.ui.nav

import com.example.findme.data.Geo
import com.example.findme.data.LegKind
import com.example.findme.data.Maneuver
import com.example.findme.data.RoadEventKind
import com.example.findme.data.RouteEvent
import com.example.findme.data.TransitStep
import com.example.findme.data.TravelMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class RussianPhrasesTest {

    private val ru = RussianPhrases

    @After
    fun metric() {
        Geo.imperialSource = { false }
    }

    @Test
    fun `distances in words, as Organic Maps says them`() {
        assertEquals("пятьдесят метров", ru.distanceWords(40.0))
        assertEquals("сто пятьдесят метров", ru.distanceWords(160.0))
        assertEquals("четыреста метров", ru.distanceWords(390.0))
        assertEquals("километр", ru.distanceWords(980.0))
        assertEquals("полтора километра", ru.distanceWords(1_400.0))
        assertEquals("два километра", ru.distanceWords(2_100.0))
        assertEquals("два с половиной километра", ru.distanceWords(2_500.0))
        assertEquals("пять километров", ru.distanceWords(5_000.0))
        assertEquals("двадцать один километр", ru.distanceWords(21_000.0))
    }

    @Test
    fun `miles and feet`() {
        Geo.imperialSource = { true }
        assertEquals("пятьсот футов", ru.distanceWords(150.0))
        assertEquals("тысячу пятьсот футов", ru.distanceWords(460.0))
        assertEquals("одну милю", ru.distanceWords(1_620.0))
        assertEquals("полторы мили", ru.distanceWords(2_414.0))
        assertEquals("две мили", ru.distanceWords(3_219.0))
    }

    @Test
    fun `numbers and plurals`() {
        assertEquals("двести пятьдесят", ru.number(250))
        assertEquals("одиннадцать", ru.number(11))
        assertEquals("две тысячи пятьсот", ru.number(2_500))
        assertEquals("двадцать одну", ru.number(21, feminine = true))
        assertEquals("метр", ru.plural(1, "метр", "метра", "метров"))
        assertEquals("метра", ru.plural(23, "метр", "метра", "метров"))
        assertEquals("метров", ru.plural(12, "метр", "метра", "метров"))
        assertEquals("метров", ru.plural(111, "метр", "метра", "метров"))
    }

    private fun progress(next: Maneuver, inMeters: Double) = NavProgress(
        remainingMeters = 5000.0, remainingSeconds = 600.0, fraction = 0.1f, speedKmh = 0, limitKmh = null,
        next = next, nextInMeters = inMeters, nextSegment = 10,
    )

    @Test
    fun `turn prompts in Russian`() {
        val p = VoicePrompter(TravelMode.Car) { RussianPhrases }
        assertEquals("Через четыреста метров поверните направо", p.update(progress(Maneuver.Right, 390.0), emptyList(), 10.0))
        assertEquals("Поверните направо", p.update(progress(Maneuver.Right, 45.0), emptyList(), 10.0))
    }

    @Test
    fun `cameras and public transport in Russian`() {
        val p = VoicePrompter(TravelMode.Car) { RussianPhrases }
        val camera = progress(Maneuver.Straight, 900.0).copy(
            event = RouteEvent("c", 15, 0.5, RoadEventKind.SpeedCamera, 60), eventInMeters = 200.0,
        )
        assertEquals("Впереди камера контроля скорости, ограничение шестьдесят", p.event(camera, 10.0))

        val ride = TransitStep(LegKind.Transport, startSegment = 0, seconds = 600.0, lineName = "45", vehicle = "bus", toStop = "Чорсу")
        assertEquals("Садитесь на автобус 45 до остановки Чорсу", ru.takeRide(ride))
        assertEquals("Идите к пункту назначения", ru.walkTo(null))
    }

    @Test
    fun `the language can change on the way`() {
        var phrases: Phrases = EnglishPhrases
        val p = VoicePrompter(TravelMode.Car) { phrases }
        assertEquals("In 400 meters, turn right", p.update(progress(Maneuver.Right, 390.0), emptyList(), 10.0))
        phrases = RussianPhrases
        assertEquals("Поверните направо", p.update(progress(Maneuver.Right, 45.0), emptyList(), 10.0))
    }
}
