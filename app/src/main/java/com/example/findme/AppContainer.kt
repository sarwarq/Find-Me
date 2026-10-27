package com.example.findme

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.findme.data.NavStore
import com.example.findme.data.SavedPlacesStore
import com.example.findme.data.auth.AuthRepository
import com.example.findme.data.location.LocationRepository
import com.example.findme.data.meet.MeetingRepository
import com.example.findme.maps.PlaceSearchService
import com.example.findme.maps.RouteService
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.meet.MeetViewModel
import com.example.findme.ui.nav.Voice
import com.example.findme.ui.profile.AuthViewModel

/**
 * Creates the app's long-lived objects once and hands them to whoever needs them
 * (manual dependency injection, no framework). Anything that holds data or a connection
 * is created here and nowhere else.
 */
class AppContainer(private val app: Application) {

    // ---------- One for the whole app ----------

    val savedPlaces by lazy { SavedPlacesStore(app) }
    val navStore by lazy { NavStore(app) }
    val meetings by lazy { MeetingRepository(app) }
    val auth by lazy { AuthRepository(app) }
    val location: LocationRepository get() = LocationRepository
    val voice by lazy { Voice(app) }

    // ---------- New for each screen ----------
    // MapKit requests are cancelled together with the screen that made them.

    fun newRouteService() = RouteService()
    fun newPlaceSearch() = PlaceSearchService()

    /** Builds every ViewModel of the app with its dependencies. */
    val viewModelFactory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            MapViewModel(
                store = savedPlaces,
                navStore = navStore,
                places = newPlaceSearch(),
                routeService = newRouteService(),
                location = location,
                voice = voice,
            )
        }
        initializer { MeetViewModel(app, meetings, navStore, location) }
        initializer { AuthViewModel(auth) }
    }
}
