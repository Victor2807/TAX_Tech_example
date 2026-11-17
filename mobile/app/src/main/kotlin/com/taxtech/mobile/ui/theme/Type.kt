package com.taxtech.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

val Typography = Typography(
  displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp),
  headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
  headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
  headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
  titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
  titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
  labelSmall = base.labelSmall.copy(letterSpacing = 0.8.sp),
)

/** Cifras con dígitos de ancho fijo para que los importes no "bailen" al animarse. */
val Cifras = TextStyle(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")
val Mono = TextStyle(fontFamily = FontFamily.Monospace)
