package com.taxtech.mobile.ui.panel

import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.data.Empresa
import com.taxtech.mobile.data.Resumen
import com.taxtech.mobile.data.Usuario
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.comun.mesAnterior
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Estado común del panel: empresa activa (selector del encabezado), mes activo (selector de
 * periodo, solo en Conciliar, Tablero y Comparativo) y el resumen de la empresa. Vive mientras
 * dura la sesión; al salir se descarta con todos los ViewModels del panel.
 */
class PanelViewModel(c: Contenedor, val usuario: Usuario) : VmBase(c) {
  data class Estado(
    val empresas: List<Empresa> = emptyList(),
    val empresaId: Long? = null,
    val resumen: Resumen? = null,
    val cargando: Boolean = true,
    val error: String? = null,
  ) {
    val empresa: Empresa? get() = empresas.firstOrNull { it.id == empresaId }
  }

  private val _estado = MutableStateFlow(Estado())
  val estado: StateFlow<Estado> = _estado.asStateFlow()

  /** Mes activo AAAA-MM. Si falta, el mes anterior (el que normalmente se concilia). */
  private val _mes = MutableStateFlow(mesAnterior())
  val mes: StateFlow<String> = _mes.asStateFlow()

  /** Ejecución que el Resumen pidió abrir en Conciliar: (regla, ejecucion_id). */
  val ejecucionPedida = MutableStateFlow<Pair<String, Long>?>(null)

  /** Tabla y filtro que otra pantalla pidió abrir en el Explorador. */
  val tablaPedida = MutableStateFlow<Pair<String, Map<String, String>>?>(null)

  init { cargarEmpresas() }

  fun cargarEmpresas() {
    _estado.update { it.copy(cargando = true, error = null) }
    lanzar(alFallar = { m -> _estado.update { it.copy(cargando = false, error = m) } }) {
      val lista = repo.empresas()
      val id = _estado.value.empresaId?.takeIf { x -> lista.any { it.id == x } } ?: lista.firstOrNull()?.id
      _estado.update { it.copy(empresas = lista, empresaId = id) }
      recargarAhora()
    }
  }

  /** Vuelve a leer el resumen de la empresa activa (después de ejecutar, cargar u ocultar). */
  fun recargar() = lanzar(alFallar = { m -> _estado.update { it.copy(cargando = false, error = m) } }) { recargarAhora() }

  suspend fun recargarAhora() {
    _estado.update { it.copy(cargando = true, error = null) }
    val r = repo.resumen(_estado.value.empresaId)
    _estado.update { it.copy(resumen = r, cargando = false) }
  }

  fun elegirEmpresa(id: Long) {
    if (id == _estado.value.empresaId) return
    _estado.update { it.copy(empresaId = id, resumen = null) }
    ejecucionPedida.value = null
    recargar()
  }

  fun elegirMes(mes: String) {
    // Nunca un mes futuro: el selector del panel tampoco lo permite
    _mes.value = minOf(mes, mesActual())
  }

  fun mesActual(): String = com.taxtech.mobile.ui.comun.sumarMes(mesAnterior(), 1)
}
