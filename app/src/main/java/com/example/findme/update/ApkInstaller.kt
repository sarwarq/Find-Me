package com.example.findme.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.example.findme.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads an APK straight into a [PackageInstaller] session and commits it.
 *
 * Android then asks the user to confirm (and, the first time, to allow FindMe to install
 * apps); the outcome comes to [InstallResultReceiver]. Android itself refuses an APK signed
 * with a different key, so only builds signed with our upload key can replace the app.
 */
internal class ApkInstaller(private val context: Context) {

    /**
     * Downloads [release], checks its SHA-256 and hands it to Android.
     * [onProgress] gets 0..1 (or -1 when the size is unknown) on the main thread.
     * Cancelling the coroutine abandons the session; nothing is installed.
     */
    suspend fun install(release: ReleaseInfo, onProgress: (Float) -> Unit) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (release.size > 0) setSize(release.size)
        }
        val sessionId = installer.createSession(params)
        var committed = false
        try {
            installer.openSession(sessionId).use { session ->
                withContext(Dispatchers.IO) { download(release, session, onProgress) }
                session.commit(resultIntent(sessionId).intentSender)
                committed = true
            }
        } finally {
            if (!committed) runCatching { installer.abandonSession(sessionId) }
        }
    }

    private suspend fun download(release: ReleaseInfo, session: PackageInstaller.Session, onProgress: (Float) -> Unit) {
        val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("APK download: HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.size
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var shownPercent = -1
            conn.inputStream.use { input ->
                session.openWrite(APK_NAME, 0, if (total > 0) total else -1).use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        // At most ~100 UI updates for the whole download.
                        val percent = if (total > 0) (done * 100 / total).toInt() else -1
                        if (percent != shownPercent) {
                            shownPercent = percent
                            val p = if (total > 0) done.toFloat() / total else -1f
                            withContext(Dispatchers.Main) { onProgress(p) }
                        }
                    }
                    session.fsync(out)
                }
            }
            val sha = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha != release.sha256) throw IOException("APK checksum mismatch: got $sha, expected ${release.sha256}")
        } finally {
            conn.disconnect()
        }
    }

    private fun resultIntent(sessionId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        sessionId,
        Intent(context, InstallResultReceiver::class.java),
        // Mutable: Android adds the status and the confirmation intent to it.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    private companion object {
        const val APK_NAME = "findme.apk"
        const val BUFFER_SIZE = 64 * 1024
        const val TIMEOUT_MS = 30_000
    }
}

/** Gets the install result from Android (not exported: only our PendingIntent reaches it). */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.container.updates.onInstallResult(intent)
    }
}
