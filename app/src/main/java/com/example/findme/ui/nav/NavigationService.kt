package com.example.findme.ui.nav

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.findme.MainActivity
import com.example.findme.R
import com.example.findme.data.Geo
import com.example.findme.data.Maneuver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.math.roundToInt

/**
 * Keeps guidance going with the screen off or another app open, like Organic Maps'
 * `NavigationService`: a foreground service with a notification showing the next turn.
 *
 * The guidance itself stays in [NavigationController]; this service only keeps the process
 * alive and allowed to use the location. MapKit is kept running meanwhile (see MainActivity).
 */
class NavigationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, build(this, notice), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
                )
            } catch (e: Exception) {
                // E.g. location permission revoked meanwhile: guidance works on screen only.
                Log.w(TAG, "startForeground failed", e)
                stopSelf()
            }
            ACTION_STOP -> {
                // No guidance listens any more (the screen is gone): just go away.
                if (_stopRequests.subscriptionCount.value == 0) stopSelf() else _stopRequests.tryEmit(Unit)
            }
            // Restarted by the system without an intent: the guidance it served is gone.
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    /** What the notification says. */
    data class Notice(val title: String, val text: String)

    companion object {
        private const val TAG = "NavigationService"
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 44
        private const val ACTION_START = "com.example.findme.START_NAVIGATION"
        private const val ACTION_STOP = "com.example.findme.STOP_NAVIGATION"

        @Volatile private var running = false
        @Volatile private var notice = Notice("Navigation", "")

        private val _stopRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        /** "Stop" pressed in the notification. */
        val stopRequests: SharedFlow<Unit> = _stopRequests.asSharedFlow()

        /**
         * Shows [next] (starting the service the first time). The first call must come while
         * the app is on screen: Android lets a location service start only then.
         */
        fun show(context: Context, next: Notice) {
            if (next == notice && running) return
            notice = next
            if (running) {
                context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, build(context, next))
                return
            }
            try {
                ContextCompat.startForegroundService(
                    context, Intent(context, NavigationService::class.java).setAction(ACTION_START),
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not start", e) // in the background: guidance goes on while the app is open
            }
        }

        fun hide(context: Context) {
            context.stopService(Intent(context, NavigationService::class.java))
        }

        /** "300 m · Turn right" / "onto Navoi Avenue · 12 km, 15 min left". */
        fun noticeFor(p: NavProgress?, rerouting: Boolean): Notice {
            if (rerouting) return Notice("Recalculating the route", p?.let(::left).orEmpty())
            if (p == null) return Notice("Navigation", "")
            val action = VoicePrompter.actionText(p.next)?.replaceFirstChar { it.uppercase() } ?: "Go straight"
            val title = if (p.next == Maneuver.Finish) "Destination in ${Geo.formatDistance(p.nextInMeters)}"
            else "${Geo.formatDistance(p.nextInMeters)} · $action"
            val onto = p.nextStreet?.takeIf { p.next != Maneuver.Finish }?.let { "onto $it · " }.orEmpty()
            return Notice(title, onto + left(p))
        }

        private fun left(p: NavProgress): String {
            val min = (p.remainingSeconds / 60).roundToInt().coerceAtLeast(1)
            val time = if (min < 60) "$min min" else "${min / 60} h ${min % 60} min"
            return "${Geo.formatDistance(p.remainingMeters)}, $time left"
        }

        private fun build(context: Context, n: Notice): Notification {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Navigation", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "The next turn while guidance runs in the background"
                    },
                )
            }
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val stop = PendingIntent.getService(
                context, 2,
                Intent(context, NavigationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ms_navigation)
                .setContentTitle(n.title)
                .setContentText(n.text)
                .setContentIntent(open)
                .addAction(R.drawable.ms_close, "Stop", stop)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .build()
        }
    }
}
