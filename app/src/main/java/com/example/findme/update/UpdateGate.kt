package com.example.findme.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import com.example.findme.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Whether this app version may be used. */
sealed interface UpdateState {
    data object Ok : UpdateState

    /** A newer release is out, or this version is below `min_version_code`: blocked until updated. */
    data class Required(val message: String) : UpdateState
}

/** The update download, shown on the update screen. */
sealed interface DownloadState {
    data object Idle : DownloadState

    /** [progress] is 0..1, or -1 while the size is unknown. */
    data class Downloading(val progress: Float) : DownloadState

    /** Handed to Android, waiting for the user to confirm the install. */
    data object Installing : DownloadState

    data class Failed(val message: String) : DownloadState
}

/**
 * Forced updates, without Google Play: only the newest version may be used.
 *
 * - GitHub Releases: every release has an `update.json` (see [ReleaseInfo]). A newer
 *   `versionCode` there replaces the app with the blocking "Update required" screen; "Update"
 *   downloads the APK and installs it through [ApkInstaller]. The newest known version is
 *   remembered, so the app stays blocked when opened offline.
 * - Firebase Remote Config `min_version_code`: blocks the same way; a kill switch that works
 *   even if GitHub can't be reached.
 *
 * Debug builds never look at GitHub: they are signed with the debug key, and Android
 * refuses to replace them with a release APK.
 *
 * This is the user-facing layer only. A patched APK can skip it, so anything that must be
 * enforced has to be checked on the server too (App Check, planned).
 *
 * One for the whole app (in [com.example.findme.AppContainer]), so a download survives the
 * activity being recreated. The activity calls [onResume] and [onPause].
 */
class UpdateGate(context: Context) {

    var state by mutableStateOf<UpdateState>(UpdateState.Ok); private set
    var download by mutableStateOf<DownloadState>(DownloadState.Idle); private set

    private val app = context.applicationContext
    private val scope = MainScope()
    private val prefs = app.getSharedPreferences("update", Context.MODE_PRIVATE)
    private val versionCode: Long = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
    private val installer = ApkInstaller(app)

    /** `update.json` of the newest release; null when this build doesn't take updates from GitHub. */
    private val latestUrl: String? = BuildConfig.UPDATE_REPO
        .takeIf { it.isNotBlank() && !BuildConfig.DEBUG }
        ?.let { "https://github.com/$it/releases/latest/download/update.json" }

    private val remoteConfig: FirebaseRemoteConfig? =
        if (FirebaseApp.getApps(app).isNotEmpty()) FirebaseRemoteConfig.getInstance() else null

    private var minVersion = 0L
    private var requiredMessage = DEFAULT_MESSAGE

    private var lastCheck = 0L
    private var checkJob: Job? = null
    private var installJob: Job? = null

    /** The visible activity, to show Android's install confirmation from. */
    private var resumed: Activity? = null
    private var pendingConfirm: Intent? = null

    init {
        remoteConfig?.apply {
            // Debug builds fetch every time, so a changed value can be tested right away.
            val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            setConfigSettingsAsync(
                remoteConfigSettings { minimumFetchIntervalInSeconds = if (debuggable) 0 else FETCH_INTERVAL_SECONDS },
            )
            setDefaultsAsync(mapOf(KEY_MIN_VERSION to 0L, KEY_MESSAGE to DEFAULT_MESSAGE))
        }
        // The last fetched values work offline too.
        readRemote()
        refresh()
        check()
    }

    /** Fetches the newest minimum version and the newest GitHub release. */
    fun check() {
        lastCheck = SystemClock.elapsedRealtime()
        remoteConfig?.fetchAndActivate()?.addOnCompleteListener {
            readRemote()
            refresh()
        }
        if (latestUrl != null && checkJob?.isActive != true) {
            checkJob = scope.launch { fetchLatest().onSuccess(::remember) }
        }
    }

    /** Coming back to the app: show a pending install confirmation, re-check now and then. */
    fun onResume(activity: Activity) {
        resumed = activity
        launchConfirm()
        if (state is UpdateState.Required || SystemClock.elapsedRealtime() - lastCheck > RECHECK_MS) check()
    }

    fun onPause(activity: Activity) {
        if (resumed === activity) resumed = null
    }

    /** "Update" button: download the newest release and install it. */
    fun update() {
        if (installJob?.isActive == true || download == DownloadState.Installing) return
        installJob = scope.launch {
            download = DownloadState.Downloading(-1f)
            val release = latestUrl?.let { fetchLatest().onSuccess(::remember).getOrNull() }
            if (release == null || release.versionCode <= versionCode) {
                download = DownloadState.Failed(
                    when {
                        latestUrl == null -> "This build can't update itself. Install FindMe from GitHub."
                        release == null -> "Could not check for updates. Check the internet connection."
                        else -> "No newer version is published yet. Try again later."
                    },
                )
                return@launch
            }
            try {
                installer.install(release) { download = DownloadState.Downloading(it) }
                // Android answers in onInstallResult: confirmation, success or failure.
                download = DownloadState.Installing
            } catch (e: CancellationException) {
                download = DownloadState.Idle
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                download = DownloadState.Failed("Download failed. Check the internet connection and try again.")
            }
        }
    }

    /** "Cancel" during the download. */
    fun cancel() {
        installJob?.cancel()
    }

    /** From [InstallResultReceiver]: what Android did with the committed APK. */
    internal fun onInstallResult(intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                pendingConfirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                download = DownloadState.Installing
                launchConfirm()
            }
            // On success Android restarts the app right away; this is rarely seen.
            PackageInstaller.STATUS_SUCCESS -> download = DownloadState.Idle
            // The user said no; they can tap "Update" again.
            PackageInstaller.STATUS_FAILURE_ABORTED -> download = DownloadState.Idle
            else -> {
                Log.w(TAG, "Install failed: $status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
                download = DownloadState.Failed(installError(status))
            }
        }
    }

    private fun launchConfirm() {
        // In the background: shown the next time the app is opened (onResume).
        val activity = resumed ?: return
        val confirm = pendingConfirm ?: return
        pendingConfirm = null
        try {
            activity.startActivity(confirm)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No install confirmation screen", e)
            download = DownloadState.Failed("Could not install the update.")
        }
    }

    private fun readRemote() {
        val rc = remoteConfig ?: return
        minVersion = rc.getLong(KEY_MIN_VERSION)
        requiredMessage = rc.getString(KEY_MESSAGE).ifBlank { DEFAULT_MESSAGE }
    }

    /** Stores what GitHub says is newest (null: no release), so the gate holds offline too. */
    private fun remember(release: ReleaseInfo?) {
        prefs.edit {
            if (release == null) {
                remove(KEY_LATEST_CODE).remove(KEY_LATEST_NAME)
            } else {
                putLong(KEY_LATEST_CODE, release.versionCode).putString(KEY_LATEST_NAME, release.versionName)
            }
        }
        refresh()
    }

    private fun refresh() {
        state = when {
            versionCode < minVersion -> UpdateState.Required(requiredMessage)
            prefs.getLong(KEY_LATEST_CODE, 0) > versionCode -> UpdateState.Required(
                "FindMe ${prefs.getString(KEY_LATEST_NAME, "")} is out. Update to keep using the app.",
            )
            else -> UpdateState.Ok
        }
    }

    /**
     * The newest release: success(null) if none is published yet, failure if the network is down
     * or the file is broken (then the last known newest version stays in effect).
     */
    private suspend fun fetchLatest(): Result<ReleaseInfo?> {
        val url = latestUrl ?: return Result.success(null)
        return withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
            }
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_OK ->
                        Result.success(ReleaseInfo.parse(conn.inputStream.bufferedReader().use { it.readText() }))
                    // No release published yet.
                    HttpURLConnection.HTTP_NOT_FOUND -> Result.success(null)
                    else -> throw IOException("HTTP $code")
                }
            } catch (e: Exception) {
                Log.i(TAG, "Update check failed: $e")
                Result.failure(e)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun installError(status: Int): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "This copy of FindMe was signed with a different key. Uninstall it and install FindMe from GitHub."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough free space for the update."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installing apps is blocked on this phone."
        else -> "Could not install the update."
    }

    private companion object {
        const val TAG = "UpdateGate"
        const val KEY_MIN_VERSION = "min_version_code"
        const val KEY_MESSAGE = "update_message"
        /** The newest release seen on GitHub. (Older versions stored "dismissed_release" for "Later".) */
        const val KEY_LATEST_CODE = "latest_release"
        const val KEY_LATEST_NAME = "latest_release_name"
        const val DEFAULT_MESSAGE = "This version of FindMe is no longer supported. Please update to keep using the app."
        /** Remote Config refetches at most this often (Firebase limits frequent fetches). */
        const val FETCH_INTERVAL_SECONDS = 60L * 60
        /** Look for a new release again when the app comes back after this long. */
        const val RECHECK_MS = 30 * 60 * 1000L
        const val TIMEOUT_MS = 15_000
    }
}
