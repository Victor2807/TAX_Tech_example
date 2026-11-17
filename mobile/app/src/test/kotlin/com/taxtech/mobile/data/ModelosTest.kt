package com.taxtech.mobile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// JSON con la forma de compartido/src/tipos.ts (datos ficticios): importes como texto, ids como número.
class ModelosTest {
  private fun json(s: String) = JsonApi.parseToJsonElement(s)

  @Test
  fun tablero_leeImportesComoTexto() {
    val meses = json(
      """{"empresa_id":1,"meses":[{"periodo_desde":"2026-10-01","periodo_hasta":"2026-10-31","ejecucion_c5":12,"ejecutado_en":"2026-11-15T18:00:00.000Z",
        "reglas_ejecutadas":10,"trasladado":"100.00","cobrado":"80.50","pendiente_cobro":"19.50","notas_emitidas":"0.50","notas_recibidas":"0.00",
        "pagado":"70.00","pendiente_pago":"5.00","acreditable_nacional":"60.00","acreditable_importacion":"10.00","acreditable":"70.00",
        "saldo":"-62328.00","retenido":"3.00","no_acreditable":[{"estatus":"SIN_PAGO","partidas":2,"iva":"5.00"}],
        "riesgo_por_regla":[{"regla":"C4","conciliacion":"Pagos ERP vs banco","hallazgos":3,"importe_riesgo":"1234.56","ejecutada":true}]}]}""",
    ).aTableroIva()
    val m = meses.single()
    assertEquals(-62328.0, m.saldo, 0.0)
    assertEquals(80.0, m.trasladadoCobrado, 1e-9)
    assertEquals(12L, m.ejecucionC5)
    assertEquals("SIN_PAGO", m.noAcreditable.single().estatus)
    assertEquals(1234.56, m.riesgo.single().importe, 1e-9)
  }

  @Test
  fun explorador_columnasYLlaveSimple() {
    val t = json(
      """{"nombre":"cfdi","columnas":[{"nombre":"empresa_id","tipo":"INTEGER","requerida":true,"llave":true},
        {"nombre":"uuid","tipo":"TEXT","requerida":true,"llave":true},{"nombre":"metodo_pago","tipo":"TEXT","requerida":false,"llave":false,"valores":["PUE","PPD"]},
        {"nombre":"total","tipo":"DECIMAL","requerida":true,"llave":false},{"nombre":"cargado_en","tipo":"DATETIME","requerida":true,"llave":false}],
        "editable":true,"borrable":false}""",
    ).aTablaExplorador()
    assertEquals(listOf("empresa_id", "uuid"), t.llaves)
    assertNull(t.llaveSimple)
    assertEquals(listOf("PUE", "PPD"), t.columnas[2].valores)
    assertTrue(t.columnas[3].esDecimal)
    assertTrue(t.columnas[4].soloLectura)
    assertFalse(t.borrable)
  }

  @Test
  fun accesos_usuariosEmpresasYGrupos() {
    val a = json(
      """{"usuarios":[{"usuario_id":1,"email":"admin@x.mx","rol":"ADMIN","activo":true,"debe_cambiar_contrasena":false,"empresas":[]},
        {"usuario_id":2,"email":"lector@x.mx","rol":"LECTOR","activo":false,"debe_cambiar_contrasena":true,"empresas":[1,2]}],
        "empresas":[{"empresa_id":1,"rfc":"EMP010101AA1","razon_social":"Empresa","grupo_id":5,"grupo":"Corporativo","sociedad_sap":null}],
        "grupos":[{"grupo_id":5,"nombre":"Corporativo","empresas":1}]}""",
    ).aAccesos()
    assertEquals(Rol.ADMIN, a.usuarios[0].rol)
    assertEquals(listOf(1L, 2L), a.usuarios[1].empresas)
    assertFalse(a.usuarios[1].activo)
    assertEquals("Corporativo", a.empresas.single().grupo)
    assertEquals(1L, a.grupos.single().empresas)
  }

  @Test
  fun resultadoCarga_conAvisosYDetalle() {
    val r = json(
      """{"carga_id":null,"simulado":true,"fuente":"BANCO","archivo":"estado.sta","formato":"MT940","registros":42,
        "periodo_desde":"2026-10-01","periodo_hasta":"2026-10-31","detalle":{"movimientos":42,"estados":1},"avisos":["Saldo no cuadra"],"errores":[]}""",
    ).aResultadoCarga()
    assertTrue(r.simulado)
    assertNull(r.cargaId)
    assertEquals(42L, r.detalle["movimientos"])
    assertEquals(listOf("Saldo no cuadra"), r.avisos)
  }

  @Test
  fun comparativo_porReglaSinMeses() {
    val c = json("""{"periodos":[],"por_regla":[{"regla":"C1","conciliacion":"x","meses":null}],"detalle":[]}""").aComparativo()
    assertTrue(c.porRegla.single().meses.isEmpty())
  }

  @Test
  fun rol_desconocidoEsLector() {
    assertEquals(Rol.LECTOR, Rol.de("AUDITOR"))
    assertTrue(Rol.EDITOR.puedeEditar)
    assertFalse(Rol.LECTOR.puedeEditar)
  }

  @Test
  fun servidor_normaliza() {
    assertEquals("http://10.0.2.2:3001", normalizarServidor(" 10.0.2.2:3001/ "))
    assertEquals("https://api.ejemplo.mx", normalizarServidor("https://api.ejemplo.mx"))
    assertNull(normalizarServidor(""))
  }

  @Test
  fun objeto_omiteNulos() {
    assertEquals("""{"a":1,"c":[1,2]}""", objeto("a" to 1, "b" to null, "c" to listOf(1L, 2L)).toString())
  }
}
