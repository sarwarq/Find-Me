package com.example.findme.data.location

import com.example.findme.data.Geo
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.location.LocationStatus
import com.yandex.mapkit.location.Purpose
import com.yandex.mapkit.location.SubscriptionSettings
import com.yandex.mapkit.location.UseInBackground
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * One position of the phone. [heading] is where I'm going (0 = north): the GPS course when
 * moving, else the direction of my last move; it keeps the last value while I stand still.
 */
class LocationFix(val point: Point, val speedMps: Double?, val heading: Float?)

/**
 * The phone's position for the whole app while it is open (map, meet-ups), from MapKit.
 * The background sharing service has its own provider, since it runs with the app closed.
 */
object LocationRepository {
    /** Below this speed the GPS course is noise. */
    private const val MIN_COURSE_SPEED_MPS = 1.5
    /** Without a GPS course, how far I must move before the direction is updated. */
    private const val MIN_MOVE_M = 8.0

    private var manager: LocationManager? = null
    private val _fix = MutableStateFlow<LocationFix?>(null)
    val fix: StateFlow<LocationFix?> = _fix.asStateFlow()

    private var heading: Float? = null
    private var headingFrom: Point? = null

    // MapKit keeps only a weak reference to the listener.
    private val listener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            val pos = location.position
            updateHeading(pos, location.speed, location.heading)
            _fix.value = LocationFix(pos, location.speed, heading)
        }

        override fun onLocationStatusUpdated(status: LocationStatus) = Unit
    }

    /** Starts the updates; call once location permission is granted. MapKit must be initialized. */
    fun start() {
        if (manager != null) return
        manager = MapKitFactory.getInstance().createLocationManager().also {
            it.subscribeForLocationUpdates(
                SubscriptionSettings(UseInBackground.DISALLOW, Purpose.GENERAL),
                WeakReference(listener),
            )
        }
    }

    fun stop() {
        manager?.unsubscribe(WeakReference(listener))
        manager = null
    }

    private fun updateHeading(pos: Point, speed: Double?, course: Double?) {
        if (course != null && (speed ?: 0.0) > MIN_COURSE_SPEED_MPS) {
            heading = course.toFloat()
            headingFrom = pos
            return
        }
        val from = headingFrom
        if (from == null) {
            headingFrom = pos
        } else if (Geo.distanceMeters(from, pos) >= MIN_MOVE_M) {
            heading = Geo.bearing(from, pos)
            headingFrom = pos
        }
    }
}
