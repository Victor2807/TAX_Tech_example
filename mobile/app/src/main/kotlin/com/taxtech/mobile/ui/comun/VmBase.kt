package com.taxtech.mobile.ui.comun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.data.ConfigApi
import com.taxtech.mobile.data.ErrorApi
import com.taxtech.mobile.data.Repositorio
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Base de los ViewModels: lanza llamadas a la API y manda los errores a un aviso
 * (equivalente a `manejarError` del panel). Un 401 ya cerró la sesión y un 403 por
 * contraseña pendiente ya llevó a cambiarla: esos no se repiten como aviso.
 */
abstract class VmBase(protected val c: Contenedor) : ViewModel() {
  protected val repo: Repositorio get() = c.repo

  protected fun avisar(mensaje: String) = c.avisos.emitir(mensaje)

  protected fun lanzar(
    alFallar: ((String) -> Unit)? = null,
    alTerminar: (() -> Unit)? = null,
    bloque: suspend () -> Unit,
  ): Job = viewModelScope.launch {
    try {
      bloque()
    } catch (e: CancellationException) {
      throw e
    } catch (e: ErrorApi) {
      val silencioso = e.estado == 401 || (e.estado == 403 && e.codigo == ConfigApi.CODIGO_CAMBIAR_CONTRASENA)
      if (!silencioso) (alFallar ?: ::avisar)(e.message ?: "Error ${e.estado}")
    } catch (e: Exception) {
      (alFallar ?: ::avisar)("Respuesta inesperada de la API: ${e.message}")
    } finally {
      alTerminar?.invoke()
    }
  }
}
