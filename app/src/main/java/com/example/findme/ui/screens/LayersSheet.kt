package com.example.findme.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.findme.data.MapLayerType
import com.example.findme.data.NightMode
import com.example.findme.ui.MapViewModel
import com.example.findme.ui.theme.yPalette

/** "Layers": map type, traffic and night mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(vm: MapViewModel) {
    ModalBottomSheet(
        onDismissRequest = { vm.layersOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yPalette.sheet,
    ) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(
                "Layers",
                style = MaterialTheme.typography.titleLarge,
                color = yPalette.textPrimary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MapLayerType.entries.forEach { type ->
                    LayerCard(type, selected = vm.mapLayer == type, modifier = Modifier.weight(1f)) {
                        vm.mapLayer = type
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.toggleTraffic() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Traffic, null, tint = yPalette.icon)
                Spacer(Modifier.width(16.dp))
                Text("Traffic", style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary, modifier = Modifier.weight(1f))
                Switch(
                    checked = vm.trafficOn,
                    onCheckedChange = { vm.toggleTraffic() },
                    colors = SwitchDefaults.colors(checkedTrackColor = yPalette.accent),
                )
            }
            Text(
                "Dark theme",
                style = MaterialTheme.typography.bodyLarge,
                color = yPalette.textPrimary,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(yPalette.field)
                    .padding(3.dp),
            ) {
                NightMode.entries.forEach { mode ->
                    val selected = vm.nightMode == mode
                    Text(
                        mode.title,
                        color = yPalette.textPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) yPalette.sheet else Color.Transparent)
                            .clickable { vm.nightMode = mode }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerCard(type: MapLayerType, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        val shape = RoundedCornerShape(12.dp)
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1.2f)
                .clip(shape)
                .border(if (selected) 2.5.dp else 0.dp, if (selected) yPalette.accent else Color.Transparent, shape)
        ) {
            val ground = when (type) {
                MapLayerType.Scheme -> Color(0xFFF1EEE6)
                else -> Color(0xFF3F5A3A)
            }
            drawRect(ground)
            if (type != MapLayerType.Scheme) {
                drawCircle(Color(0xFF2F4A2C), radius = size.minDimension * 0.35f, center = Offset(size.width * 0.25f, size.height * 0.3f))
                drawCircle(Color(0xFF55704A), radius = size.minDimension * 0.3f, center = Offset(size.width * 0.8f, size.height * 0.75f))
            } else {
                drawCircle(Color(0xFFC9E7B4), radius = size.minDimension * 0.28f, center = Offset(size.width * 0.8f, size.height * 0.25f))
                drawRect(Color(0xFFAAD3F2), topLeft = Offset(0f, size.height * 0.72f), size = androidx.compose.ui.geometry.Size(size.width * 0.4f, size.height * 0.28f))
            }
            if (type != MapLayerType.Satellite) {
                val road = if (type == MapLayerType.Scheme) Color.White else Color(0xFFFFD45C)
                drawLine(road, Offset(0f, size.height * 0.55f), Offset(size.width, size.height * 0.35f), strokeWidth = 8f)
                drawLine(road, Offset(size.width * 0.45f, 0f), Offset(size.width * 0.6f, size.height), strokeWidth = 6f)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            type.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) yPalette.accent else yPalette.textPrimary,
        )
    }
}
