package com.example.findme.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.findme.data.DriveEta
import com.example.findme.data.Geo
import com.example.findme.data.MapLayerType
import com.example.findme.data.NavStore
import com.example.findme.data.NightMode
import com.example.findme.data.Place
import com.example.findme.data.SavedNav
import com.example.findme.data.SavedPlace
import com.example.findme.data.SavedPlacesStore
import com.example.findme.data.SuggestUi
import com.example.findme.data.TrafficInfo
import com.example.findme.data.Waypoint
import com.example.findme.data.WaypointSlot
import com.example.findme.data.location.LocationFix
import com.example.findme.data.location.LocationRepository
import com.example.findme.data.meet.Meeting
import com.example.findme.data.toSaved
import com.example.findme.maps.PlaceSearchService
import com.example.findme.maps.RouteService
import com.example.findme.maps.toPlace
import com.example.findme.ui.nav.NavigationController
import com.example.findme.ui.route.RouteController
import com.example.findme.ui.search.SearchController
import com.yandex.mapkit.GeoObject
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.search.SuggestItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

sealed interface Screen {
    data object Home : Screen
    data class Results(val query: String) : Screen
    /** [searchText] is shown in the top search bar when the card was opened from search. */
    data class PlaceCard(val place: Place, val loading: Boolean, val searchText: String? = null) : Screen
    data object Route : Screen
    data object Saved : Screen
}

sealed interface MapCommand {
    data class MoveTo(val point: Point, val zoom: Float? = null) : MapCommand
    data class Fit(val box: BoundingBox) : MapCommand
    data class Follow(val point: Point, val heading: Float?) : MapCommand
    data object ZoomIn : MapCommand
    data object ZoomOut : MapCommand
    data object ResetNorth : MapCommand
}

/**
 * The map screen: which sheet is open, the place card, saved places, camera and layers.
 * Search, route planning and guidance live in [search], [route] and [nav];
 * this class connects them (a search result opens a card, a card opens a route...).
 */
class MapViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SavedPlacesStore(app)
    private val places = PlaceSearchService()
    private val routeService = RouteService()

    val commands = MutableSharedFlow<MapCommand>(extraBufferCapacity = 16)
    var message by mutableStateOf<String?>(null)

    val search = SearchController(places, store, searchBox = ::searchBox)
    val route = RouteController(
        routeService, commands,
        say = { message = it },
        openSearch = { slot -> search.open(slot = slot) },
    )
    val nav = NavigationController(route, NavStore(app), viewModelScope, commands, say = { message = it })

    // ---------- Navigation between screens ----------

    val stack = mutableStateListOf<Screen>(Screen.Home)
    val screen: Screen get() = stack.last()

    var layersOpen by mutableStateOf(false)

    // ---------- Travel times by car ----------

    var homeEta by mutableStateOf<DriveEta?>(null); private set
    var workEta by mutableStateOf<DriveEta?>(null); private set
    /** Car travel time to the place in the open card, keyed by place id. */
    var cardEta by mutableStateOf<Pair<String, DriveEta>?>(null); private set

    // ---------- Location & camera ----------

    var userLocation by mutableStateOf<Point?>(null); private set
    var hasLocationPermission by mutableStateOf(false); private set
    var following by mutableStateOf(false); private set
    var azimuth by mutableFloatStateOf(0f); private set
    /** Meters per screen pixel at the map center, for the scale ruler. */
    var metersPerPx by mutableFloatStateOf(0f)
    var lastCamera: CameraPosition? = null; private set
    private var centeredOnUser = false

    // ---------- Layers ----------

    var trafficOn by mutableStateOf(false); private set
    var trafficInfo by mutableStateOf<TrafficInfo?>(null)
    var mapLayer by mutableStateOf(MapLayerType.Scheme)
    var nightMode by mutableStateOf(NightMode.Auto)

    // ---------- Saved places ----------

    var bookmarks by mutableStateOf(store.bookmarks()); private set
    var home by mutableStateOf(store.home()); private set
    var work by mutableStateOf(store.work()); private set

    // Set by the map engine while the map is on screen.
    var visibleArea: (() -> Geometry?)? = null
    var visibleBox: (() -> BoundingBox?)? = null
    var currentZoom: (() -> Float)? = null

    private var detailToken = 0

    init {
        route.onLoaded = { result -> if (!nav.onRouteLoaded(result)) route.fit() }
        viewModelScope.launch { LocationRepository.fix.filterNotNull().collect(::onFix) }
    }

    // =====================================================================
    // Screens
    // =====================================================================

    val canGoBack: Boolean get() = search.isOpen || stack.size > 1 || nav.active

    fun onBack(): Boolean {
        when {
            search.isOpen -> search.close()
            nav.active -> nav.stop()
            stack.size > 1 -> pop()
            else -> return false
        }
        return true
    }

    private fun push(s: Screen) {
        stack.add(s)
    }

    private fun replaceTop(s: Screen) {
        stack[stack.lastIndex] = s
    }

    private fun pop() {
        val removed = stack.removeAt(stack.lastIndex)
        if (removed is Screen.Route) {
            route.clear()
            nav.cancelAutoStart()
            if (nav.active) nav.end()
        }
        if (removed is Screen.PlaceCard) detailToken++
        if (stack.none { it is Screen.Results }) search.clearResults()
    }

    fun goHome() {
        while (stack.size > 1) pop()
    }

    // =====================================================================
    // Search (the state is in [search]; here what a search opens)
    // =====================================================================

    fun submitSearch(text: String) {
        val q = text.trim()
        if (q.isEmpty()) return
        search.addHistory(q)
        val slot = search.pickSlot
        search.close()
        if (slot != null) {
            search.findPlace(q, visibleArea?.invoke(), onError = { message = it }) {
                route.setWaypoint(slot, it.toWaypoint())
            }
            return
        }
        if (screen is Screen.Results) replaceTop(Screen.Results(q)) else push(Screen.Results(q))
        runSearch(q, fit = true)
    }

    fun searchHere() {
        val q = (screen as? Screen.Results)?.query ?: return
        runSearch(q, fit = false)
    }

    private fun runSearch(q: String, fit: Boolean) {
        search.run(q, visibleArea?.invoke()) { found ->
            if (found.size == 1 && !found[0].isBusiness && screen is Screen.Results) {
                // A single address: show its card directly, like Yandex Maps does.
                showPlace(found[0], loading = false, move = true, replace = true)
                return@run
            }
            if (fit && found.isNotEmpty()) {
                Geo.boundsOf(found.map { it.point })?.let { commands.tryEmit(MapCommand.Fit(it)) }
            }
        }
    }

    fun onSuggestClick(s: SuggestUi) {
        val item = s.item
        val uri = item.uri
        when {
            item.action == SuggestItem.Action.SUBSTITUTE ->
                search.onQueryChange(item.displayText ?: s.title)

            uri != null && item.type != SuggestItem.Type.TRANSIT -> {
                search.addHistory(s.title)
                val slot = search.pickSlot
                search.close()
                if (slot != null) {
                    search.placeByUri(uri, onError = { message = it }) {
                        route.setWaypoint(slot, it.toWaypoint())
                    }
                } else {
                    val center = item.center
                    val placeholder = Place(
                        id = uri,
                        name = s.title,
                        lat = center?.latitude ?: 0.0,
                        lon = center?.longitude ?: 0.0,
                        address = s.subtitle,
                        uri = uri,
                    )
                    showPlace(placeholder, loading = true, move = center != null, replace = false, searchText = s.title)
                    loadDetails(placeholder, move = center == null)
                }
            }

            else -> submitSearch(item.searchText ?: s.title)
        }
    }

    private fun searchBox(): BoundingBox =
        visibleBox?.invoke() ?: Geo.boxAround(userLocation ?: DEFAULT_POINT, 15_000.0)

    // =====================================================================
    // Place card
    // =====================================================================

    fun onResultClick(place: Place) {
        showPlace(place, loading = false, move = true, replace = screen is Screen.PlaceCard)
    }

    fun onResultPinTap(index: Int) {
        val place = search.results.getOrNull(index) ?: return
        showPlace(place, loading = false, move = false, replace = screen is Screen.PlaceCard)
    }

    fun onPoiTap(geo: GeoObject) {
        if (screen is Screen.Route || nav.active) return
        val place = geo.toPlace(fromPoi = true) ?: return
        showPlace(place, loading = true, move = false, replace = screen is Screen.PlaceCard, searchText = null)
        loadDetails(place, move = false)
    }

    fun onLongTap(point: Point) {
        if (screen is Screen.Route || nav.active) return
        val place = Place(
            id = "pt:${point.latitude},${point.longitude}",
            name = "Point on the map",
            lat = point.latitude,
            lon = point.longitude,
            address = Geo.coordinatesText(point.latitude, point.longitude),
            isMapPoint = true,
        )
        showPlace(place, loading = true, move = false, replace = screen is Screen.PlaceCard, searchText = null)
        loadDetails(place, move = false)
    }

    fun onMapTap() {
        if (screen is Screen.PlaceCard) onBack()
    }

    fun openBookmarks() {
        if (screen != Screen.Saved) push(Screen.Saved)
    }

    fun openSaved(saved: SavedPlace) {
        val place = saved.toPlace()
        showPlace(place, loading = place.uri != null, move = true, replace = false, searchText = null)
        if (place.uri != null) loadDetails(place, move = false)
    }

    private fun showPlace(
        place: Place,
        loading: Boolean,
        move: Boolean,
        replace: Boolean,
        searchText: String? = (screen as? Screen.Results)?.query ?: (screen as? Screen.PlaceCard)?.searchText,
    ) {
        val s = Screen.PlaceCard(place, loading, searchText)
        if (replace) replaceTop(s) else push(s)
        detailToken++
        requestCardEta(place)
        if (move) {
            val zoom = max(currentZoom?.invoke() ?: 16f, 16f)
            commands.tryEmit(MapCommand.MoveTo(place.point, zoom))
        }
    }

    /** Loads organization details by URI, or the address of a point by reverse geocoding. */
    private fun loadDetails(base: Place, move: Boolean) {
        val token = detailToken
        val onError: (com.yandex.runtime.Error) -> Unit = {
            if (token == detailToken) applyDetails(base)
        }
        val onResult: (List<GeoObject>) -> Unit = { objects ->
            if (token == detailToken) {
                val found = objects.firstOrNull()?.toPlace(base.fromPoi)
                val merged = when {
                    found == null -> base
                    base.isMapPoint -> base.copy(
                        name = found.name,
                        address = found.address ?: base.address,
                        category = found.category,
                    )
                    else -> found.copy(fromPoi = base.fromPoi)
                }
                applyDetails(merged)
                if (move) commands.tryEmit(MapCommand.MoveTo(merged.point, 16f))
            }
        }
        val uri = base.uri
        if (uri != null && !base.isMapPoint) {
            places.byUri(KEY_DETAILS, uri, userLocation, onError, onResult)
        } else {
            places.address(KEY_DETAILS, base.point, onError, onResult)
        }
    }

    private fun applyDetails(place: Place) {
        val card = screen as? Screen.PlaceCard ?: return
        replaceTop(card.copy(place = place, loading = false))
    }

    private fun requestCardEta(place: Place) {
        val me = userLocation ?: return
        if (cardEta?.first == place.id) return
        cardEta = null
        if (Geo.distanceMeters(me, place.point) < 50) return
        routeService.requestDriveEta("card", me, place.point) { eta ->
            if (eta != null && (screen as? Screen.PlaceCard)?.place?.id == place.id) cardEta = place.id to eta
        }
    }

    /** Selected place pin; null when the place is a highlighted search result or a map POI. */
    val markerPlace: Place?
        get() {
            val card = screen as? Screen.PlaceCard ?: return null
            if (card.place.fromPoi) return null
            if (search.results.any { it.id == card.place.id }) return null
            return card.place
        }

    val selectedResultId: String?
        get() = (screen as? Screen.PlaceCard)?.place?.id

    val poiSelected: Boolean
        get() = (screen as? Screen.PlaceCard)?.place?.fromPoi == true

    // =====================================================================
    // Bookmarks, home & work
    // =====================================================================

    fun isBookmarked(place: Place) = bookmarks.any { it.matches(place) }

    fun toggleBookmark(place: Place) {
        bookmarks = if (isBookmarked(place)) {
            message = "Removed from Saved"
            bookmarks.filterNot { it.matches(place) }
        } else {
            message = "Added to Saved"
            listOf(place.toSaved()) + bookmarks
        }
        store.setBookmarks(bookmarks)
    }

    fun removeBookmark(saved: SavedPlace) {
        bookmarks = bookmarks - saved
        store.setBookmarks(bookmarks)
    }

    fun setHome(place: Place) {
        home = place.toSaved(); store.setHome(home); message = "Home saved"
        refreshSavedEtas()
    }

    fun setWork(place: Place) {
        work = place.toSaved(); store.setWork(work); message = "Work saved"
        refreshSavedEtas()
    }

    /** Car travel times shown on the "Home" and "Work" buttons. */
    fun refreshSavedEtas() {
        val me = userLocation ?: return
        home?.let { h -> routeService.requestDriveEta("home", me, Point(h.lat, h.lon)) { homeEta = it } }
        work?.let { w -> routeService.requestDriveEta("work", me, Point(w.lat, w.lon)) { workEta = it } }
    }

    private fun SavedPlace.matches(p: Place) =
        (uri != null && uri == p.uri) || (abs(lat - p.lat) < 1e-5 && abs(lon - p.lon) < 1e-5)

    // =====================================================================
    // Opening the route screen (the route itself is in [route])
    // =====================================================================

    fun routeTo(place: Place) = openRoute(from = myLocationWaypoint(), to = place.toWaypoint())

    fun routeFrom(place: Place) = openRoute(from = place.toWaypoint(), to = null)

    /** [title] replaces the stored name, so a route home reads "To: Home". */
    fun routeToSaved(saved: SavedPlace, title: String? = null) =
        routeTo(saved.toPlace().let { if (title != null) it.copy(name = title) else it })

    /** "Directions" in a meet-up: from my location to the meeting place. */
    fun routeToMeeting(m: Meeting) {
        goHome()
        openRoute(
            from = myLocationWaypoint(),
            to = Waypoint(name = m.title.ifBlank { m.placeName }, lat = m.lat, lon = m.lon),
        )
    }

    /** Route button next to the search field: from my location, destination to be chosen. */
    fun startRoutePlanning() = openRoute(from = myLocationWaypoint(), to = null)

    private fun openRoute(from: Waypoint?, to: Waypoint?, stops: List<Waypoint> = emptyList()) {
        nav.cancelAutoStart()
        if (screen !is Screen.Route) push(Screen.Route)
        route.set(from, to, stops)
        if (to == null) search.open(slot = WaypointSlot.To)
        else if (from == null) search.open(slot = WaypointSlot.From)
    }

    /** The app was closed in guidance: rebuild the route from here and go on. */
    private fun resumeNavigation(saved: SavedNav) {
        goHome()
        search.close()
        route.selectMode(saved.mode)
        openRoute(from = myLocationWaypoint(), to = saved.to, stops = saved.stops)
        nav.startWhenRouteArrives()
    }

    fun pickWaypoint(slot: WaypointSlot) = search.open(slot = slot)

    fun useMyLocationFor(slot: WaypointSlot) {
        val me = myLocationWaypoint()
        if (me == null) {
            message = "Your location is not known yet"
            return
        }
        search.close()
        route.setWaypoint(slot, me)
    }

    fun pickSaved(saved: SavedPlace, slot: WaypointSlot) {
        search.close()
        route.setWaypoint(slot, Waypoint(saved.name, saved.lat, saved.lon))
    }

    private fun myLocationWaypoint(): Waypoint? =
        userLocation?.let { Waypoint("Current location", it.latitude, it.longitude, isMyLocation = true) }

    private fun Place.toWaypoint() = Waypoint(
        name, lat, lon,
        categoryClass = categoryClass,
        category = category.takeIf { isBusiness },
    )

    // =====================================================================
    // Map controls & location
    // =====================================================================

    fun onPermissionResult(granted: Boolean) {
        hasLocationPermission = granted
        if (granted) LocationRepository.start()
    }

    private fun onFix(fix: LocationFix) {
        val first = !centeredOnUser
        centeredOnUser = true
        userLocation = fix.point
        nav.onFix(fix)
        val resume = nav.takePendingResume()
        if (resume != null) {
            resumeNavigation(resume)
            if (first) refreshSavedEtas()
            return
        }
        when {
            nav.active -> Unit // the guidance camera follows by itself
            following -> commands.tryEmit(MapCommand.MoveTo(fix.point))
            first && screen == Screen.Home -> commands.tryEmit(MapCommand.MoveTo(fix.point, 16f))
        }
        if (first) refreshSavedEtas()
    }

    /** Returns false when the location permission has to be requested first. */
    fun onMyLocationClick(): Boolean {
        if (!hasLocationPermission) return false
        val me = userLocation
        if (me == null) {
            message = "Finding your location..."
            return true
        }
        following = true
        commands.tryEmit(MapCommand.MoveTo(me, max(currentZoom?.invoke() ?: 16f, 16f)))
        return true
    }

    fun zoomIn() = commands.tryEmit(MapCommand.ZoomIn)
    fun zoomOut() = commands.tryEmit(MapCommand.ZoomOut)
    fun resetNorth() = commands.tryEmit(MapCommand.ResetNorth)

    fun toggleTraffic() {
        trafficOn = !trafficOn
    }

    fun onCameraChanged(position: CameraPosition, byUser: Boolean, finished: Boolean) {
        if (abs(position.azimuth - azimuth) > 0.5f) azimuth = position.azimuth
        if (byUser) {
            following = false
            if (nav.active) nav.pauseFollow()
            if (finished && screen is Screen.Results) search.onMapMoved()
        }
        if (finished) lastCamera = position
    }

    fun isDark(systemDark: Boolean) = when (nightMode) {
        NightMode.Auto -> systemDark
        NightMode.On -> true
        NightMode.Off -> false
    }

    override fun onCleared() {
        LocationRepository.stop()
        routeService.cancelAll()
        routeService.cancelEtas()
        places.cancelAll()
    }

    companion object {
        val DEFAULT_POINT = Point(40.3834, 71.7843)
        private const val KEY_DETAILS = "details"
    }
}
