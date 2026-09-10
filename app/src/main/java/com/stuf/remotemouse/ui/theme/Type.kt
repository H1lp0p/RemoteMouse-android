package com.stuf.remotemouse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.stuf.remotemouse.R

/**
 * Kelly Slab ships as a single Regular face (Google Fonts / OFL).
 * Weights other than Normal may use platform synthetic styling.
 */
val KellySlabFamily = FontFamily(
    Font(R.font.kelly_slab_regular, FontWeight.Normal),
)

private fun kellyStyle(
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = KellySlabFamily,
    fontWeight = fontWeight,
    fontSize = fontSize,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

val Typography = Typography(
    displayLarge = kellyStyle(57.sp, 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = kellyStyle(45.sp, 52.sp),
    displaySmall = kellyStyle(36.sp, 44.sp),
    headlineLarge = kellyStyle(32.sp, 40.sp),
    headlineMedium = kellyStyle(28.sp, 36.sp),
    headlineSmall = kellyStyle(24.sp, 32.sp),
    titleLarge = kellyStyle(22.sp, 28.sp),
    titleMedium = kellyStyle(16.sp, 24.sp, FontWeight.Medium, 0.15.sp),
    titleSmall = kellyStyle(14.sp, 20.sp, FontWeight.Medium, 0.1.sp),
    bodyLarge = kellyStyle(16.sp, 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = kellyStyle(14.sp, 20.sp, letterSpacing = 0.25.sp),
    bodySmall = kellyStyle(12.sp, 16.sp, letterSpacing = 0.4.sp),
    labelLarge = kellyStyle(14.sp, 20.sp, FontWeight.Medium, 0.1.sp),
    labelMedium = kellyStyle(12.sp, 16.sp, FontWeight.Medium, 0.5.sp),
    labelSmall = kellyStyle(11.sp, 16.sp, FontWeight.Medium, 0.5.sp),
)
