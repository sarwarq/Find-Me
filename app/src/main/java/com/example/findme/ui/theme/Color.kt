package com.example.findme.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Fixed colors of the Rendezvous design: the same in day and night. */
object YColors {
    val Blue = Color(0xFF2F55F4)
    val BlueDark = Color(0xFF4D6BFF)
    val Coral = Color(0xFFFF6B4A)
    val CoralDark = Color(0xFFFF7A5C)
    val Ink = Color(0xFF10141C)
    val Red = Color(0xFFE23B3B)
    val Yellow = Color(0xFFF2A20C)
    val Star = Color(0xFFF2A20C)
    val Green = Color(0xFF16A05A)
    val Orange = Color(0xFFF08C00)

    val JamFree = Color(0xFF16A05A)
    val JamLight = Color(0xFFF2A20C)
    val JamHard = Color(0xFFE23B3B)
    val JamBlocked = Color(0xFF8E1B12)
    val RouteAlt = Color(0xFF9AA6C9)

    /** One color per person in a meet-up (avatar, pin and route line). */
    val Friends = listOf(
        Color(0xFF7C5CFF), Color(0xFF0EA5A0), Color(0xFFF08C00), Color(0xFFE0458B),
        Color(0xFF2F9BF4), Color(0xFF16A05A), Color(0xFFB45CE0), Color(0xFFE2603B),
    )
}

@Immutable
data class YPalette(
    val isDark: Boolean,
    /** App background behind cards (settings, forms). */
    val ground: Color,
    /** Sheets and cards. */
    val sheet: Color,
    /** Floating map buttons. */
    val button: Color,
    /** Text fields, chips, icon tiles. */
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
    /** Meet-ups: meet point, invites. Always carries [onCoral] text. */
    val coral: Color,
    val onCoral: Color,
    val coralSoft: Color,
    /** Coral readable as text on [sheet]. */
    val coralText: Color,
    val closeCircle: Color,
    val selectedTab: Color,
    val onSelectedTab: Color,
    /** Selected chip: ink in day, light in night. */
    val chipSelected: Color,
    val onChipSelected: Color,
    val grabber: Color,
    /** Status badges: soft background + readable text. */
    val successSoft: Color,
    val onSuccessSoft: Color,
    val warningSoft: Color,
    val onWarningSoft: Color,
    val dangerSoft: Color,
    val onDangerSoft: Color,
    /** Shadow tint of floating elements. */
    val shadow: Color,
)

val LightPalette = YPalette(
    isDark = false,
    ground = Color(0xFFF4F5F7),
    sheet = Color(0xFFFFFFFF),
    button = Color(0xFFFFFFFF),
    field = Color(0xFFEEF0F4),
    chip = Color(0xFFEEF0F4),
    textPrimary = Color(0xFF10141C),
    textSecondary = Color(0xFF5B6573),
    icon = Color(0xFF10141C),
    iconSecondary = Color(0xFF3A4351),
    divider = Color(0xFFE3E6EB),
    accent = YColors.Blue,
    onAccent = Color.White,
    accentSoft = Color(0xFFE8EDFF),
    coral = YColors.Coral,
    onCoral = YColors.Ink,
    coralSoft = Color(0xFFFFF1EC),
    coralText = Color(0xFFC23A1E),
    closeCircle = Color(0xFFEEF0F4),
    selectedTab = Color(0xFFFFFFFF),
    onSelectedTab = Color(0xFF10141C),
    chipSelected = YColors.Ink,
    onChipSelected = Color.White,
    grabber = Color(0xFFD5D9E0),
    successSoft = Color(0xFFE3F5EA),
    onSuccessSoft = Color(0xFF0B6B3A),
    warningSoft = Color(0xFFFFF2D6),
    onWarningSoft = Color(0xFF7A4E00),
    dangerSoft = Color(0xFFFDE5E5),
    onDangerSoft = Color(0xFFA61E1E),
    shadow = Color(0xFF10141C),
)

val DarkPalette = YPalette(
    isDark = true,
    ground = Color(0xFF0B0E14),
    sheet = Color(0xFF151A23),
    button = Color(0xFF151A23),
    field = Color(0xFF1F2531),
    chip = Color(0xFF1F2531),
    textPrimary = Color(0xFFF1F4F9),
    textSecondary = Color(0xFFA4ADBB),
    icon = Color(0xFFF1F4F9),
    iconSecondary = Color(0xFFCDD3DD),
    divider = Color(0xFF2A3140),
    accent = YColors.BlueDark,
    onAccent = Color.White,
    accentSoft = Color(0xFF1C2654),
    coral = YColors.CoralDark,
    onCoral = YColors.Ink,
    coralSoft = Color(0xFF2A1C19),
    coralText = Color(0xFFFF8A70),
    closeCircle = Color(0xFF1F2531),
    selectedTab = Color(0xFF2A3140),
    onSelectedTab = Color(0xFFF1F4F9),
    chipSelected = Color(0xFFF1F4F9),
    onChipSelected = YColors.Ink,
    grabber = Color(0xFF3A4252),
    successSoft = Color(0xFF13301F),
    onSuccessSoft = Color(0xFF4CD38A),
    warningSoft = Color(0xFF35290F),
    onWarningSoft = Color(0xFFF5BD4F),
    dangerSoft = Color(0xFF3A1E22),
    onDangerSoft = Color(0xFFFF8A8A),
    shadow = Color(0xFF000000),
)

val LocalYPalette = staticCompositionLocalOf { LightPalette }
