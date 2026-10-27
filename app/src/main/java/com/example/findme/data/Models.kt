package com.example.findme.data

import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.search.SuggestItem

/** A place shown in the place card: an organization, an address, or a point on the map. */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val category: String? = null,
    val categoryClass: String? = null,
    val address: String? = null,
    val uri: String? = null,
    val phone: String? = null,
    val website: String? = null,
    val hoursText: String? = null,
    val isOpenNow: Boolean? = null,
    val rating: Float? = null,
    val ratingsCount: Int? = null,
    val isBusiness: Boolean = false,
    val fromPoi: Boolean = false,
    val isMapPoint: Boolean = false,
) {
    val point: Point get() = Point(lat, lon)
}

data class SuggestUi(
    val title: String,
    val subtitle: String?,
    val distance: String?,
    val type: SuggestItem.Type,
    val item: SuggestItem,
)

enum class TravelMode(val title: String) {
    Car("Car"),
    Transit("Transit"),
    Walk("Walk"),
    Bike("Bike"),
    Scooter("Scooter"),
}

data class Waypoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val isMyLocation: Boolean = false,
    val categoryClass: String? = null,
    val category: String? = null,
) {
    val point: Point get() = Point(lat, lon)
}

/** One drawable piece of a route. [segmentColors] colors each polyline segment (traffic jams). */
data class RouteLine(
    val points: List<Point>,
    val color: Int,
    val segmentColors: List<Int>? = null,
    val dashed: Boolean = false,
    /** Segment of the variant's [NavTrack] where this line begins (transit: one line per leg). */
    val trackStart: Int = 0,
)

enum class LegKind { Walk, Transport }

data class TransitLeg(
    val kind: LegKind,
    val label: String,
    val color: Int? = null,
    val vehicle: String? = null,
)

data class RouteVariant(
    val durationText: String,
    val durationSeconds: Double,
    val distanceText: String?,
    val details: String?,
    val lines: List<RouteLine>,
    val legs: List<TransitLeg>,
    val bbox: BoundingBox?,
    val distanceMeters: Double = 0.0,
    val hasIncidents: Boolean = false,
    val track: NavTrack? = null,
    /** Public transport only: total time on foot and departure time of the first vehicle. */
    val walkSeconds: Double = 0.0,
    val departureText: String? = null,
    /** Public transport only: walk and ride steps in order, for guidance. */
    val steps: List<TransitStep> = emptyList(),
    /** Track segment where each intermediate stop is reached, in order. */
    val stopSegments: List<Int> = emptyList(),
)

/**
 * One step of a public transport route: a walk, or a ride on one line.
 * [startSegment] is the index of the track segment where the step begins.
 */
data class TransitStep(
    val kind: LegKind,
    val startSegment: Int,
    val seconds: Double,
    /** Ride: "12", "M1"; null for a walk. */
    val lineName: String? = null,
    val color: Int? = null,
    val vehicle: String? = null,
    /** Ride: where to get on and off, and how many stops in between. */
    val fromStop: String? = null,
    val toStop: String? = null,
    val stops: Int = 0,
    /** Ride: departure and arrival by the timetable, when MapKit knows them. */
    val departureText: String? = null,
    val departureMillis: Long? = null,
    val arrivalMillis: Long? = null,
)

enum class Maneuver {
    Straight, SlightLeft, SlightRight, Left, Right, HardLeft, HardRight,
    ForkLeft, ForkRight, UturnLeft, UturnRight, Roundabout, Finish,
}

/** A maneuver that starts at polyline segment [segment]. */
data class ManeuverPoint(val segment: Int, val maneuver: Maneuver)

/** Route geometry prepared for guidance: progress, speed limits and the next maneuver. */
class NavTrack(
    val points: List<Point>,
    /** Speed limit in m/s per segment, null where unknown. */
    val speedLimits: List<Float?>,
    val maneuvers: List<ManeuverPoint>,
    /** Traffic color per segment, or null when the route has no traffic data. */
    val segmentColors: List<Int>?,
) {
    /** Distance from the start to every point, in meters. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + Geo.distanceMeters(points[i - 1], points[i])
    }
    val length: Double get() = cumulative.lastOrNull() ?: 0.0
}

/** Travel time by car to a place, shown on the home/work buttons and the place card. */
data class DriveEta(
    val seconds: Double,
    val text: String,
    val distanceText: String,
    /** Time with traffic divided by time without it. */
    val trafficRatio: Double,
)

data class RouteResult(
    val loading: Boolean = false,
    val variants: List<RouteVariant> = emptyList(),
    val error: String? = null,
)

/** Which point of a route the search picks: start, destination, or an intermediate stop. */
enum class WaypointSlot { From, To, Stop }

enum class MapLayerType(val title: String) { Scheme("Map"), Satellite("Satellite"), Hybrid("Hybrid") }

enum class NightMode(val title: String) { Auto("Auto"), On("On"), Off("Off") }

data class TrafficInfo(val level: Int, val color: Int)
