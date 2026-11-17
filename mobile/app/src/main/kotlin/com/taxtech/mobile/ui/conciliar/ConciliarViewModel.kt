package com.taxtech.mobile.ui.conciliar

import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.data.Ejecucion
import com.taxtech.mobile.data.EjecucionOculta
import com.taxtech.mobile.data.ResultadosEjecucion
import com.taxtech.mobile.data.entero
import com.taxtech.mobile.data.texto
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.comun.limitesMes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/** Filtros por revisión: se combinan con el de estatus (igual que el panel). */
enum class FiltroRevision(val etiqueta: String, val cumple: (JsonObject) -> Boolean) {
  TODAS("Todas", { true }),
  PENDIENTES("Sin revisar", { it["revisado_por"].texto() == null }),
  REVISADAS("Revisadas", { it["revisado_por"].texto() != null }),
  CONSERVADAS("Conservadas", { it["revisado_por"].texto() != null && it["revision_heredada_de"].texto() != null }),
}

fun JsonObject.idResultado(): Long = this["resultado_id"].entero() ?: 0

class ConciliarViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(
    val empresaId: Long? = null,
    val mes: String? = null,
    val regla: String = "C1",
    val ejecuciones: List<Ejecucion> = emptyList(),
    val ejecucionId: Long? = null,
    val resultados: ResultadosEjecucion? = null,
    val estatus: String? = null,
    val revision: FiltroRevision = FiltroRevision.TODAS,
    val seleccion: Set<Long> = emptySet(),
    val cargandoEjecuciones: Boolean = false,
    val cargandoResultados: Boolean = false,
    val ejecutando: Boolean = false,
    val trabajando: Boolean = false,
    val ocultas: List<EjecucionOculta>? = null,
    val error: String? = null,
  ) {
    val ejecucion: Ejecucion? get() = ejecuciones.firstOrNull { it.id == ejecucionId } ?: resultados?.ejecucion?.takeIf { it.id == ejecucionId }
    val porEstatus: List<JsonObject> get() = resultados?.filas.orEmpty().filter { estatus == null || it["estatus"].texto() == estatus }
    val visibles: List<JsonObject> get() = porEstatus.filter(revision.cumple)
  }

  val estado = MutableStateFlow(Estado())

  /** Llamado al abrir la pestaña o al cambiar empresa, mes o la ejecución pedida desde el Resumen. */
  fun sincronizar(empresaId: Long?, mes: String, pedido: Pair<String, Long>?) {
    val e = estado.value
    if (empresaId == null) return
    if (pedido != null) {
      estado.update { it.copy(empresaId = empresaId, mes = mes, regla = pedido.first) }
      cargarEjecuciones(preferir = pedido.second)
      return
    }
    if (e.empresaId == empresaId && e.mes == mes) return
    val mismaEmpresa = e.empresaId == empresaId
    estado.update { it.copy(empresaId = empresaId, mes = mes) }
    if (mismaEmpresa) elegirMasRecienteDelMes() else cargarEjecuciones()
  }

  fun elegirRegla(regla: String) {
    if (regla == estado.value.regla) return
    estado.update { it.copy(regla = regla, ejecuciones = emptyList(), ejecucionId = null, resultados = null, estatus = null, revision = FiltroRevision.TODAS, seleccion = emptySet()) }
    cargarEjecuciones()
  }

  private fun cargarEjecuciones(preferir: Long? = null) {
    val e = estado.value
    val empresa = e.empresaId ?: return
    estado.update { it.copy(cargandoEjecuciones = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargandoEjecuciones = false) } }) {
      val lista = repo.ejecuciones(empresa, e.regla)
      estado.update { it.copy(ejecuciones = lista) }
      if (preferir != null && lista.any { it.id == preferir }) elegirEjecucion(preferir) else elegirMasRecienteDelMes()
    }
  }

  /** Sin una ejecución pedida se abre la más reciente de la regla en el mes activo. */
  private fun elegirMasRecienteDelMes() {
    val e = estado.value
    val mes = e.mes ?: return
    val id = e.ejecuciones.firstOrNull { it.desde.startsWith(mes) }?.id
    if (id != null) elegirEjecucion(id)
    else estado.update { it.copy(ejecucionId = null, resultados = null, seleccion = emptySet()) }
  }

  fun elegirEjecucion(id: Long) {
    estado.update { it.copy(ejecucionId = id, estatus = null, revision = FiltroRevision.TODAS, seleccion = emptySet()) }
    recargarResultados()
  }

  private fun recargarResultados() {
    val id = estado.value.ejecucionId ?: return
    estado.update { it.copy(cargandoResultados = true) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargandoResultados = false) } }) {
      val r = repo.resultados(id)
      estado.update { it.copy(resultados = r, seleccion = it.seleccion.filter { s -> r.filas.any { f -> f.idResultado() == s } }.toSet()) }
    }
  }

  fun filtrarEstatus(s: String?) = estado.update { it.copy(estatus = s, seleccion = emptySet()) }
  fun filtrarRevision(r: FiltroRevision) = estado.update { it.copy(revision = r, seleccion = emptySet()) }

  fun alternar(id: Long) = estado.update { it.copy(seleccion = if (id in it.seleccion) it.seleccion - id else it.seleccion + id) }
  fun seleccionarVisibles() = estado.update { it.copy(seleccion = it.visibles.map { f -> f.idResultado() }.toSet()) }
  fun limpiarSeleccion() = estado.update { it.copy(seleccion = emptySet()) }

  /** Periodo de la regla suelta; por omisión, el mes activo completo. */
  fun ejecutar(desde: String, hasta: String, toleranciaImporte: String?, toleranciaDias: String?, alTerminar: () -> Unit) {
    val e = estado.value
    val empresa = e.empresaId ?: return
    estado.update { it.copy(ejecutando = true) }
    lanzar(alTerminar = { estado.update { it.copy(ejecutando = false) } }) {
      val ej = repo.ejecutar(e.regla, empresa, desde, hasta, toleranciaImporte, toleranciaDias)
      avisar("Conciliación #${ej.id}: ${ej.totalConciliadas} de ${ej.totalPartidas} conciliadas")
      cargarEjecuciones(preferir = ej.id)
      alTerminar()
    }
  }

  fun revisar(id: Long, nota: String?) = trabajo {
    repo.revisar(id, nota)
    avisar("Resultado marcado como revisado")
  }

  fun revisarSeleccion(nota: String?) {
    val ids = estado.value.seleccion.toList()
    if (ids.isEmpty()) return
    trabajo {
      val n = repo.revisarVarios(ids, nota)
      avisar("$n partida${if (n == 1L) " marcada" else "s marcadas"} como revisada${if (n == 1L) "" else "s"}")
      estado.update { it.copy(seleccion = emptySet()) }
    }
  }

  fun quitarRevisionSeleccion() {
    val f = estado.value.resultados?.filas.orEmpty().associateBy { it.idResultado() }
    val ids = estado.value.seleccion.filter { f[it]?.get("revisado_por").texto() != null }
    if (ids.isEmpty()) return
    trabajo {
      val n = repo.quitarRevision(ids)
      avisar("Revisión quitada de $n partida${if (n == 1L) "" else "s"}")
      estado.update { it.copy(seleccion = emptySet()) }
    }
  }

  private fun trabajo(bloque: suspend () -> Unit) {
    estado.update { it.copy(trabajando = true) }
    lanzar(alTerminar = { estado.update { it.copy(trabajando = false) } }) {
      bloque()
      recargarResultados()
    }
  }

  fun ocultar(motivo: String?, alTerminar: () -> Unit) {
    val id = estado.value.ejecucionId ?: return
    trabajoEjecuciones {
      repo.visibilidad(id, true, motivo)
      avisar("Ejecución #$id oculta")
      alTerminar()
    }
  }

  fun mostrar(id: Long, alTerminar: () -> Unit) = trabajoEjecuciones {
    repo.visibilidad(id, false)
    avisar("Ejecución #$id visible de nuevo")
    alTerminar()
  }

  private fun trabajoEjecuciones(bloque: suspend () -> Unit) {
    estado.update { it.copy(trabajando = true) }
    lanzar(alTerminar = { estado.update { it.copy(trabajando = false) } }) {
      bloque()
      estado.update { it.copy(ejecucionId = null, resultados = null) }
      if (estado.value.ocultas != null) estado.update { it.copy(ocultas = repo.ejecucionesOcultas()) }
      cargarEjecuciones()
    }
  }

  fun cargarOcultas() = lanzar { estado.update { it.copy(ocultas = repo.ejecucionesOcultas()) } }
  fun cerrarOcultas() = estado.update { it.copy(ocultas = null) }

  fun periodoDelMes(): Pair<String, String> = limitesMes(estado.value.mes ?: com.taxtech.mobile.ui.comun.mesAnterior())
}
