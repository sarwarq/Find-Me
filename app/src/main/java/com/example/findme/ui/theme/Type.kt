package com.example.findme.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.findme.R

// Onest (SIL Open Font License 1.1, free for commercial use). One variable file with
// Latin and Cyrillic, so the UI can be translated to Russian later.
@OptIn(ExperimentalTextApi::class)
private fun onest(weight: FontWeight) = Font(
    R.font.onest,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Onest = FontFamily(
    onest(FontWeight.Normal),
    onest(FontWeight.Medium),
    onest(FontWeight.SemiBold),
    onest(FontWeight.Bold),
    onest(FontWeight.ExtraBold),
)

// Tabular figures everywhere: ETA, speed and distance don't jitter while they update.
private fun style(weight: FontWeight, size: Int, line: Int, spacing: Float = 0f) = TextStyle(
    fontFamily = Onest,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = spacing.em,
    fontFeatureSettings = "tnum",
)

/** Big maneuver distance in guidance ("300 m"). */
val ManeuverStyle = style(FontWeight.ExtraBold, 44, 48, -0.02f)

/** Small caps section label ("WHO IS COMING"); write the text in upper case. */
val CapsLabelStyle = style(FontWeight.Bold, 11, 14, 0.08f)

val Typography = Typography(
    headlineLarge = style(FontWeight.Bold, 32, 38, -0.02f),
    headlineSmall = style(FontWeight.Bold, 24, 30, -0.01f),
    titleLarge = style(FontWeight.Bold, 22, 28, -0.01f),
    titleMedium = style(FontWeight.SemiBold, 17, 22),
    titleSmall = style(FontWeight.SemiBold, 15, 20),
    bodyLarge = style(FontWeight.Normal, 16, 22),
    bodyMedium = style(FontWeight.Normal, 15, 21),
    bodySmall = style(FontWeight.Normal, 13, 16),
    labelLarge = style(FontWeight.SemiBold, 16, 22),
    labelMedium = style(FontWeight.SemiBold, 14, 18),
    labelSmall = style(FontWeight.SemiBold, 12, 16),
)
