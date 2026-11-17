package com.taxtech.mobile.data

import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source

object ConfigApi {
  /**
   * La API local escucha solo en 127.0.0.1:3001 de la máquina de desarrollo. Con
   * `adb reverse tcp:3001 tcp:3001` el emulador o el celular por USB la ven en esa misma dirección.
   */
  const val SERVIDOR_POR_OMISION = "http://127.0.0.1:3001"
  const val PREFIJO = "/api"
  /** Código del 403 que devuelve la API mientras el usuario debe cambiar su contraseña. */
  const val CODIGO_CAMBIAR_CONTRASENA = "CAMBIAR_CONTRASENA"
  const val MINIMO_CONTRASENA = 12
}

/** Error de la API o de la red. `estado` es el código HTTP (0 = sin conexión). */
class ErrorApi(val estado: Int, mensaje: String, val codigo: String? = null) : Exception(mensaje)

/** Archivo para un POST multipart: se lee al enviarse, sin cargarlo completo en memoria. */
class ArchivoSubida(val nombre: String, val tipo: String, val tamano: Long, val abrir: () -> InputStream)

data class Peticion(
  val metodo: String,
  val url: String,
  val token: String?,
  val json: String? = null,
  val archivo: ArchivoSubida? = null,
)

data class RespuestaHttp(val codigo: Int, val cuerpo: String) {
  val exitosa: Boolean get() = codigo in 200..299
}

/** Se abstrae para poder probar el cliente sin red. */
fun interface TransporteHttp {
  suspend fun enviar(p: Peticion): RespuestaHttp
}

private val TIPO_JSON = "application/json; charset=utf-8".toMediaType()

class TransporteOkHttp(
  private val cliente: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    // El cierre mensual corre las 10 reglas en una transacción y las cargas suben hasta 50 MB
    .readTimeout(180, TimeUnit.SECONDS)
    .writeTimeout(180, TimeUnit.SECONDS)
    .build(),
) : TransporteHttp {
  override suspend fun enviar(p: Peticion): RespuestaHttp = withContext(Dispatchers.IO) {
    val cuerpo: RequestBody? = when {
      p.archivo != null -> MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart("archivo", p.archivo.nombre, cuerpoArchivo(p.archivo)).build()
      p.json != null -> p.json.toRequestBody(TIPO_JSON)
      p.metodo == "POST" || p.metodo == "PATCH" || p.metodo == "PUT" -> "{}".toRequestBody(TIPO_JSON)
      else -> null
    }
    val req = Request.Builder().url(p.url).method(p.metodo, cuerpo).header("Accept", "application/json")
      .apply { p.token?.let { header("Authorization", "Bearer $it") } }
      .build()
    try {
      cliente.newCall(req).execute().use { r -> RespuestaHttp(r.code, r.body?.string().orEmpty()) }
    } catch (e: IOException) {
      throw ErrorApi(0, "No se pudo conectar con la API de TAX Tech. Revisa que el servidor esté encendido y su dirección.")
    }
  }

  private fun cuerpoArchivo(a: ArchivoSubida) = object : RequestBody() {
    override fun contentType() = a.tipo.toMediaTypeOrNull()
    override fun contentLength() = if (a.tamano >= 0) a.tamano else -1
    override fun writeTo(sink: BufferedSink) {
      a.abrir().source().use { sink.writeAll(it) }
    }
  }
}
