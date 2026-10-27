package com.example.findme.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colors sampled from Yandex Maps screenshots. */
object YColors {
    val Blue = Color(0xFF196DFF)
    val BlueDark = Color(0xFF3D82FF)
    val Red = Color(0xFFFA453A)
    val Yellow = Color(0xFFFFCC00)
    val Star = Color(0xFFFFCD01)
    val Green = Color(0xFF2FA84F)
    val Orange = Color(0xFFE0922F)

    val JamFree = Color(0xFF3CB83C)
    val JamLight = Color(0xFFFFC820)
    val JamHard = Color(0xFFFF4F3F)
    val JamBlocked = Color(0xFF9E1B0F)
    val RouteAlt = Color(0xFF3557D5)
}

@Immutable
data class YPalette(
    val isDark: Boolean,
    val sheet: Color,
    val button: Color,
    val field: Color,
    val chip: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val icon: Color,
    val iconSecondary: Color,
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val closeCircle: Color,
    val selectedTab: Color,
    val onSelectedTab: Color,
)

val LightPalette = YPalette(
    isDark = false,
    sheet = Color(0xFFFFFFFF),
    button = Color(0xFFFFFFFF),
    field = Color(0xFFF3F3F3),
    chip = Color(0xFFF3F3F3),
    textPrimary = Color(0xFF21201F),
    textSecondary = Color(0xFF8E8E8E),
    icon = Color(0xFF21201F),
    iconSecondary = Color(0xFF4A4A4A),
    divider = Color(0xFFECECEC),
    accent = YColors.Blue,
    onAccent = Color.White,
    accentSoft = Color(0xFFEDF3FF),
    closeCircle = Color(0xFFD5D5D5),
    selectedTab = Color(0xFFFFFFFF),
    onSelectedTab = Color(0xFF21201F),
)

val DarkPalette = YPalette(
    isDark = true,
    sheet = Color(0xFF1E1E1E),
    button = Color(0xFF2B2B2B),
    field = Color(0xFF333333),
    chip = Color(0xFF333333),
    textPrimary = Color(0xFFF2F2F2),
    textSecondary = Color(0xFF9E9E9E),
    icon = Color(0xFFF2F2F2),
    iconSecondary = Color(0xFFCFCFCF),
    divider = Color(0xFF333333),
    accent = YColors.BlueDark,
    onAccent = Color.White,
    accentSoft = Color(0xFF243552),
    closeCircle = Color(0xFF4A4A4A),
    selectedTab = Color(0xFF4A4A4A),
    onSelectedTab = Color(0xFFF2F2F2),
)

val LocalYPalette = staticCompositionLocalOf { LightPalette }
