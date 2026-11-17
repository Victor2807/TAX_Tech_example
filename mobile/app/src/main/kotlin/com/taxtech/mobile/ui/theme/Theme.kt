package com.taxtech.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val Oscuro = darkColorScheme(
  primary = Marca.Esmeralda400,
  onPrimary = Marca.Noche950,
  primaryContainer = Marca.Esmeralda900,
  onPrimaryContainer = Marca.Esmeralda200,
  secondary = Marca.Oro400,
  onSecondary = Marca.Noche950,
  secondaryContainer = Color(0xFF422006),
  onSecondaryContainer = Marca.Oro300,
  tertiary = Marca.Cielo400,
  background = Marca.Noche950,
  onBackground = Marca.Pizarra100,
  surface = Marca.Noche900,
  onSurface = Marca.Pizarra100,
  surfaceVariant = Marca.Noche800,
  onSurfaceVariant = Marca.Pizarra400,
  surfaceContainerLowest = Marca.Noche950,
  surfaceContainerLow = Color(0xFF0B1222),
  surfaceContainer = Marca.Noche900,
  surfaceContainerHigh = Color(0xFF162033),
  surfaceContainerHighest = Marca.Noche800,
  outline = Marca.Noche700,
  outlineVariant = Marca.Noche800,
  error = Marca.Rosa400,
  onError = Marca.Noche950,
)

private val Claro = lightColorScheme(
  primary = Marca.Esmeralda700,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFD1FAE5),
  onPrimaryContainer = Marca.Esmeralda900,
  secondary = Marca.Oro700,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFEF3C7),
  onSecondaryContainer = Color(0xFF78350F),
  tertiary = Marca.Cielo600,
  background = Marca.Pizarra50,
  onBackground = Marca.Noche900,
  surface = Color.White,
  onSurface = Marca.Noche900,
  surfaceVariant = Marca.Pizarra100,
  onSurfaceVariant = Marca.Pizarra600,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Marca.Pizarra50,
  surfaceContainer = Marca.Pizarra100,
  surfaceContainerHigh = Marca.Pizarra200,
  surfaceContainerHighest = Marca.Pizarra300,
  outline = Marca.Pizarra300,
  outlineVariant = Marca.Pizarra200,
  error = Marca.Rosa600,
  onError = Color.White,
)

/** Colores propios de TAX Tech que no están en el esquema de Material. */
@Immutable
data class ColoresTax(
  val oscuro: Boolean,
  val vidrio: Color,
  val bordeVidrio: Color,
  val aurora1: Color,
  val aurora2: Color,
  val aurora3: Color,
  // Gráficos: mismos tonos que el tablero web
  val serie1: Color,
  val serie2: Color,
  val favor: Color,
  val cargo: Color,
  val rejilla: Color,
  val eje: Color,
  val exito: Color,
  val aviso: Color,
  val peligro: Color,
)

private val ColoresOscuro = ColoresTax(
  oscuro = true,
  vidrio = Color(0xB30F172A),
  bordeVidrio = Color(0x33FFFFFF),
  aurora1 = Marca.Esmeralda500.copy(alpha = 0.26f),
  aurora2 = Marca.Oro500.copy(alpha = 0.12f),
  aurora3 = Marca.Cielo400.copy(alpha = 0.14f),
  serie1 = Color(0xFF3987E5),
  serie2 = Color(0xFFD95926),
  favor = Color(0xFF3987E5),
  cargo = Color(0xFFE66767),
  rejilla = Marca.Noche800,
  eje = Marca.Noche700,
  exito = Marca.Esmeralda400,
  aviso = Marca.Oro400,
  peligro = Marca.Rosa400,
)

private val ColoresClaro = ColoresTax(
  oscuro = false,
  vidrio = Color(0xD9FFFFFF),
  bordeVidrio = Color(0x1F0F172A),
  aurora1 = Marca.Esmeralda300.copy(alpha = 0.40f),
  aurora2 = Marca.Oro300.copy(alpha = 0.34f),
  aurora3 = Marca.Cielo400.copy(alpha = 0.16f),
  serie1 = Color(0xFF2A78D6),
  serie2 = Color(0xFFEB6834),
  favor = Color(0xFF2A78D6),
  cargo = Color(0xFFE34948),
  rejilla = Marca.Pizarra200,
  eje = Marca.Pizarra300,
  exito = Marca.Esmeralda700,
  aviso = Marca.Oro700,
  peligro = Marca.Rosa600,
)

val LocalColoresTax = staticCompositionLocalOf { ColoresOscuro }

@Composable
fun TAXTechTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalColoresTax provides if (darkTheme) ColoresOscuro else ColoresClaro) {
    MaterialTheme(colorScheme = if (darkTheme) Oscuro else Claro, typography = Typography, content = content)
  }
}
