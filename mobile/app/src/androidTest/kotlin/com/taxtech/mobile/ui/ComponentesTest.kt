package com.taxtech.mobile.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.taxtech.mobile.ui.comun.ChipEstatus
import com.taxtech.mobile.ui.comun.SelectorMes
import com.taxtech.mobile.ui.theme.TAXTechTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Pruebas de interfaz de los componentes comunes (en claro y en oscuro). */
class ComponentesTest {
  @get:Rule val regla = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun chipDeEstatus_describeElEstatus() {
    regla.setContent { TAXTechTheme(darkTheme = true) { ChipEstatus("PROVEEDOR_69B") } }
    regla.onNodeWithContentDescription("PROVEEDOR_69B: Emisor en lista 69-B").assertExists()
  }

  @Test
  fun selectorDeMes_noAvanzaMasAllaDelMaximo() {
    var mes = "2026-10"
    regla.setContent { TAXTechTheme(darkTheme = false) { Column { SelectorMes(mes, { mes = it }, maximo = "2026-11") } } }
    regla.onNodeWithText("Octubre 2026").assertExists()
    regla.onNodeWithContentDescription("Mes siguiente").performClick()
    assertEquals("2026-11", mes)
    regla.onNodeWithContentDescription("Mes anterior").performClick()
    assertEquals("2026-09", mes)
  }
}
