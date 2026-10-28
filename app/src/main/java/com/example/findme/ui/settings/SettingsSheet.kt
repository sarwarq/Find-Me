package com.example.findme.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.findme.container
import com.example.findme.data.Geo
import com.example.findme.data.NightMode
import com.example.findme.data.Units
import com.example.findme.ui.components.CapsLabel
import com.example.findme.ui.components.SegmentedControl
import com.example.findme.ui.components.YSwitch
import com.example.findme.ui.theme.yPalette

/**
 * The app's settings as grouped cards: guidance, routes, appearance, meet-ups.
 * Every change applies at once and is remembered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onClose: () -> Unit) {
    val container = LocalContext.current.container
    val s = container.settings
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = yPalette.ground,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = yPalette.textPrimary,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
            )

            Group("Guidance") {
                SwitchRow("Voice prompts", "Turns, cameras, arrival", s.voice) { container.voice.setMuted(!it) }
                Divider()
                SwitchRow("Speed camera alerts", null, s.cameraAlerts) { s.cameraAlerts = it }
                Divider()
                Column(Modifier.padding(top = 12.dp, bottom = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Warn when speeding",
                            style = MaterialTheme.typography.bodyLarge,
                            color = yPalette.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text("over the limit by", style = MaterialTheme.typography.bodySmall, color = yPalette.textSecondary)
                    }
                    Spacer(Modifier.height(10.dp))
                    SegmentedControl(
                        SPEEDING_MARGINS.map { if (it == 0) "Off" else "+${Geo.speed(it)}" },
                        SPEEDING_MARGINS.indexOf(s.speedingMarginKmh).coerceAtLeast(0),
                        { s.speedingMarginKmh = SPEEDING_MARGINS[it] },
                    )
                }
            }

            Group("Routes") {
                SwitchRow("Avoid toll roads", null, s.avoidTolls) { s.avoidTolls = it }
                Divider()
                SwitchRow("Avoid unpaved roads", null, s.avoidUnpaved) { s.avoidUnpaved = it }
            }

            Group("Appearance") {
                ChoiceRow("Theme", THEMES.map(::themeTitle), THEMES.indexOf(s.nightMode)) { s.nightMode = THEMES[it] }
                Divider()
                ChoiceRow("Units", UNITS.map { if (it == Units.Metric) "km" else "mi" }, UNITS.indexOf(s.units)) {
                    s.units = UNITS[it]
                }
            }

            Group("Meet-ups") {
                SwitchRow("Friends' routes", "Draw the roads friends drive on the map", s.friendRoutes) {
                    s.friendRoutes = it
                }
                Divider()
                SwitchRow("Tell me when a friend arrives", null, s.friendArrived) { s.friendArrived = it }
            }
        }
    }
}

/** Speeding margins offered, km/h (0 = off); shown in mph when the units are miles. */
private val SPEEDING_MARGINS = listOf(0, 5, 10, 20)

private val THEMES = listOf(NightMode.Auto, NightMode.Off, NightMode.On)
private val UNITS = listOf(Units.Metric, Units.Imperial)

private fun themeTitle(mode: NightMode) = when (mode) {
    NightMode.Auto -> "Auto"
    NightMode.Off -> "Day"
    NightMode.On -> "Night"
}

/** Caps label and a white card holding the rows. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    CapsLabel(title, Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(yPalette.sheet)
            .padding(horizontal = 14.dp),
        content = content,
    )
}

@Composable
private fun Divider() = HorizontalDivider(color = yPalette.divider)

/** Whole row toggles; the switch on the right shows the state. */
@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = yPalette.textSecondary)
            }
        }
        Spacer(Modifier.width(12.dp))
        YSwitch(checked, onChange = null)
    }
}

/** Title on the left, a compact segmented control on the right. */
@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = yPalette.textPrimary, modifier = Modifier.weight(1f))
        SegmentedControl(options, selected, onSelect, fill = false)
    }
}
