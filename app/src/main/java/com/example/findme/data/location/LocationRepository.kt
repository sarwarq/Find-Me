package com.example.findme.data.location

import com.example.findme.data.TravelMode
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.location.Location
import com.yandex.mapkit.location.LocationListener
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.location.LocationManager
import com.yandex.mapkit.location.LocationSettingsFactory
import com.yandex.mapkit.location.LocationSimulator
import com.yandex.mapkit.location.LocationSimulatorListener
import com.yandex.mapkit.location.LocationStatus
import com.yandex.mapkit.location.SimulationSettings
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
    private var manager: LocationManager? = null
    private val _fix = MutableStateFlow<LocationFix?>(null)
    val fix: StateFlow<LocationFix?> = _fix.asStateFlow()

    private val headings = HeadingEstimator()

    // MapKit keeps only a weak reference to the listener.
    private val listener = object : LocationListener {
        override fun onLocationUpdated(location: Location) {
            val pos = location.position
            val heading = headings.update(pos, location.speed, location.heading)
            _fix.value = LocationFix(pos, location.speed, heading)
        }

        override fun onLocationStatusUpdated(status: LocationStatus) = Unit
    }

    /** What the fixes are for: MapKit filters and snaps them differently for a car or a walk. */
    private var purpose = Purpose.GENERAL

    private fun settings() = SubscriptionSettings(UseInBackground.DISALLOW, purpose)

    /** Starts the updates; call once location permission is granted. MapKit must be initialized. */
    fun start() {
        if (manager != null) return
        manager = MapKitFactory.getInstance().createLocationManager().also {
            it.subscribeForLocationUpdates(settings(), WeakReference(listener))
        }
    }

    /** Guidance in [mode] (null when it ends): asks MapKit for fixes tuned to that travel. */
    fun setTravelMode(mode: TravelMode?) {
        val next = when (mode) {
            TravelMode.Car -> Purpose.AUTOMOTIVE_NAVIGATION
            TravelMode.Walk -> Purpose.PEDESTRIAN_NAVIGATION
            TravelMode.Bike -> Purpose.BICYCLE_NAVIGATION
            TravelMode.Scooter -> Purpose.SCOOTER_NAVIGATION
            TravelMode.Transit, null -> Purpose.GENERAL // a bus ride is neither a walk nor a drive
        }
        if (next == purpose) return
        purpose = next
        val source = simulator ?: manager ?: return
        source.unsubscribe(WeakReference(listener))
        source.subscribeForLocationUpdates(settings(), WeakReference(listener))
    }

    fun stop() {
        stopSimulation()
        manager?.unsubscribe(WeakReference(listener))
        manager = null
    }

    // ---------- Test drives (debug builds only) ----------

    private var simulator: LocationSimulator? = null
    private val simulatorListener = object : LocationSimulatorListener {
        override fun onSimulationFinished() = stopSimulation()
    }

    /**
     * Drives along [route] at [speedMps] instead of using GPS, so guidance can be tried at
     * the desk. With [gpsHeading] false the fixes carry no course, like a phone whose GPS
     * gives none (the case where the map used not to turn).
     */
    fun simulate(route: List<Point>, speedMps: Double, gpsHeading: Boolean) {
        stopSimulation()
        val kit = MapKitFactory.getInstance()
        val sim = kit.createLocationSimulator()
        manager?.unsubscribe(WeakReference(listener))
        kit.setLocationManager(sim) // the arrow on the map follows the simulator too
        sim.subscribeForSimulatorEvents(WeakReference(simulatorListener))
        sim.subscribeForLocationUpdates(
            settings(),
            WeakReference(listener),
        )
        sim.speed = speedMps
        val settings = LocationSettingsFactory.fineSettings()
            .setProvideHeading(gpsHeading)
            .setProvideSpeed(true)
            .setSpeed(speedMps)
        sim.startSimulation(listOf(SimulationSettings(Polyline(route), settings)))
        simulator = sim
    }

    /** Back to the real GPS. */
    fun stopSimulation() {
        val sim = simulator ?: return
        simulator = null
        sim.unsubscribe(WeakReference(listener))
        sim.unsubscribeFromSimulatorEvents(WeakReference(simulatorListener))
        sim.stopSimulation()
        MapKitFactory.getInstance().resetLocationManagerToDefault()
        manager?.subscribeForLocationUpdates(
            settings(),
            WeakReference(listener),
        )
    }
}
