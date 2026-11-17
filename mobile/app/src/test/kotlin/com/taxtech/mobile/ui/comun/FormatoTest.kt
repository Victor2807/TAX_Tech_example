package com.taxtech.mobile.ui.comun

import com.taxtech.mobile.data.Columna
import com.taxtech.mobile.ui.explorador.convertir
import com.taxtech.mobile.ui.login.validarContrasenaNueva
import java.util.TimeZone
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatoTest {
  @Test
  fun pesosYCompacto() {
    assertEquals("$62,328.00", pesos(62328.0))
    assertEquals("−$1,234.50", pesos(-1234.5))
    assertEquals("$1.2M", compacto(1_234_567.0))
    assertEquals("$45K", compacto(45_200.0))
    assertEquals("−$980", compacto(-980.0))
  }

  @Test
  fun meses() {
    assertEquals("Octubre 2026", mesLargo("2026-10-01"))
    assertEquals("nov 26", mesCorto("2026-11-01"))
    assertEquals("2027-01", sumarMes("2026-12", 1))
    assertEquals("2026-02-01" to "2026-02-28", limitesMes("2026-02"))
    assertEquals("2028-02-29", limitesMes("2028-02").second)
  }

  @Test
  fun fechas() {
    assertEquals("31/10/2026", fecha("2026-10-31"))
    // 18:30 UTC = 12:30 en Ciudad de México (UTC−6, sin horario de verano)
    assertEquals("15/11/2026 12:30", fechaHora("2026-11-15T18:30:00.000Z", TimeZone.getTimeZone("America/Mexico_City")))
    assertEquals("15/11/2026 18:30", fechaHora("2026-11-15 12:30:00-06", TimeZone.getTimeZone("UTC")))
  }

  @Test
  fun avisoDePeriodo() {
    assertNull(avisoPeriodo("2026-10-01", "2026-10-31", "C5"))
    assertTrue(avisoPeriodo("2026-10-01", "2026-11-30", "C5")!!.startsWith("El periodo abarca más de un mes."))
    assertTrue(avisoPeriodo("2026-10-05", "2026-10-31", "C1")!!.startsWith("El periodo no es un mes completo."))
  }

  @Test
  fun marcasDelEjeIncluyenCero() {
    val m = marcasEje(-62328.0, 76728.0)
    assertTrue(0.0 in m)
    assertTrue(m.first() <= -62328.0 && m.last() >= 76728.0)
  }

  @Test
  fun contrasenaNueva() {
    assertNotNull(validarContrasenaNueva("actual", "corta", "corta"))
    assertNotNull(validarContrasenaNueva("misma-contrasena", "misma-contrasena", "misma-contrasena"))
    assertNotNull(validarContrasenaNueva("actual", "una-frase-larga", "otra-frase-larga"))
    assertNull(validarContrasenaNueva("actual", "una-frase-larga", "una-frase-larga"))
  }

  @Test
  fun conversionDelExplorador() {
    val entero = Columna("registros", "INTEGER", true, false, null)
    val decimal = Columna("total", "DECIMAL", false, false, null)
    val bool = Columna("activo", "BOOLEAN", true, false, null)
    assertEquals(JsonPrimitive(42L), convertir(entero, "42").getOrThrow())
    assertTrue(convertir(entero, "4.2").isFailure)
    assertEquals(JsonPrimitive("1234.56"), convertir(decimal, "1234.56").getOrThrow())
    assertEquals(JsonNull, convertir(decimal, " ").getOrThrow())
    assertEquals(JsonPrimitive(false), convertir(bool, "false").getOrThrow())
    assertTrue(convertir(entero, "").isFailure)
  }
}
