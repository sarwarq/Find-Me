package com.example.findme.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.findme.MainActivity
import com.example.findme.R
import com.example.findme.data.meet.MeetingRepository
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The meeting whose members currently see my location. */
data class Sharing(
    val meetingId: String,
    val title: String,
    val destLat: Double,
    val destLon: Double,
    val startedMillis: Long,
)

/**
 * Sends my location to one meeting while I'm on my way. Runs as a foreground service with
 * a notification (the user always sees that sharing is on, and can stop it from there).
 *
 * Stops by itself on arrival (closer than [ARRIVE_METERS] to the place) and after [MAX_MILLIS].
 * Writes at most every [MIN_WRITE_MILLIS], or every [IDLE_WRITE_MILLIS] when standing still.
 */
class LocationShareService : Service() {

    private lateinit var repo: MeetingRepository
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var sharing: Sharing? = null
    private var uid: String? = null
    private var lastWrite: Location? = null
    private var lastWriteMillis = 0L

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let(::onLocation)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = MeetingRepository(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val next = Sharing(
                    meetingId = intent.getStringExtra(EXTRA_MEETING) ?: return stopNow(),
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    destLat = intent.getDoubleExtra(EXTRA_LAT, 0.0),
                    destLon = intent.getDoubleExtra(EXTRA_LON, 0.0),
                    startedMillis = System.currentTimeMillis(),
                )
                start(next)
            }
            ACTION_STOP -> {
                stopSharing(removeFromMap = true)
            }
            // Restarted by the system without an intent: don't track the user unexpectedly.
            else -> return stopNow()
        }
        return START_NOT_STICKY
    }

    private fun start(next: Sharing) {
        val user = if (FirebaseApp.getApps(this).isNotEmpty()) FirebaseAuth.getInstance().currentUser else null
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (user == null || !hasPermission) {
            stopNow()
            return
        }

        // Switching to another meeting: disappear from the previous one.
        sharing?.takeIf { it.meetingId != next.meetingId }?.let { old -> uid?.let { repo.removeLocation(old.meetingId, it) } }

        sharing = next
        uid = user.uid
        lastWrite = null
        lastWriteMillis = 0L
        _state.value = next

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(next),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } catch (e: Exception) {
            // E.g. started from the background on Android 12+, or permission revoked meanwhile.
            Log.w(TAG, "startForeground failed", e)
            stopNow()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_MILLIS)
            .setMinUpdateIntervalMillis(MIN_WRITE_MILLIS)
            .build()
        try {
            fused.removeLocationUpdates(callback)
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.w(TAG, "No location permission", e)
            stopNow()
        }
    }

    private fun onLocation(loc: Location) {
        val s = sharing ?: return
        val me = uid ?: return
        val now = System.currentTimeMillis()

        if (now - s.startedMillis > MAX_MILLIS) {
            stopSharing(removeFromMap = true)
            return
        }

        val dest = Location("dest").apply { latitude = s.destLat; longitude = s.destLon }
        val arrived = loc.distanceTo(dest) < ARRIVE_METERS && loc.accuracy < ARRIVE_ACCURACY

        val prev = lastWrite
        val moved = prev == null || loc.distanceTo(prev) > MIN_MOVE_METERS
        val due = now - lastWriteMillis > IDLE_WRITE_MILLIS
        if (!arrived && !(moved && now - lastWriteMillis >= MIN_WRITE_MILLIS) && !due) return

        repo.shareLocation(
            meetingId = s.meetingId,
            uid = me,
            lat = loc.latitude,
            lon = loc.longitude,
            accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
            heading = if (loc.hasBearing() && loc.speed > 1f) loc.bearing else null,
            speed = if (loc.hasSpeed()) loc.speed else null,
            arrived = arrived,
            etaSeconds = navProgress?.first,
            remainingMeters = navProgress?.second,
        )
        lastWrite = loc
        lastWriteMillis = now

        if (arrived) {
            // Keep the "arrived" point on the others' maps, but stop following me.
            notifyArrived(s)
            stopSharing(removeFromMap = false)
        }
    }

    private fun stopSharing(removeFromMap: Boolean) {
        val s = sharing
        val me = uid
        if (removeFromMap && s != null && me != null) repo.removeLocation(s.meetingId, me)
        stopNow()
    }

    private fun stopNow(): Int {
        fused.removeLocationUpdates(callback)
        sharing = null
        _state.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        _state.value = null
        super.onDestroy()
    }

    // ---------- Notifications ----------

    private fun notification(s: Sharing): Notification {
        ensureChannel(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, LocationShareService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_group)
            .setContentTitle("Sharing your location")
            .setContentText("Members of \"${s.title}\" see you on the map")
            .setContentIntent(open)
            .addAction(R.drawable.ms_close, "Stop", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    private fun notifyArrived(s: Sharing) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(this)
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_group)
            .setContentTitle("You arrived")
            .setContentText("Location sharing for \"${s.title}\" stopped")
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ARRIVED_ID, n)
    }

    companion object {
        private const val TAG = "LocationShare"
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 42
        private const val ARRIVED_ID = 43
        private const val ACTION_START = "com.example.findme.START_SHARING"
        private const val ACTION_STOP = "com.example.findme.STOP_SHARING"
        private const val EXTRA_MEETING = "meeting"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LON = "lon"

        private const val UPDATE_MILLIS = 5_000L
        private const val MIN_WRITE_MILLIS = 5_000L
        private const val IDLE_WRITE_MILLIS = 30_000L
        private const val MIN_MOVE_METERS = 15f
        private const val ARRIVE_METERS = 60f
        private const val ARRIVE_ACCURACY = 100f
        private const val MAX_MILLIS = 4 * 60 * 60 * 1000L

        private val _state = MutableStateFlow<Sharing?>(null)

        /** Which meeting I'm sharing my location with, or null. */
        val state: StateFlow<Sharing?> = _state.asStateFlow()

        /** Remaining time (s) and distance (m) from my navigation, sent with my position; null when not navigating. */
        @Volatile
        var navProgress: Pair<Double, Double>? = null

        /** Call while the app is on screen: Android allows starting location tracking only then. */
        fun start(context: Context, meetingId: String, title: String, destLat: Double, destLon: Double) {
            val intent = Intent(context, LocationShareService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_MEETING, meetingId)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_LAT, destLat)
                .putExtra(EXTRA_LON, destLon)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            if (_state.value == null) return
            context.startService(Intent(context, LocationShareService::class.java).setAction(ACTION_STOP))
        }

        private fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Location sharing", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shown while meet-up members see your location"
                },
            )
        }
    }
}
