package com.example.findme.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Kilometers and km/h, or miles and mph. */
enum class Units(val title: String) { Metric("Kilometers"), Imperial("Miles") }

/**
 * The app's settings, remembered between launches. Each one is Compose state, so the screens
 * that read it redraw when it changes.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ---------- Navigation ----------

    /** Spoken guidance (the sound button in guidance turns the same switch). */
    var voice by pref(true)
    /** Say and show the cameras ahead. */
    var cameraAlerts by pref(true)
    /** Beep this many km/h over the limit; 0 = never. */
    var speedingMarginKmh by pref(10)
    var avoidTolls by pref(false)
    var avoidUnpaved by pref(false)

    // ---------- Map ----------

    var nightMode by pref(NightMode.Auto) { NightMode.entries.firstOrNull { e -> e.name == it } }
    var units by pref(Units.Metric) { Units.entries.firstOrNull { e -> e.name == it } }

    // ---------- Meet-ups ----------

    /** Draw friends' routes on the map. */
    var friendRoutes by pref(true)
    /** A notification when a friend arrives at the meeting place. */
    var friendArrived by pref(true)

    // ---------- Plumbing ----------

    private fun pref(default: Boolean) = Pref({ k -> getBoolean(k, default) }, { k, v -> putBoolean(k, v) })
    private fun pref(default: Int) = Pref({ k -> getInt(k, default) }, { k, v -> putInt(k, v) })
    private fun <E : Enum<E>> pref(default: E, parse: (String) -> E?) = Pref(
        { k -> getString(k, null)?.let(parse) ?: default },
        { k, v -> putString(k, v.name) },
    )

    /** One setting, kept under its property's name: read once, written back on every change. */
    private inner class Pref<T>(
        private val read: SharedPreferences.(String) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) : ReadWriteProperty<Any?, T> {
        private var state: MutableState<T>? = null

        private fun state(key: String) = state ?: mutableStateOf(prefs.read(key)).also { state = it }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state(property.name).value

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            state(property.name).value = value
            prefs.edit { write(property.name, value) }
        }
    }
}
