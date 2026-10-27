package com.example.findme.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.core.net.toUri
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings

/** Whether this app version may be used. */
sealed interface UpdateState {
    data object Ok : UpdateState

    /** Below `min_version_code`: the app is blocked until updated. */
    data class Required(val message: String) : UpdateState

    /** Google Play has a newer version; the user may update now or later. */
    data class Optional(val versionCode: Int) : UpdateState
}

/**
 * Forced and optional updates.
 *
 * - Firebase Remote Config `min_version_code`: versions below it see a blocking
 *   "Update required" screen (set it in the console when an old version must stop working).
 * - Google Play In-App Updates: the "Update" button runs Play's full-screen update; an app not
 *   installed from Play (e.g. by adb) opens the store page instead.
 *
 * This is the user-facing layer only. A patched APK can skip it, so anything that must be
 * enforced has to be checked on the server too (Play Integrity / App Check, planned).
 *
 * Create it in `onCreate` (it registers an activity result) and call [onResume].
 */
class UpdateGate(private val activity: ComponentActivity) {

    var state by mutableStateOf<UpdateState>(UpdateState.Ok); private set

    private val playUpdates: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private val prefs = activity.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val versionCode: Long = currentVersionCode(activity)

    private val launcher: ActivityResultLauncher<IntentSenderRequest> =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // Cancelled or failed: a required update keeps the app blocked; check again.
            if (result.resultCode != Activity.RESULT_OK) checkPlay()
        }

    private val remoteConfig: FirebaseRemoteConfig? =
        if (FirebaseApp.getApps(activity).isNotEmpty()) FirebaseRemoteConfig.getInstance() else null

    init {
        remoteConfig?.apply {
            // Debug builds fetch every time, so a changed value can be tested right away.
            val debuggable = (activity.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = if (debuggable) 0 else FETCH_INTERVAL_SECONDS },
            )
            setDefaultsAsync(mapOf(KEY_MIN_VERSION to 0L, KEY_MESSAGE to DEFAULT_MESSAGE))
        }
        // The last fetched values work offline too.
        applyRemote()
        check()
    }

    /** Fetches the newest minimum version, then asks Google Play about updates. */
    fun check() {
        val rc = remoteConfig
        if (rc == null) {
            checkPlay()
            return
        }
        rc.fetchAndActivate().addOnCompleteListener {
            applyRemote()
            checkPlay()
        }
    }

    /** Coming back to the app: resume an interrupted forced update, re-check a blocked app. */
    fun onResume() {
        playUpdates.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) startPlayUpdate(info)
        }
        if (state is UpdateState.Required) check()
    }

    /** "Update" button. */
    fun update() {
        playUpdates.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                    info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                ) {
                    startPlayUpdate(info)
                } else {
                    openStore()
                }
            }
            .addOnFailureListener { openStore() }
    }

    /** "Later" on an optional update: don't ask again for this version. */
    fun later() {
        (state as? UpdateState.Optional)?.let { prefs.edit { putInt(KEY_DISMISSED, it.versionCode) } }
        state = UpdateState.Ok
    }

    private fun applyRemote() {
        val rc = remoteConfig ?: return
        val min = rc.getLong(KEY_MIN_VERSION)
        if (versionCode < min) {
            state = UpdateState.Required(rc.getString(KEY_MESSAGE).ifBlank { DEFAULT_MESSAGE })
        } else if (state is UpdateState.Required) {
            state = UpdateState.Ok
        }
    }

    private fun checkPlay() {
        playUpdates.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return@addOnSuccessListener
                when (state) {
                    // Blocked anyway: start Play's update right away.
                    is UpdateState.Required -> if (info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) startPlayUpdate(info)
                    UpdateState.Ok -> if (prefs.getInt(KEY_DISMISSED, 0) != info.availableVersionCode()) {
                        state = UpdateState.Optional(info.availableVersionCode())
                    }
                    is UpdateState.Optional -> Unit
                }
            }
            // Not installed from Google Play, no Play Store, offline: nothing to offer.
            .addOnFailureListener { Log.i(TAG, "Play update check unavailable: ${it.message}") }
    }

    private fun startPlayUpdate(info: AppUpdateInfo) {
        try {
            playUpdates.startUpdateFlowForResult(info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build())
        } catch (e: Exception) {
            Log.w(TAG, "Play update failed to start", e)
            openStore()
        }
    }

    private fun openStore() {
        val pkg = activity.packageName
        try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$pkg".toUri()))
        } catch (_: ActivityNotFoundException) {
            try {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$pkg".toUri()),
                )
            } catch (_: ActivityNotFoundException) {
                Log.w(TAG, "No app to open the store")
            }
        }
    }

    private companion object {
        const val TAG = "UpdateGate"
        const val KEY_MIN_VERSION = "min_version_code"
        const val KEY_MESSAGE = "update_message"
        const val KEY_DISMISSED = "dismissed_version"
        const val DEFAULT_MESSAGE = "This version of FindMe is no longer supported. Please update to keep using the app."
        /** Remote Config refetches at most this often (Firebase limits frequent fetches). */
        const val FETCH_INTERVAL_SECONDS = 60L * 60

        fun currentVersionCode(context: Context): Long {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
        }
    }
}
