package com.example.findme.ui.profile

import com.example.findme.ui.theme.YColors
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.findme.data.auth.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Round avatar: the Google photo, or the first letter of the name on a color picked from the uid. */
@Composable
fun Avatar(user: UserProfile, size: Dp, modifier: Modifier = Modifier) {
    val photo by produceState(PhotoCache.get(user.photoUrl), user.photoUrl) {
        if (value == null) value = PhotoCache.load(user.photoUrl)
    }
    Box(
        modifier.size(size).clip(CircleShape).background(avatarColor(user.uid)),
        contentAlignment = Alignment.Center,
    ) {
        val image = photo
        if (image != null) {
            Image(image, user.name, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        } else {
            Text(
                user.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}

private val AvatarColors = YColors.Friends

fun avatarColor(uid: String): Color = AvatarColors[Math.floorMod(uid.hashCode(), AvatarColors.size)]

/** Tiny in-memory photo cache: there are only a few avatars, a full image library is not needed. */
object PhotoCache {
    private val cache = LruCache<String, ImageBitmap>(32)

    fun get(url: String?): ImageBitmap? = url?.let { cache.get(it) }

    suspend fun load(url: String?): ImageBitmap? {
        if (url.isNullOrBlank()) return null
        get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            try {
                // Google photo URLs end with "=s96-c"; ask for a sharper one.
                val sharp = url.replace(Regex("=s\\d+-c$"), "=s256-c")
                val conn = URL(sharp).openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000
                try {
                    conn.inputStream.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w("Avatar", "Photo download failed", e)
                null
            }
        }?.also { cache.put(url, it) }
    }
}
