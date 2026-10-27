package com.example.findme.maps

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.PointF
import android.view.animation.LinearInterpolator
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.MapLayerType
import com.example.findme.data.Place
import com.example.findme.data.RouteLine
import com.example.findme.data.RouteVariant
import com.example.findme.data.TrafficInfo
import com.example.findme.data.Waypoint
import com.example.findme.ui.MapCommand
import com.example.findme.ui.MapViewModel
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.ScreenPoint
import com.yandex.mapkit.ScreenRect
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polyline
import com.yandex.mapkit.layers.GeoObjectTapListener
import com.yandex.mapkit.layers.ObjectEvent
import com.yandex.mapkit.logo.Alignment
import com.yandex.mapkit.logo.HorizontalAlignment
import com.yandex.mapkit.logo.Padding
import com.yandex.mapkit.logo.VerticalAlignment
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.GeoObjectSelectionMetadata
import com.yandex.mapkit.map.IconStyle
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.map.MapObjectTapListener
import com.yandex.mapkit.map.MapType
import com.yandex.mapkit.map.PlacemarkMapObject
import com.yandex.mapkit.map.PolylineMapObject
import com.yandex.mapkit.map.RotationType
import com.yandex.mapkit.map.TextStyle
import com.yandex.mapkit.map.VisibleRegionUtils
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.traffic.TrafficColor
import com.yandex.mapkit.traffic.TrafficLevel
import com.yandex.mapkit.traffic.TrafficListener
import com.yandex.mapkit.user_location.UserLocationLayer
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Orange used for meet-ups on the map. */
const val MEET_COLOR = 0xFFFF7A00.toInt()

/** A meet-up member drawn on the map. [label] goes under the pin, e.g. "Ali · 12 min". */
data class MeetMarker(
    val uid: String,
    val name: String,
    val label: String,
    val point: Point,
    val color: Int,
    val photo: android.graphics.Bitmap?,
    val stale: Boolean,
    val arrived: Boolean,
    /** The rest of their route to the place, drawn in [color]; null to hide it. */
    val route: List<Point>? = null,
)

/** What the live meet-up layer shows: the meeting place and the members who share their location. */
data class MeetMapData(val place: Point, val title: String, val markers: List<MeetMarker>)

/** Owns the MapKit [MapView] and draws everything the [MapViewModel] asks for. */
class MapEngine(private val context: Context, private val vm: MapViewModel) {

    val mapView = MapView(context)
    private val mapWindow get() = mapView.mapWindow
    private val map get() = mapView.mapWindow.map
    private val density = context.resources.displayMetrics.density

    private val routeLayer = map.mapObjects.addCollection()
    private val balloonLayer = map.mapObjects.addCollection()
    private val resultsLayer = map.mapObjects.addCollection()
    private val markerLayer = map.mapObjects.addCollection()
    private val meetLayer = map.mapObjects.addCollection()
    private val trafficLayer = MapKitFactory.getInstance().createTrafficLayer(mapWindow)
    private var userLayer: UserLocationLayer? = null
    private var userView: UserLocationView? = null

    private var viewportTop = 0f
    private var viewportBottom = Float.NaN
    private var darkMap = false
    private var navMode = false

    // MapKit keeps only weak references to listeners, so they live as fields here.
    private val inputListener = object : InputListener {
        override fun onMapTap(map: Map, point: Point) = vm.onMapTap()
        override fun onMapLongTap(map: Map, point: Point) = vm.onLongTap(point)
    }

    private val geoObjectTapListener = GeoObjectTapListener { event ->
        val geo = event.geoObject
        val selection = geo.metadataContainer.getItem(GeoObjectSelectionMetadata::class.java)
            ?: return@GeoObjectTapListener false
        map.selectGeoObject(selection)
        vm.onPoiTap(geo)
        true
    }

    private val cameraListener = CameraListener { _, position, reason, finished ->
        vm.onCameraChanged(position, reason == CameraUpdateReason.GESTURES, finished)
        updateScale()
    }

    private val trafficListener = object : TrafficListener {
        override fun onTrafficChanged(level: TrafficLevel?) {
            vm.trafficInfo = level?.let {
                val color = when (it.color) {
                    TrafficColor.GREEN -> 0xFF3CB83C.toInt()
                    TrafficColor.YELLOW -> 0xFFFFC820.toInt()
                    else -> 0xFFFF4F3F.toInt()
                }
                TrafficInfo(it.level, color)
            }
        }

        override fun onTrafficLoading() = Unit
        override fun onTrafficExpired() {
            vm.trafficInfo = null
        }
    }

    private val resultTapListener = MapObjectTapListener { obj, _ ->
        (obj.userData as? Int)?.let(vm::onResultPinTap)
        true
    }

    private val routeTapListener = MapObjectTapListener { obj, _ ->
        (obj.userData as? Int)?.let(vm.route::selectVariant)
        true
    }

    init {
        map.move(vm.lastCamera ?: CameraPosition(MapViewModel.DEFAULT_POINT, 12f, 0f, 0f))
        map.addInputListener(WeakReference(inputListener))
        map.addTapListener(WeakReference(geoObjectTapListener))
        map.addCameraListener(WeakReference(cameraListener))
        trafficLayer.addTrafficListener(WeakReference(trafficListener))
        resultsLayer.addTapListener(WeakReference(resultTapListener))
        routeLayer.addTapListener(WeakReference(routeTapListener))
        balloonLayer.addTapListener(WeakReference(routeTapListener))

        vm.visibleArea = { VisibleRegionUtils.toPolygon(map.visibleRegion) }
        vm.visibleBox = { VisibleRegionUtils.getBounds(map.visibleRegion) }
        vm.currentZoom = { map.cameraPosition.zoom }
    }

    fun detach() {
        vm.visibleArea = null
        vm.visibleBox = null
        vm.currentZoom = null
    }

    fun onStart() = mapView.onStart()
    fun onStop() = mapView.onStop()

    // ---------------------------------------------------------------------
    // Layers
    // ---------------------------------------------------------------------

    fun applyLayers(type: MapLayerType, dark: Boolean, traffic: Boolean) {
        map.mapType = when (type) {
            MapLayerType.Scheme -> MapType.VECTOR_MAP
            MapLayerType.Satellite -> MapType.SATELLITE
            MapLayerType.Hybrid -> MapType.HYBRID
        }
        map.isNightModeEnabled = dark
        trafficLayer.isTrafficVisible = traffic
        if (dark != darkMap) {
            darkMap = dark
            lastResults?.let { (list, sel) -> renderResults(list, sel) }
        }
    }

    /** Scale ruler: meters per view pixel at the center of the map. */
    private fun updateScale() {
        val w = mapWindow.width().toFloat()
        val h = mapWindow.height().toFloat()
        if (w <= 0f || h <= 0f || mapView.height <= 0) return
        val a = mapWindow.screenToWorld(ScreenPoint(w / 2 - 50f, h / 2)) ?: return
        val b = mapWindow.screenToWorld(ScreenPoint(w / 2 + 50f, h / 2)) ?: return
        val perWindowPx = Geo.distanceMeters(a, b) / 100.0
        val perViewPx = (perWindowPx * h / mapView.height).toFloat()
        if (abs(perViewPx - vm.metersPerPx) > vm.metersPerPx * 0.01f) vm.metersPerPx = perViewPx
    }

    // ---------------------------------------------------------------------
    // User location: red dot, or the yellow arrow while navigating
    // ---------------------------------------------------------------------

    private val userObjectListener = object : UserLocationObjectListener {
        override fun onObjectAdded(view: UserLocationView) {
            userView = view
            styleUser(view)
        }

        override fun onObjectUpdated(view: UserLocationView, event: ObjectEvent) {
            if (userView !== view) {
                userView = view
                styleUser(view)
            }
        }

        override fun onObjectRemoved(view: UserLocationView) {
            if (userView === view) userView = null
        }
    }

    private fun styleUser(view: UserLocationView) {
        if (!view.isValid) return
        if (navMode) {
            val arrow = ImageProvider.fromBitmap(PinFactory.navArrow(context))
            val style = IconStyle().setAnchor(PointF(0.5f, 0.6f)).setRotationType(RotationType.ROTATE).setZIndex(50f)
            view.arrow.setIcon(arrow, style)
            view.pin.setIcon(arrow, style)
            view.accuracyCircle.fillColor = 0
            view.accuracyCircle.strokeWidth = 0f
        } else {
            view.pin.setIcon(
                ImageProvider.fromBitmap(PinFactory.userPin(context, heading = false)),
                IconStyle().setAnchor(PointF(0.5f, 0.5f)).setZIndex(50f),
            )
            view.arrow.setIcon(
                ImageProvider.fromBitmap(PinFactory.userPin(context, heading = true)),
                IconStyle().setAnchor(PointF(0.5f, 0.5f)).setRotationType(RotationType.ROTATE).setZIndex(50f),
            )
            // The default accuracy circle is opaque; Yandex Maps draws it light and translucent.
            view.accuracyCircle.fillColor = 0x1AFA453A
            view.accuracyCircle.strokeColor = 0x40FA453A
            view.accuracyCircle.strokeWidth = 1f
        }
    }

    fun enableUserLocation() {
        if (userLayer != null) return
        userLayer = MapKitFactory.getInstance().createUserLocationLayer(mapWindow).apply {
            isVisible = true
            isHeadingModeActive = false
            setObjectListener(WeakReference(userObjectListener))
        }
    }

    /** Switches the map between normal mode and guidance (yellow arrow, camera low on the screen). */
    fun setNavigationMode(enabled: Boolean) {
        if (navMode == enabled) return
        navMode = enabled
        userView?.let(::styleUser)
        applyFocus()
    }

    // ---------------------------------------------------------------------
    // Map objects
    // ---------------------------------------------------------------------

    private var lastResults: Pair<List<Place>, String?>? = null

    private fun Place.style() = CategoryStyles.of(categoryClass, category, isBusiness)

    fun renderResults(places: List<Place>, selectedId: String?) {
        lastResults = places to selectedId
        resultsLayer.clear()
        val textStyle = TextStyle()
            .setSize(11f)
            .setColor(if (darkMap) 0xFFF2F2F2.toInt() else 0xFF21201F.toInt())
            .setOutlineColor(if (darkMap) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
            .setOutlineWidth(1.5f)
            .setPlacement(TextStyle.Placement.RIGHT)
            .setOffset(4f)
            .setTextOptional(true)
        places.forEachIndexed { index, place ->
            val selected = place.id == selectedId
            val style = place.style()
            val pin = if (selected) {
                val bmp = PinFactory.placePin(context, style)
                resultsLayer.addPlacemark(
                    place.point,
                    ImageProvider.fromBitmap(bmp),
                    IconStyle().setAnchor(PinFactory.pinAnchor(context, bmp)).setZIndex(10f),
                )
            } else {
                resultsLayer.addPlacemark(
                    place.point,
                    ImageProvider.fromBitmap(PinFactory.resultPin(context, style)),
                    IconStyle().setAnchor(PointF(0.5f, 0.5f)).setZIndex(1f),
                )
            }
            pin.userData = index
            // MapKit does not hide overlapping labels, so names are shown only for a few results.
            if (!selected && places.size <= 5) pin.setText(place.name, textStyle)
        }
    }

    fun renderMarker(place: Place?, poiSelected: Boolean) {
        markerLayer.clear()
        if (!poiSelected) map.deselectGeoObject()
        place ?: return
        val style = if (place.isMapPoint) CategoryStyle(R.drawable.ms_location_on, 0xFFFA453A.toInt()) else place.style()
        val bmp = PinFactory.placePin(context, style)
        markerLayer.addPlacemark(
            place.point,
            ImageProvider.fromBitmap(bmp),
            IconStyle().setAnchor(PinFactory.pinAnchor(context, bmp)).setZIndex(20f),
        )
    }

    /** [transit]: public transport shows only the selected variant and no time balloons, like Yandex Maps. */
    fun renderRoute(
        from: Waypoint?,
        to: Waypoint?,
        stops: List<Waypoint>,
        variants: List<RouteVariant>,
        selected: Int,
        visible: Boolean,
        transit: Boolean,
    ) {
        routeLayer.clear()
        balloonLayer.clear()
        if (!visible) return
        if (!transit) {
            variants.forEachIndexed { index, variant ->
                if (index != selected) variant.lines.forEach { addRouteLine(it, alternative = true, index = index) }
            }
        }
        variants.getOrNull(selected)?.lines?.forEach { addRouteLine(it, alternative = false, index = selected) }
        if (from != null && !from.isMyLocation) {
            routeLayer.addPlacemark(
                from.point,
                ImageProvider.fromBitmap(PinFactory.startPin(context)),
                IconStyle().setAnchor(PointF(0.5f, 0.5f)).setZIndex(30f),
            )
        }
        // Stops on the way: numbered circles, 1, 2, 3...
        stops.forEachIndexed { i, stop ->
            routeLayer.addPlacemark(
                stop.point,
                ImageProvider.fromBitmap(PinFactory.stopPin(context, i + 1)),
                IconStyle().setAnchor(PointF(0.5f, 0.5f)).setZIndex(31f),
            )
        }
        to?.let {
            val style = when {
                it.name == "Home" -> CategoryStyle(R.drawable.ms_home, 0xFF21201F.toInt())
                it.name == "Work" -> CategoryStyle(R.drawable.ms_work, 0xFF21201F.toInt())
                it.categoryClass == null && it.category == null -> CategoryStyle(R.drawable.ms_flag, 0xFF21201F.toInt())
                else -> CategoryStyles.of(it.categoryClass, it.category)
            }
            val bmp = PinFactory.finishPin(context, style)
            val finish = routeLayer.addPlacemark(
                it.point,
                ImageProvider.fromBitmap(bmp),
                IconStyle().setAnchor(PinFactory.pinAnchor(context, bmp)).setZIndex(30f),
            )
            // Destination name under the pin, e.g. "Home".
            finish.setText(
                it.name,
                TextStyle()
                    .setSize(13f)
                    .setColor(if (darkMap) 0xFFF2F2F2.toInt() else 0xFF21201F.toInt())
                    .setOutlineColor(if (darkMap) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
                    .setOutlineWidth(2f)
                    .setPlacement(TextStyle.Placement.BOTTOM)
                    .setOffset(2f),
            )
        }
        if (!navMode && !transit) renderBalloons(variants, selected)
    }

    // Live meet-up objects, kept between updates: re-creating them made the pins blink.
    private class MemberObjects(
        val pin: PlacemarkMapObject,
        var bitmap: android.graphics.Bitmap,
        var label: String,
        /** Where the pin is drawn now (it glides towards the newest position). */
        var shown: Point,
        var target: Point,
        var line: PolylineMapObject? = null,
        var linePoints: List<Point>? = null,
        var animator: ValueAnimator? = null,
    )

    private var meetFlag: PlacemarkMapObject? = null
    private var meetFlagPoint: Point? = null
    private var meetFlagTitle: String? = null
    private var meetDark: Boolean? = null
    private val meetMembers = HashMap<String, MemberObjects>()

    /**
     * Live meet-up: an orange flag at the place, a pin per member who shares their location and
     * the rest of their route. Updates the existing objects in place; moved pins glide.
     */
    fun renderMeetup(data: MeetMapData?) {
        // Labels take their colors from the map theme: rebuild once when it changes.
        if (data == null || meetDark != darkMap) clearMeetup()
        data ?: return
        meetDark = darkMap

        val flag = meetFlag
        if (flag == null) {
            val bmp = PinFactory.placePin(context, CategoryStyle(R.drawable.ms_flag, MEET_COLOR))
            meetFlag = meetLayer.addPlacemark(
                data.place,
                ImageProvider.fromBitmap(bmp),
                IconStyle().setAnchor(PinFactory.pinAnchor(context, bmp)).setZIndex(36f),
            ).also { it.setText(data.title, labelStyle()) }
        } else {
            if (meetFlagPoint?.let { same(it, data.place) } != true) flag.geometry = data.place
            if (meetFlagTitle != data.title) flag.setText(data.title, labelStyle())
        }
        meetFlagPoint = data.place
        meetFlagTitle = data.title

        val ids = data.markers.mapTo(HashSet()) { it.uid }
        meetMembers.keys.filter { it !in ids }.forEach { uid -> meetMembers.remove(uid)?.let(::removeMember) }

        data.markers.forEachIndexed { i, m ->
            val bmp = PinFactory.memberPin(
                context, m.uid, m.name.take(1).uppercase(), m.color, m.photo, m.stale, m.arrived,
            )
            // Fresh positions above stale ones.
            val style = IconStyle().setAnchor(PinFactory.pinAnchor(context, bmp)).setZIndex(if (m.stale) 37f else 38f + i * 0.01f)
            val obj = meetMembers[m.uid] ?: MemberObjects(
                pin = meetLayer.addPlacemark(m.point, ImageProvider.fromBitmap(bmp), style).also {
                    it.setText(m.label, labelStyle())
                },
                bitmap = bmp,
                label = m.label,
                shown = m.point,
                target = m.point,
            ).also { meetMembers[m.uid] = it }

            // The pin factory caches bitmaps, so the same look gives the same instance.
            if (obj.bitmap !== bmp) {
                obj.pin.setIcon(ImageProvider.fromBitmap(bmp), style)
                obj.bitmap = bmp
            }
            if (obj.label != m.label) {
                obj.pin.setText(m.label, labelStyle())
                obj.label = m.label
            }
            if (!same(obj.target, m.point)) glide(obj, m.point)
            updateMemberLine(obj, m.route, m.color)
        }
    }

    /** Moves a pin smoothly instead of jumping; far jumps (bad GPS fix, first fix) are instant. */
    private fun glide(obj: MemberObjects, to: Point) {
        obj.animator?.cancel()
        obj.target = to
        val from = obj.shown
        if (Geo.distanceMeters(from, to) > 2_000) {
            obj.shown = to
            obj.pin.geometry = to
            return
        }
        obj.animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1_000
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                if (!obj.pin.isValid) {
                    cancel()
                    return@addUpdateListener
                }
                val f = a.animatedValue as Float
                val p = Point(
                    from.latitude + (to.latitude - from.latitude) * f,
                    from.longitude + (to.longitude - from.longitude) * f,
                )
                obj.shown = p
                obj.pin.geometry = p
            }
            start()
        }
    }

    private fun updateMemberLine(obj: MemberObjects, route: List<Point>?, color: Int) {
        if (route == null || route.size < 2) {
            obj.line?.let { if (it.isValid) meetLayer.remove(it) }
            obj.line = null
            obj.linePoints = null
            return
        }
        if (obj.linePoints === route) return
        val line = obj.line
        if (line == null) {
            obj.line = meetLayer.addPolyline(Polyline(route)).apply {
                setStrokeColor(color)
                strokeWidth = 4f
                outlineColor = 0xFFFFFFFF.toInt()
                outlineWidth = 1.5f
                zIndex = 3.5f
            }
        } else {
            line.geometry = Polyline(route)
        }
        obj.linePoints = route
    }

    private fun removeMember(obj: MemberObjects) {
        obj.animator?.cancel()
        if (obj.pin.isValid) meetLayer.remove(obj.pin)
        obj.line?.let { if (it.isValid) meetLayer.remove(it) }
    }

    private fun clearMeetup() {
        meetMembers.values.forEach { it.animator?.cancel() }
        meetMembers.clear()
        meetLayer.clear()
        meetFlag = null
        meetFlagPoint = null
        meetFlagTitle = null
        meetDark = null
    }

    private fun same(a: Point, b: Point) = a.latitude == b.latitude && a.longitude == b.longitude

    private fun labelStyle() = TextStyle()
        .setSize(13f)
        .setColor(if (darkMap) 0xFFF2F2F2.toInt() else 0xFF21201F.toInt())
        .setOutlineColor(if (darkMap) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
        .setOutlineWidth(2f)
        .setPlacement(TextStyle.Placement.BOTTOM)
        .setOffset(2f)

    private fun addRouteLine(line: RouteLine, alternative: Boolean, index: Int) {
        if (line.points.size < 2) return
        val polyline = routeLayer.addPolyline(Polyline(line.points))
        polyline.userData = index
        if (alternative) {
            // Walking alternatives are gray dashes, driving ones pale blue.
            polyline.setStrokeColor(if (line.dashed) 0xFFA3A3A3.toInt() else 0xFF9DB4F0.toInt())
            polyline.strokeWidth = 5.5f
            polyline.outlineColor = if (line.dashed) 0xFFFFFFFF.toInt() else 0xFF6F8DE0.toInt()
            polyline.outlineWidth = 1f
            polyline.zIndex = 1f
        } else {
            val colors = line.segmentColors
            if (colors != null) {
                // MapKit takes palette indices per segment, not colors.
                val palette = colors.distinct()
                palette.forEachIndexed { i, color -> polyline.setPaletteColor(i, color) }
                polyline.setStrokeColors(colors.map { palette.indexOf(it) })
            } else {
                polyline.setStrokeColor(line.color)
            }
            polyline.strokeWidth = 7f
            polyline.outlineColor = if (darkMap) 0xFF000000.toInt() else 0x59000000
            polyline.outlineWidth = 1.2f
            polyline.zIndex = 5f
        }
        if (line.dashed) {
            polyline.dashLength = 9f
            polyline.gapLength = 5f
            if (!alternative) polyline.outlineColor = 0xFFFFFFFF.toInt()
        }
    }

    /** "21 min / 10 km" on the selected route and "+6 min" on alternatives, where routes diverge. */
    private fun renderBalloons(variants: List<RouteVariant>, selected: Int) {
        val main = variants.getOrNull(selected) ?: return
        val samples = variants.map { v -> sample(v.lines.flatMap { it.points }, 120) }
        variants.forEachIndexed { index, variant ->
            val own = samples[index]
            if (own.isEmpty()) return@forEachIndexed
            val others = samples.filterIndexed { i, _ -> i != index }.flatten()
            val spot = if (others.isEmpty()) {
                own[own.size / 2]
            } else {
                // The point of this route farthest from all others, skipping the shared ends.
                own.subList(own.size / 8, own.size - own.size / 8).ifEmpty { own }
                    .maxBy { p -> others.minOf { o -> Geo.distanceMeters(p, o) } }
            }
            val isMain = index == selected
            val bmp = if (isMain) {
                PinFactory.routeBalloon(context, variant.durationText, variant.distanceText, true, 0xFFFFFFFF.toInt())
            } else {
                val diff = variant.durationSeconds - main.durationSeconds
                val (text, color) = when {
                    diff >= 60 -> "+${Geo.durationText(diff)}" to 0xFFFA453A.toInt()
                    diff <= -60 -> "−${Geo.durationText(-diff)}" to 0xFF2FA84F.toInt()
                    else -> variant.durationText to 0xFF21201F.toInt()
                }
                PinFactory.routeBalloon(context, text, null, false, color)
            }
            val mark = balloonLayer.addPlacemark(
                spot,
                ImageProvider.fromBitmap(bmp),
                IconStyle().setAnchor(PinFactory.balloonAnchor(context, bmp)).setZIndex(if (isMain) 41f else 40f),
            )
            mark.userData = index
        }
    }

    private fun sample(points: List<Point>, max: Int): List<Point> {
        if (points.size <= max) return points
        val step = points.size.toDouble() / max
        return List(max) { points[(it * step).toInt()] }
    }

    // ---------------------------------------------------------------------
    // Camera
    // ---------------------------------------------------------------------

    /** Map area not covered by the top panel and the bottom sheet, in pixels. */
    fun setViewport(topPx: Float, bottomPx: Float, logoLiftDp: Float = 0f) {
        viewportTop = topPx
        viewportBottom = bottomPx
        val h = mapView.height
        if (h > 0 && !bottomPx.isNaN()) {
            val logoBottom = (h - bottomPx).coerceAtLeast(0f) + (8 + logoLiftDp) * density
            map.logo.setAlignment(Alignment(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM))
            map.logo.setPadding(Padding((12 * density).toInt(), logoBottom.toInt()))
        } else if (h > 0) {
            // Guidance: the logo sits above the bottom panel.
            map.logo.setAlignment(Alignment(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM))
            map.logo.setPadding(Padding((12 * density).toInt(), (96 * density).toInt()))
        }
    }

    private fun visibleRect(): ScreenRect? {
        val w = mapWindow.width().toFloat()
        val h = mapWindow.height().toFloat()
        if (w <= 0f || h <= 0f) return null
        // MapWindow may render at a different scale than the view.
        val scale = h / mapView.height.coerceAtLeast(1)
        val bottom = (if (viewportBottom.isNaN()) h else viewportBottom * scale).coerceIn(0f, h)
        val top = (viewportTop * scale).coerceIn(0f, bottom)
        if (bottom - top < h * 0.2f) return null
        return ScreenRect(ScreenPoint(0f, top), ScreenPoint(w, bottom))
    }

    /** Moves the camera focus into the visible area without visually shifting the map. */
    private fun applyFocus() {
        val rect = visibleRect() ?: return
        val cx = (rect.topLeft.x + rect.bottomRight.x) / 2
        val height = rect.bottomRight.y - rect.topLeft.y
        // In guidance the arrow stays in the lower part of the screen, like Yandex Navigator.
        val focus = ScreenPoint(cx, rect.topLeft.y + height * (if (navMode) 0.72f else 0.5f))
        val oldRect = mapWindow.focusRect
        val oldPoint = mapWindow.focusPoint
        if (oldRect != null && oldPoint != null &&
            abs(oldRect.topLeft.y - rect.topLeft.y) < 2 &&
            abs(oldRect.bottomRight.y - rect.bottomRight.y) < 2 &&
            abs(oldPoint.y - focus.y) < 2
        ) return
        val world = mapWindow.screenToWorld(focus) ?: return
        val cp = map.cameraPosition
        mapWindow.focusRect = rect
        mapWindow.focusPoint = focus
        map.move(CameraPosition(world, cp.zoom, cp.azimuth, cp.tilt))
    }

    fun handle(cmd: MapCommand) {
        val cp = map.cameraPosition
        when (cmd) {
            is MapCommand.MoveTo -> {
                applyFocus()
                map.move(
                    CameraPosition(cmd.point, cmd.zoom ?: cp.zoom, cp.azimuth, cp.tilt),
                    Animation(Animation.Type.SMOOTH, 0.5f),
                    null,
                )
            }

            is MapCommand.Fit -> {
                applyFocus()
                val pos = map.cameraPosition(Geometry.fromBoundingBox(cmd.box))
                map.move(
                    CameraPosition(pos.target, min(pos.zoom - 0.4f, 17f), 0f, 0f),
                    Animation(Animation.Type.SMOOTH, 0.6f),
                    null,
                )
            }

            is MapCommand.Follow -> {
                applyFocus()
                val heading = cmd.heading
                // Turn the short way round (350° -> 10° is +20°, not -340°).
                val azimuth = if (heading == null) cp.azimuth
                else cp.azimuth + (((heading - cp.azimuth) % 360 + 540) % 360 - 180)
                // Without a GPS course MapKit shows the pin, not the arrow: aim it by my direction.
                if (heading != null) userView?.takeIf { it.isValid }?.pin?.direction = heading
                map.move(
                    // 16.5: about 200 m on the scale ruler (each zoom step halves it).
                    CameraPosition(cmd.point, 16.5f, azimuth, 55f),
                    Animation(Animation.Type.LINEAR, 0.8f),
                    null,
                )
            }

            MapCommand.ZoomIn -> map.move(
                CameraPosition(cp.target, min(cp.zoom + 1f, 21f), cp.azimuth, cp.tilt),
                Animation(Animation.Type.SMOOTH, 0.25f),
                null,
            )

            MapCommand.ZoomOut -> map.move(
                CameraPosition(cp.target, max(cp.zoom - 1f, 2f), cp.azimuth, cp.tilt),
                Animation(Animation.Type.SMOOTH, 0.25f),
                null,
            )

            MapCommand.ResetNorth -> map.move(
                CameraPosition(cp.target, cp.zoom, 0f, 0f),
                Animation(Animation.Type.SMOOTH, 0.4f),
                null,
            )
        }
    }
}
