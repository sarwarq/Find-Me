package com.example.findme.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.findme.data.Geo
import com.example.findme.data.Place
import com.example.findme.data.SavedPlacesStore
import com.example.findme.data.SuggestUi
import com.example.findme.data.WaypointSlot
import com.example.findme.data.location.LocationRepository
import com.example.findme.maps.PlaceSearchService
import com.example.findme.maps.toPlace
import com.example.findme.maps.toUserMessage
import com.yandex.mapkit.geometry.BoundingBox
import com.yandex.mapkit.geometry.Geometry
import com.yandex.mapkit.search.SuggestItem

/** The search field, its suggestions, the list of results and the search history. */
class SearchController(
    private val places: PlaceSearchService,
    private val store: SavedPlacesStore,
    private val location: LocationRepository,
    /** Where to look: the visible map, or around me. */
    private val searchBox: () -> BoundingBox,
) {
    /** The full-screen search with suggestions is open. */
    var isOpen by mutableStateOf(false); private set
    /** Set when the search chooses a place for a route instead of showing it. */
    var pickSlot by mutableStateOf<WaypointSlot?>(null); private set
    var query by mutableStateOf(""); private set
    var suggestions by mutableStateOf(emptyList<SuggestUi>()); private set

    var results by mutableStateOf(emptyList<Place>()); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    /** The map moved away from the results: offer "Search here". */
    var showSearchHere by mutableStateOf(false); private set

    var history by mutableStateOf(store.history()); private set

    private val me get() = location.fix.value?.point

    fun open(prefill: String = "", slot: WaypointSlot? = null) {
        pickSlot = slot
        isOpen = true
        onQueryChange(prefill)
    }

    fun close() {
        isOpen = false
        pickSlot = null
        suggestions = emptyList()
        places.resetSuggest()
    }

    fun onQueryChange(text: String) {
        query = text
        if (text.isBlank()) {
            suggestions = emptyList()
            places.resetSuggest()
            return
        }
        places.suggest(text, searchBox(), me) { response ->
            suggestions = response.items.take(12).map { it.toUi() }
        }
    }

    /** Looks for [q] in [area] (or around); [onResult] gets the places when they arrive. */
    fun run(q: String, area: Geometry?, onResult: (List<Place>) -> Unit) {
        results = emptyList()
        loading = true
        error = null
        showSearchHere = false
        places.search(
            KEY_RESULTS, q, area ?: Geometry.fromBoundingBox(searchBox()), me,
            onError = {
                loading = false
                error = it.toUserMessage()
            },
        ) { objects ->
            loading = false
            val found = objects.mapNotNull { it.toPlace() }
            results = found
            error = if (found.isEmpty()) "Nothing found" else null
            onResult(found)
        }
    }

    /** The results list closed. */
    fun clearResults() {
        places.cancel(KEY_RESULTS)
        results = emptyList()
        error = null
        showSearchHere = false
    }

    /** The user moved the map over the results. */
    fun onMapMoved() {
        if (!loading) showSearchHere = true
    }

    /** The first place for [q], to put on a route. */
    fun findPlace(q: String, area: Geometry?, onError: (String) -> Unit, onFound: (Place) -> Unit) {
        places.search(
            KEY_PICK, q, area ?: Geometry.fromBoundingBox(searchBox()), me,
            onError = { onError(it.toUserMessage()) },
        ) { objects ->
            val place = objects.firstOrNull()?.toPlace()
            if (place == null) onError("Nothing found") else onFound(place)
        }
    }

    /** The place behind a suggestion, to put on a route. */
    fun placeByUri(uri: String, onError: (String) -> Unit, onFound: (Place) -> Unit) {
        places.byUri(KEY_PICK, uri, me, onError = { onError(it.toUserMessage()) }) { objects ->
            objects.firstOrNull()?.toPlace()?.let(onFound)
        }
    }

    fun addHistory(q: String) {
        history = (listOf(q) + history.filterNot { it.equals(q, ignoreCase = true) }).take(15)
        store.setHistory(history)
    }

    fun clearHistory() {
        history = emptyList()
        store.setHistory(history)
    }

    private fun SuggestItem.toUi(): SuggestUi = SuggestUi(
        title = title.text,
        subtitle = subtitle?.text,
        // MapKit's own text follows the locale (miles for en_US); the app always shows kilometers.
        distance = center?.let { Geo.distanceText(me, it) } ?: distance?.text,
        type = type,
        item = this,
    )

    private companion object {
        const val KEY_RESULTS = "results"
        const val KEY_PICK = "pick"
    }
}
