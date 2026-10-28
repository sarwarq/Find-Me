package com.example.findme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.findme.R
import com.example.findme.data.auth.UserProfile
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.Screen
import com.example.findme.ui.profile.Avatar
import com.example.findme.ui.theme.YColors
import com.example.findme.ui.theme.yPalette
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Side margin of everything floating over the map. */
private val EDGE = 16.dp

/**
 * Floating map controls. Main screen: search pill + profile at the top, traffic and layers
 * under it, "New meet-up" on the left and directions + "my location" on the right right above
 * the sheet. Other screens: layers under the top bar, "my location" above the sheet. Zoom sits in the
 * middle of the right edge, the scale ruler on the left, the compass under layers when turned.
 */
@Composable
fun MapControls(
    vm: MapViewModel,
    sheetTop: Float,
    topBarPx: Int,
    user: UserProfile?,
    onSearch: () -> Unit,
    onProfileClick: () -> Unit,
    onLocationClick: () -> Unit,
    onVoice: () -> Unit,
    onNewMeetup: () -> Unit,
) {
    val density = LocalDensity.current
    fun px(v: Dp) = with(density) { v.roundToPx() }
    val screen = vm.screen
    val isHome = screen == Screen.Home

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullHeight = constraints.maxHeight.toFloat()
        val statusTop = WindowInsets.statusBars.getTop(density)
        val searchTop = statusTop + px(12.dp)
        val top = when {
            topBarPx > 0 -> topBarPx + px(12.dp)
            isHome -> searchTop + px(52.dp) + px(12.dp)
            else -> statusTop + px(12.dp)
        }
        val sheet = if (sheetTop.isNaN()) fullHeight else sheetTop
        val sheetLow = sheet > fullHeight * 0.5f

        // ---- Main screen: search pill and profile ----
        if (isHome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, searchTop) }
                    .padding(horizontal = EDGE),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchPill(onSearch, onVoice, Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                ProfileButton(user, onProfileClick)
            }
            val info = vm.trafficInfo
            if (vm.trafficOn && info != null) {
                TrafficPill(
                    info.level, Color(info.color),
                    onClick = { vm.layersOpen = true },
                    modifier = Modifier.align(Alignment.TopStart)
                        .offset { IntOffset(px(EDGE), top + px(4.dp)) },
                )
            }
        }

        // ---- Top right column: layers (and transit on the route screen), compass ----
        val az = ((vm.azimuth % 360) + 360) % 360
        val rotated = az > 1f && az < 359f
        val buttons = 1 + (if (rotated) 1 else 0)
        Column(
            Modifier.align(Alignment.TopEnd).offset { IntOffset(-px(EDGE), top) },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MapButton(onClick = { vm.layersOpen = true }) {
                YIcon(R.drawable.ms_layers, "Map layers", tint = yPalette.icon, size = 24.dp)
            }
            if (rotated) {
                MapButton(onClick = { vm.resetNorth() }, shape = CircleShape) { Compass(az) }
            }
        }
        val topColumnBottom = top + px(48.dp) * buttons + px(12.dp) * (buttons - 1)

        // ---- Right above the sheet ----
        val rowTop = sheet - px(12.dp) - px(48.dp)
        val showRow = sheetLow && rowTop > topColumnBottom + px(12.dp)
        if (showRow) {
            if (isHome) {
                NewMeetupPill(
                    onNewMeetup,
                    Modifier.align(Alignment.TopStart).offset { IntOffset(px(EDGE), rowTop.roundToInt()) },
                )
            }
            Row(
                Modifier.align(Alignment.TopEnd).offset { IntOffset(-px(EDGE), rowTop.roundToInt()) },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isHome) {
                    MapButton(onClick = { vm.startRoutePlanning() }) {
                        YIcon(R.drawable.ms_route, "Directions", tint = yPalette.icon, size = 24.dp)
                    }
                }
                MapButton(onClick = onLocationClick) {
                    YIcon(
                        R.drawable.ms_near_me, "My location",
                        tint = if (vm.following) yPalette.accent else yPalette.icon, size = 24.dp,
                    )
                }
            }
        }

        // ---- Zoom, in the middle of the right edge ----
        // Centered at ~47% of the screen, moved up when it would touch the row above the sheet.
        val zoomLimit = (if (showRow) rowTop else sheet) - px(12.dp) - px(108.dp)
        val zoomTop = minOf(fullHeight * 0.47f - px(54.dp), zoomLimit)
        if (sheetLow && zoomTop >= topColumnBottom + px(16.dp)) {
            Column(
                Modifier.align(Alignment.TopEnd).offset { IntOffset(-px(EDGE), zoomTop.roundToInt()) },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MapButton(onClick = { vm.zoomIn() }) { YIcon(R.drawable.ms_add, "Zoom in", size = 26.dp) }
                MapButton(onClick = { vm.zoomOut() }) { YIcon(R.drawable.ms_remove, "Zoom out", size = 26.dp) }
            }
            ScaleRuler(
                metersPerPx = vm.metersPerPx,
                modifier = Modifier.align(Alignment.TopStart)
                    .offset { IntOffset(px(EDGE), (zoomTop + px(54.dp) - px(60.dp)).roundToInt()) },
            )
        }
    }
}

/** White search pill of the main screen; the mic starts voice search. */
@Composable
private fun SearchPill(onClick: () -> Unit, onVoice: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
        color = yPalette.button,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            YIcon(R.drawable.ms_search, null, tint = yPalette.textSecondary, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                "Search places",
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onVoice),
                contentAlignment = Alignment.Center,
            ) {
                YIcon(R.drawable.ms_mic, "Voice search", tint = yPalette.textSecondary, size = 20.dp)
            }
        }
    }
}

/** Round avatar with a white ring: profile and settings. */
@Composable
private fun ProfileButton(user: UserProfile?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = yPalette.button,
        shadowElevation = 4.dp,
    ) {
        Box(Modifier.padding(3.dp), contentAlignment = Alignment.Center) {
            if (user != null) {
                Avatar(user, 46.dp)
            } else {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(yPalette.field),
                    contentAlignment = Alignment.Center,
                ) {
                    YIcon(R.drawable.ms_person, "Sign in", tint = yPalette.textSecondary, size = 26.dp)
                }
            }
        }
    }
}

/** Coral "New meet-up" pill, bottom left of the main screen. */
@Composable
private fun NewMeetupPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = CircleShape,
        color = yPalette.coral,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            YIcon(R.drawable.ms_person_add, null, tint = yPalette.onCoral, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                "New meet-up",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = yPalette.onCoral,
                maxLines = 1,
            )
        }
    }
}

/** "(5) Moderate traffic" pill; opens the layers sheet where traffic is switched. */
@Composable
private fun TrafficPill(level: Int, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = CircleShape,
        color = yPalette.button,
        shadowElevation = 4.dp,
    ) {
        Row(Modifier.padding(start = 7.dp, end = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
                Text(
                    level.toString(),
                    color = if (color.luminance() > 0.35f) YColors.Ink else Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                trafficText(level),
                style = MaterialTheme.typography.labelMedium,
                color = yPalette.textPrimary,
                maxLines = 1,
            )
        }
    }
}

private fun trafficText(level: Int) = when {
    level <= 3 -> "Light traffic"
    level <= 6 -> "Moderate traffic"
    else -> "Heavy traffic"
}

/** Vertical scale bar with a round distance ("100 m"). */
@Composable
private fun ScaleRuler(metersPerPx: Float, modifier: Modifier = Modifier) {
    if (metersPerPx <= 0f) return
    val density = LocalDensity.current
    val maxPx = with(density) { 120.dp.toPx() }
    val maxMeters = metersPerPx * maxPx
    val magnitude = 10.0.pow(floor(log10(maxMeters.toDouble())))
    val nice = listOf(5.0, 2.0, 1.0).map { it * magnitude }.first { it <= maxMeters }
    val heightDp = with(density) { (nice / metersPerPx).toFloat().toDp() }
    val label = if (nice >= 1000) {
        val km = nice / 1000
        "${if (km % 1.0 == 0.0) km.toInt().toString() else km.toString()} km"
    } else {
        "${nice.toInt()} m"
    }
    val lineColor = yPalette.iconSecondary
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(heightDp).background(lineColor))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = lineColor)
    }
}

@Composable
private fun Compass(azimuth: Float) {
    val south = yPalette.grabber
    val hub = yPalette.button
    Canvas(Modifier.size(26.dp).rotate(-azimuth)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val w = size.width * 0.2f
        val northPath = Path().apply { moveTo(cx, 0f); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
        val southPath = Path().apply { moveTo(cx, size.height); lineTo(cx + w, cy); lineTo(cx - w, cy); close() }
        drawPath(northPath, YColors.Red)
        drawPath(southPath, south)
        drawCircle(hub, radius = w * 0.35f, center = Offset(cx, cy))
    }
}
