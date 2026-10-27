package com.example.findme

import android.app.Application
import android.content.Context
import com.yandex.mapkit.MapKitFactory

/** Starts MapKit and holds the [AppContainer]; runs before any screen or service. */
class FindMeApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapKitFactory.setApiKey(MAPKIT_API_KEY)
        MapKitFactory.setLocale("en_US")
        MapKitFactory.initialize(this)
        container = AppContainer(this)
    }

    private companion object {
        const val MAPKIT_API_KEY = "d4790a09-6211-4912-a465-5b5dcc361628"
    }
}

/** The app's [AppContainer] from any activity or service. */
val Context.container: AppContainer get() = (applicationContext as FindMeApp).container
