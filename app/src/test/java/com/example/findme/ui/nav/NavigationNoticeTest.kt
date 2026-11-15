package com.example.findme.ui.nav

import com.example.findme.data.Maneuver
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationNoticeTest {

    private fun progress(next: Maneuver, inM: Double, street: String? = null) = NavProgress(
        remainingMeters = 12_300.0, remainingSeconds = 900.0, fraction = 0.2f, speedKmh = 50, limitKmh = null,
        next = next, nextInMeters = inM, nextStreet = street,
    )

    @Test
    fun `the next turn, the street and what is left`() {
        val n = NavigationService.noticeFor(progress(Maneuver.Right, 300.0, "Navoi Avenue"), rerouting = false)
        assertEquals("300 m · Turn right", n.title)
        assertEquals("onto Navoi Avenue · 12 km, 15 min left", n.text)
    }

    @Test
    fun `the destination and long trips`() {
        val p = progress(Maneuver.Finish, 2_400.0, "Ignored").copy(remainingSeconds = 5_000.0)
        val n = NavigationService.noticeFor(p, rerouting = false)
        assertEquals("Destination in 2.4 km", n.title)
        assertEquals("12 km, 1 h 23 min left", n.text)
    }

    @Test
    fun `going straight and rerouting`() {
        assertEquals("850 m · Go straight", NavigationService.noticeFor(progress(Maneuver.Straight, 850.0), false).title)
        assertEquals("Recalculating the route", NavigationService.noticeFor(progress(Maneuver.Left, 50.0), true).title)
    }
}
