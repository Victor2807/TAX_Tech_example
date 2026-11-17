package com.taxtech.mobile

import android.app.Application
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.taxtech.mobile.data.AlmacenPreferencias
import com.taxtech.mobile.data.ClienteApi
import com.taxtech.mobile.data.GestorSesion
import com.taxtech.mobile.data.Repositorio
import com.taxtech.mobile.data.TransporteOkHttp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Avisos breves para el usuario (equivalente al `toast` del panel). */
class Avisos {
  private val flujo = MutableSharedFlow<String>(extraBufferCapacity = 8)
  val mensajes: SharedFlow<String> = flujo.asSharedFlow()
  fun emitir(mensaje: String) { flujo.tryEmit(mensaje) }
}

/** Dependencias de la app, creadas una sola vez. */
class Contenedor(context: Context) {
  private val almacen = AlmacenPreferencias(context)
  private val api = ClienteApi(TransporteOkHttp(), almacen)
  val avisos = Avisos()
  val sesion = GestorSesion(api, almacen)
  val repo = Repositorio(api)

  init {
    api.alExpirar = { sesion.expirar() }
    api.alDebeCambiar = { sesion.exigirCambioContrasena() }
  }
}

class TaxTechAplicacion : Application() {
  lateinit var contenedor: Contenedor
    private set

  override fun onCreate() {
    super.onCreate()
    contenedor = Contenedor(this)
  }
}

val LocalContenedor = staticCompositionLocalOf<Contenedor> { error("Falta el Contenedor") }
