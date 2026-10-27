package com.example.findme.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.findme.R

// MADE Okine Sans by MadeType. The bundled files are the free "personal use" version;
// a commercial license is needed before publishing the app.
// It has Latin glyphs only, so the UI is in English.
val OkineSans = FontFamily(
    Font(R.font.okine_sans_light, FontWeight.Light),
    Font(R.font.okine_sans_regular, FontWeight.Normal),
    Font(R.font.okine_sans_medium, FontWeight.Medium),
    Font(R.font.okine_sans_bold, FontWeight.Bold),
    Font(R.font.okine_sans_black, FontWeight.Black),
)

private fun style(weight: FontWeight, size: Int, line: Int) = TextStyle(
    fontFamily = OkineSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val Typography = Typography(
    headlineSmall = style(FontWeight.Medium, 24, 28),
    titleLarge = style(FontWeight.Bold, 20, 24),
    titleMedium = style(FontWeight.Medium, 17, 22),
    titleSmall = style(FontWeight.Medium, 15, 20),
    bodyLarge = style(FontWeight.Normal, 17, 22),
    bodyMedium = style(FontWeight.Normal, 15, 20),
    bodySmall = style(FontWeight.Normal, 13, 16),
    labelLarge = style(FontWeight.Medium, 17, 22),
    labelMedium = style(FontWeight.Medium, 15, 18),
    labelSmall = style(FontWeight.Medium, 13, 16),
)
