package com.example.findme.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.ui.components.YIcon
import com.example.findme.ui.theme.yPalette

/** Full screen that replaces the app until it is updated. Back does nothing. */
@Composable
fun UpdateRequiredScreen(message: String, download: DownloadState, onUpdate: () -> Unit, onCancel: () -> Unit) {
    BackHandler { }
    Box(
        Modifier.fillMaxSize().background(yPalette.sheet).systemBarsPadding().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(yPalette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ic_nav_arrow, null, tint = Color.Unspecified, size = 52.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Update required",
                style = MaterialTheme.typography.headlineSmall,
                color = yPalette.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            if (download.busy) {
                DownloadProgress(download)
                if (download is DownloadState.Downloading) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancel) { Text("Cancel", color = yPalette.textSecondary) }
                }
            } else {
                (download as? DownloadState.Failed)?.let {
                    ErrorText(it.message)
                    Spacer(Modifier.height(16.dp))
                }
                Surface(
                    onClick = onUpdate,
                    shape = RoundedCornerShape(14.dp),
                    color = yPalette.accent,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (download is DownloadState.Failed) "Try again" else "Update",
                            style = MaterialTheme.typography.labelLarge,
                            color = yPalette.onAccent,
                        )
                    }
                }
            }
        }
    }
}

/** Downloading or waiting for Android's install screen. */
private val DownloadState.busy: Boolean
    get() = this is DownloadState.Downloading || this == DownloadState.Installing

@Composable
private fun DownloadProgress(download: DownloadState) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val progress = (download as? DownloadState.Downloading)?.progress ?: -1f
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = yPalette.accent,
                trackColor = yPalette.accentSoft,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = yPalette.accent,
                trackColor = yPalette.accentSoft,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            when {
                download == DownloadState.Installing -> "Installing…"
                progress >= 0f -> "Downloading… ${(progress * 100).toInt()}%"
                else -> "Downloading…"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = yPalette.textSecondary,
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = yPalette.coralText,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
