package com.taxtech.mobile.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Dirección del servidor y JWT de la sesión. El token no sale del dispositivo (se excluye del respaldo). */
interface AlmacenSesion {
  fun servidor(): String
  fun guardarServidor(s: String)
  fun token(): String?
  fun guardarToken(t: String?)
}

class AlmacenPreferencias(context: Context) : AlmacenSesion {
  private val prefs = context.getSharedPreferences("taxtech_sesion", Context.MODE_PRIVATE)
  override fun servidor() = prefs.getString("servidor", null) ?: ConfigApi.SERVIDOR_POR_OMISION
  override fun guardarServidor(s: String) { prefs.edit().putString("servidor", s).apply() }
  override fun token() = prefs.getString("token", null)
  override fun guardarToken(t: String?) {
    prefs.edit().apply { if (t == null) remove("token") else putString("token", t) }.apply()
  }
}

class AlmacenMemoria(private var srv: String = ConfigApi.SERVIDOR_POR_OMISION, private var tok: String? = null) : AlmacenSesion {
  override fun servidor() = srv
  override fun guardarServidor(s: String) { srv = s }
  override fun token() = tok
  override fun guardarToken(t: String?) { tok = t }
}

/** Normaliza lo que escribe el usuario: agrega http:// y quita la barra final. null si no es una URL. */
fun normalizarServidor(texto: String): String? {
  val t = texto.trim().trimEnd('/')
  if (t.isEmpty()) return null
  val conEsquema = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
  return conEsquema.toHttpUrlOrNull()?.let { conEsquema }
}

/** Mensaje de error de Nest: `{ statusCode, message }`, con `message` como texto o lista. */
fun mensajeError(j: JsonElement?): String? = when (val m = j.campo("message")) {
  is JsonArray -> m.mapNotNull { it.texto() }.joinToString(" ").ifBlank { null }
  else -> m.texto()
}

/**
 * Único punto de acceso a los datos: REST con prefijo /api y el JWT del usuario en Authorization.
 * Un 401 cierra la sesión; un 403 con codigo CAMBIAR_CONTRASENA lleva a cambiar la contraseña.
 */
class ClienteApi(private val http: TransporteHttp, private val almacen: AlmacenSesion) {
  var alExpirar: () -> Unit = {}
  var alDebeCambiar: () -> Unit = {}

  suspend fun get(ruta: String, consulta: Map<String, Any?> = emptyMap()) = enviar("GET", ruta, consulta)
  suspend fun post(ruta: String, cuerpo: JsonElement? = null, consulta: Map<String, Any?> = emptyMap()) = enviar("POST", ruta, consulta, cuerpo)
  suspend fun patch(ruta: String, cuerpo: JsonElement) = enviar("PATCH", ruta, cuerpo = cuerpo)
  suspend fun put(ruta: String, cuerpo: JsonElement) = enviar("PUT", ruta, cuerpo = cuerpo)
  suspend fun delete(ruta: String) = enviar("DELETE", ruta)
  suspend fun subir(ruta: String, consulta: Map<String, Any?>, archivo: ArchivoSubida) = enviar("POST", ruta, consulta, archivo = archivo)

  /** POST sin token (inicio de sesión): un 401 aquí es una contraseña incorrecta, no una sesión vencida. */
  suspend fun postPublico(ruta: String, cuerpo: JsonElement) = enviar("POST", ruta, cuerpo = cuerpo, conToken = false)

  fun url(ruta: String, consulta: Map<String, Any?> = emptyMap()): String {
    val base = (almacen.servidor().trimEnd('/') + ConfigApi.PREFIJO + ruta).toHttpUrlOrNull()
      ?: throw ErrorApi(0, "La dirección del servidor no es válida.")
    return base.newBuilder().apply {
      consulta.forEach { (k, v) -> if (v != null && v.toString().isNotEmpty()) addQueryParameter(k, v.toString()) }
    }.build().toString()
  }

  private suspend fun enviar(
    metodo: String,
    ruta: String,
    consulta: Map<String, Any?> = emptyMap(),
    cuerpo: JsonElement? = null,
    archivo: ArchivoSubida? = null,
    conToken: Boolean = true,
  ): JsonElement {
    val token = if (conToken) almacen.token() ?: run { alExpirar(); throw ErrorApi(401, "Inicia sesión para continuar.") } else null
    val r = http.enviar(Peticion(metodo, url(ruta, consulta), token, cuerpo?.toString(), archivo))
    val j = runCatching { JsonApi.parseToJsonElement(r.cuerpo) }.getOrNull()
    if (!r.exitosa) {
      val codigo = j.campo("codigo").texto()
      if (conToken && r.codigo == 401) alExpirar()
      if (r.codigo == 403 && codigo == ConfigApi.CODIGO_CAMBIAR_CONTRASENA) alDebeCambiar()
      throw ErrorApi(r.codigo, mensajeError(j) ?: "Error ${r.codigo}", codigo)
    }
    return j ?: JsonNull
  }
}

sealed interface EstadoSesion {
  data object Comprobando : EstadoSesion
  data class SinSesion(val mensaje: String? = null) : EstadoSesion
  /** Tras el alta o un restablecimiento: solo puede cambiar su contraseña. */
  data class DebeCambiarContrasena(val usuario: Usuario) : EstadoSesion
  /** `id` cambia en cada inicio de sesión: todo el estado del panel se reinicia con él. */
  data class Activa(val usuario: Usuario, val id: Long) : EstadoSesion
}

class GestorSesion(private val api: ClienteApi, private val almacen: AlmacenSesion) {
  private val _estado = MutableStateFlow<EstadoSesion>(EstadoSesion.Comprobando)
  val estado: StateFlow<EstadoSesion> = _estado.asStateFlow()
  private var contador = 0L

  val servidor: String get() = almacen.servidor()

  suspend fun restaurar() {
    if (almacen.token() == null) { _estado.value = EstadoSesion.SinSesion(); return }
    try {
      aplicar(api.get("/auth/sesion").aUsuario())
    } catch (e: ErrorApi) {
      // 401 ya cerró la sesión; sin red se conserva el token para reintentar
      if (e.estado != 401) _estado.value = EstadoSesion.SinSesion(e.message)
    }
  }

  /** Lanza ErrorApi si el correo, la contraseña o el servidor no son válidos. */
  suspend fun iniciar(servidor: String, email: String, contrasena: String) {
    almacen.guardarServidor(servidor)
    val r = api.postPublico("/auth/login", JsonObject(mapOf("email" to JsonPrimitive(email.trim().lowercase()), "contrasena" to JsonPrimitive(contrasena))))
    val token = r.campo("token").texto() ?: throw ErrorApi(500, "Respuesta de inicio de sesión no válida.")
    almacen.guardarToken(token)
    aplicar(r.campo("usuario")?.aUsuario() ?: api.get("/auth/sesion").aUsuario())
  }

  suspend fun cambiarContrasena(actual: String, nueva: String) {
    api.post("/auth/contrasena", JsonObject(mapOf("actual" to JsonPrimitive(actual), "nueva" to JsonPrimitive(nueva))))
    aplicar(api.get("/auth/sesion").aUsuario(), conservarPanel = true)
  }

  private fun aplicar(u: Usuario, conservarPanel: Boolean = false) {
    val actual = _estado.value
    _estado.value = when {
      u.debeCambiarContrasena -> EstadoSesion.DebeCambiarContrasena(u)
      conservarPanel && actual is EstadoSesion.Activa -> actual.copy(usuario = u)
      else -> EstadoSesion.Activa(u, ++contador)
    }
  }

  /** El JWT no tiene estado en el servidor: cerrar sesión es olvidarlo. */
  fun salir() {
    almacen.guardarToken(null)
    _estado.value = EstadoSesion.SinSesion()
  }

  fun expirar() {
    if (_estado.value is EstadoSesion.SinSesion) return
    almacen.guardarToken(null)
    _estado.value = EstadoSesion.SinSesion("Tu sesión terminó. Vuelve a entrar.")
  }

  fun exigirCambioContrasena() {
    when (val e = _estado.value) {
      is EstadoSesion.Activa -> _estado.value = EstadoSesion.DebeCambiarContrasena(e.usuario.copy(debeCambiarContrasena = true))
      else -> Unit
    }
  }
}

/** Arma un objeto JSON omitiendo los valores null (la API trata «ausente» distinto de null). */
fun objeto(vararg pares: Pair<String, Any?>): JsonObject = JsonObject(
  pares.mapNotNull { (k, v) ->
    when (v) {
      null -> null
      is JsonElement -> k to v
      is Number -> k to JsonPrimitive(v)
      is Boolean -> k to JsonPrimitive(v)
      is Collection<*> -> k to JsonArray(v.map { x -> if (x is Number) JsonPrimitive(x) else JsonPrimitive(x.toString()) })
      else -> k to JsonPrimitive(v.toString())
    }
  }.toMap(),
)
