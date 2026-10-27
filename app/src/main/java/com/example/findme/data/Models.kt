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

/** "Bus 12", "Metro M1", "Train". */
fun rideName(step: TransitStep): String {
    val kind = when (step.vehicle) {
        "bus" -> "Bus"
        "minibus" -> "Minibus"
        "trolleybus" -> "Trolleybus"
        "tramway", "tram" -> "Tram"
        "underground" -> "Metro"
        "railway", "suburban" -> "Train"
        else -> "Ride"
    }
    return listOfNotNull(kind, step.lineName).joinToString(" ")
}

enum class Maneuver {
    Straight, SlightLeft, SlightRight, Left, Right, HardLeft, HardRight,
    ForkLeft, ForkRight, UturnLeft, UturnRight, Roundabout, RoundaboutExit, Finish,
}

/** A maneuver that starts at polyline segment [segment]. */
data class ManeuverPoint(val segment: Int, val maneuver: Maneuver)

/** One arrow painted on a lane. */
enum class LaneArrow { UturnLeft, SharpLeft, Left, SlightLeft, Straight, SlightRight, Right, SharpRight, UturnRight }

/** A lane: its arrows, which of them the route takes (null: not this lane), and if it's for buses etc. */
data class RoadLane(val arrows: List<LaneArrow>, val take: LaneArrow?, val special: Boolean = false)

/** The lanes of the road at [segment] (and [t] 0..1 along it), left to right. */
data class LaneSignPoint(val segment: Int, val t: Double, val lanes: List<RoadLane>)

/** What a road event on the route is. */
enum class RoadEventKind { SpeedCamera, LaneCamera, Camera, Police, Accident, Works, Closed, Danger, School }

/** A camera, accident, road works... on the route at [segment] / [t]. */
data class RouteEvent(
    val id: String,
    val segment: Int,
    val t: Double,
    val kind: RoadEventKind,
    /** A camera's speed limit, km/h. */
    val limitKmh: Int? = null,
)

/** Route geometry prepared for guidance: progress, speed limits and the next maneuver. */
class NavTrack(
    val points: List<Point>,
    /** Speed limit in m/s per segment, null where unknown. */
    val speedLimits: List<Float?>,
    val maneuvers: List<ManeuverPoint>,
    /** Traffic color per segment, or null when the route has no traffic data. */
    val segmentColors: List<Int>?,
    /** Traffic speed in m/s per segment (0 = unknown), or null when the route has none. */
    segmentSpeeds: List<Double>? = null,
    /** Lane signs along the route, in order (car routes only). */
    val laneSigns: List<LaneSignPoint> = emptyList(),
    /** Cameras, accidents, works on the route, in order (car routes only). */
    val events: List<RouteEvent> = emptyList(),
) {
    /** Distance from the start to a point given as a segment and a share of it. */
    fun alongAt(segment: Int, t: Double): Double {
        if (cumulative.size < 2) return 0.0
        val i = segment.coerceIn(0, cumulative.size - 2)
        return cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t.coerceIn(0.0, 1.0)
    }

    /** Distance from the start to every point, in meters. */
    val cumulative: DoubleArray = DoubleArray(points.size).also { acc ->
        for (i in 1 until points.size) acc[i] = acc[i - 1] + Geo.distanceMeters(points[i - 1], points[i])
    }
    val length: Double get() = cumulative.lastOrNull() ?: 0.0

    /**
     * Time from the start to every point at the traffic speed of each segment, in seconds;
     * null without traffic data. Segments of unknown speed get the average of the known ones.
     */
    private val cumulativeTime: DoubleArray? = segmentSpeeds
        ?.takeIf { it.size == points.size - 1 && points.size >= 2 }
        ?.let { speeds ->
            val ok = { i: Int -> speeds[i].isFinite() && speeds[i] > 0 }
            val len = { i: Int -> cumulative[i + 1] - cumulative[i] }
            val known = speeds.indices.filter(ok)
            val knownTime = known.sumOf { len(it) / speeds[it].coerceAtLeast(MIN_JAM_SPEED) }
            if (knownTime <= 0) return@let null
            val average = known.sumOf(len) / knownTime
            DoubleArray(points.size).also { acc ->
                for (i in speeds.indices) {
                    val v = if (ok(i)) speeds[i].coerceAtLeast(MIN_JAM_SPEED) else average
                    acc[i + 1] = acc[i] + len(i) / v
                }
            }
        }

    /**
     * Share of the trip's time spent driving the first [along] meters, 0..1. With traffic data
     * a jam ahead weighs more than an empty road; without it, it is the share of the distance.
     */
    fun timeShare(along: Double): Double {
        val total = length
        if (total <= 0) return 1.0
        val time = cumulativeTime ?: return (along / total).coerceIn(0.0, 1.0)
        if (along <= 0) return 0.0
        if (along >= total) return 1.0
        var i = java.util.Arrays.binarySearch(cumulative, along)
        if (i < 0) i = -i - 2
        i = i.coerceIn(0, cumulative.size - 2)
        val segLen = cumulative[i + 1] - cumulative[i]
        val t = if (segLen > 0) (along - cumulative[i]) / segLen else 0.0
        return (time[i] + (time[i + 1] - time[i]) * t) / time.last()
    }

    private companion object {
        /** A standing jam still moves a little: keeps the time finite. */
        const val MIN_JAM_SPEED = 1.0
    }
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
