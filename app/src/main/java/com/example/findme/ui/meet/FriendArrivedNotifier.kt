package com.example.findme.ui.meet

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.findme.MainActivity
import com.example.findme.R

/** "Anna arrived": a friend got to the meeting place (a setting can switch it off). */
object FriendArrivedNotifier {
    private const val CHANNEL_ID = "meetups"

    fun notify(context: Context, uid: String, name: String, place: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Meet-ups", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a friend arrives at the meeting place"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ms_group)
            .setContentTitle("$name arrived")
            .setContentText("$name is at $place")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // One notification per friend: arriving again replaces the old one.
        nm.notify("arrived:$uid".hashCode(), n)
    }
}
