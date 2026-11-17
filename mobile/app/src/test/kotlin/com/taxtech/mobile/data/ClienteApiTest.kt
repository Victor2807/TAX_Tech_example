package com.taxtech.mobile.data

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ClienteApiTest {
  private lateinit var servidor: MockWebServer
  private lateinit var almacen: AlmacenMemoria
  private lateinit var api: ClienteApi
  private var expiro = 0
  private var debeCambiar = 0

  @Before
  fun preparar() {
    servidor = MockWebServer().apply { start() }
    almacen = AlmacenMemoria(servidor.url("/").toString().trimEnd('/'), "token-de-prueba")
    api = ClienteApi(TransporteOkHttp(), almacen).apply {
      alExpirar = { expiro++ }
      alDebeCambiar = { debeCambiar++ }
    }
  }

  @After
  fun terminar() = servidor.shutdown()

  private fun responder(codigo: Int, cuerpo: String) = servidor.enqueue(MockResponse().setResponseCode(codigo).setBody(cuerpo))

  @Test
  fun get_usaPrefijoApiTokenYConsulta() = runTest {
    responder(200, """[{"empresa_id":1,"rfc":"EMP010101AA1","razon_social":"Empresa","grupo_id":null,"grupo":null,"sociedad_sap":"MX01"}]""")
    val empresas = Repositorio(api).empresas()
    val r = servidor.takeRequest()
    assertEquals("/api/empresas", r.path)
    assertEquals("Bearer token-de-prueba", r.getHeader("Authorization"))
    assertEquals(1, empresas.size)
    assertEquals("MX01", empresas[0].sociedadSap)
  }

  @Test
  fun listar_mandaFiltrosComoParametrosYOmiteNulos() = runTest {
    responder(200, """{"filas":[],"total":0,"pagina":2,"limite":25}""")
    Repositorio(api).listar("cfdi", mapOf("direccion" to "RECIBIDO"), pagina = 2)
    val url = servidor.takeRequest().requestUrl!!
    assertEquals("/api/explorador/cfdi", url.encodedPath)
    assertEquals("RECIBIDO", url.queryParameter("direccion"))
    assertEquals("2", url.queryParameter("pagina"))
    assertEquals("asc", url.queryParameter("dir"))
    assertNull(url.queryParameter("orden"))
  }

  @Test
  fun ejecutar_omiteToleranciasVacias() = runTest {
    responder(200, """{"ejecucion_id":7,"regla_codigo":"C1","periodo_desde":"2026-10-01","periodo_hasta":"2026-10-31","total_partidas":17,"total_conciliadas":10}""")
    val e = Repositorio(api).ejecutar("C1", 1, "2026-10-01", "2026-10-31", "", null)
    val cuerpo = servidor.takeRequest().body.readUtf8()
    assertEquals("""{"regla":"C1","empresa_id":1,"desde":"2026-10-01","hasta":"2026-10-31"}""", cuerpo)
    assertEquals(7L, e.id)
  }

  @Test
  fun error_leeMensajeDeNest() = runTest {
    responder(400, """{"statusCode":400,"message":"hasta: La fecha inicial no puede ser posterior a la final."}""")
    try {
      api.get("/resumen")
      fail("Debió lanzar ErrorApi")
    } catch (e: ErrorApi) {
      assertEquals(400, e.estado)
      assertEquals("hasta: La fecha inicial no puede ser posterior a la final.", e.message)
      assertEquals(0, expiro)
    }
  }

  @Test
  fun error401_cierraLaSesion() = runTest {
    responder(401, """{"statusCode":401,"message":"Inicia sesión para continuar."}""")
    runCatching { api.get("/resumen") }
    assertEquals(1, expiro)
  }

  @Test
  fun error403ConCodigo_pideCambiarContrasena() = runTest {
    responder(403, """{"statusCode":403,"codigo":"CAMBIAR_CONTRASENA","message":"Debes cambiar tu contraseña antes de continuar."}""")
    val e = runCatching { api.get("/resumen") }.exceptionOrNull() as ErrorApi
    assertEquals(ConfigApi.CODIGO_CAMBIAR_CONTRASENA, e.codigo)
    assertEquals(1, debeCambiar)
    assertEquals(0, expiro)
  }

  @Test
  fun sinToken_noLlamaALaApi() = runTest {
    almacen.guardarToken(null)
    val e = runCatching { api.get("/resumen") }.exceptionOrNull() as ErrorApi
    assertEquals(401, e.estado)
    assertEquals(0, servidor.requestCount)
  }

  @Test
  fun inicioDeSesion_guardaTokenYUnaContrasenaIncorrectaNoExpira() = runTest {
    val gestor = GestorSesion(api, almacen)
    almacen.guardarToken(null)
    responder(401, """{"statusCode":401,"message":"Correo o contraseña incorrectos."}""")
    val e = runCatching { gestor.iniciar(almacen.servidor(), "ana@x.mx", "mala") }.exceptionOrNull() as ErrorApi
    assertEquals("Correo o contraseña incorrectos.", e.message)
    assertEquals(0, expiro)

    responder(200, """{"token":"jwt-nuevo","usuario":{"usuario_id":3,"email":"ana@x.mx","rol":"EDITOR","debe_cambiar_contrasena":false}}""")
    gestor.iniciar(almacen.servidor(), " Ana@X.mx ", "correcta")
    servidor.takeRequest()
    val login = servidor.takeRequest()
    assertEquals("/api/auth/login", login.path)
    assertNull(login.getHeader("Authorization"))
    assertTrue(login.body.readUtf8().contains("\"email\":\"ana@x.mx\""))
    assertEquals("jwt-nuevo", almacen.token())
    val activa = gestor.estado.value as EstadoSesion.Activa
    assertEquals(Rol.EDITOR, activa.usuario.rol)
  }

  @Test
  fun inicioDeSesion_conContrasenaPendiente() = runTest {
    val gestor = GestorSesion(api, almacen)
    responder(200, """{"token":"t","usuario":{"usuario_id":4,"email":"nuevo@x.mx","rol":"LECTOR","debe_cambiar_contrasena":true}}""")
    gestor.iniciar(almacen.servidor(), "nuevo@x.mx", "inicial-123456")
    assertTrue(gestor.estado.value is EstadoSesion.DebeCambiarContrasena)
  }

  @Test
  fun salir_olvidaElToken() {
    val gestor = GestorSesion(api, almacen)
    gestor.salir()
    assertNull(almacen.token())
    assertEquals(EstadoSesion.SinSesion(), gestor.estado.value)
  }
}
