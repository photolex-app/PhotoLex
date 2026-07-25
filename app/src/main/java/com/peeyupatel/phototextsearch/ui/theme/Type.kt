package com.peeyupatel.phototextsearch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.peeyupatel.phototextsearch.R

// Custom font families
val MontserratFont = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal)
)

// System font families
val SerifFont = FontFamily.Serif
val CursiveFont = FontFamily.Cursive
val MonospaceFont = FontFamily.Monospace

// Wordmark font — single cohesive typeface for the "PhotoLex" logo text,
// differentiated by color (see MainBars.kt/AboutPage.kt) rather than by mixing font families.
val WordmarkFont = MontserratFont

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = MontserratFont,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MontserratFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

