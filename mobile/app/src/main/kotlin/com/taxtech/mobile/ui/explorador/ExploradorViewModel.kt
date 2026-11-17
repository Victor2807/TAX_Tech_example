package com.taxtech.mobile.ui.explorador

import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.data.Columna
import com.taxtech.mobile.data.TablaExplorador
import com.taxtech.mobile.data.textoVisible
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.comun.fechaHora
import com.taxtech.mobile.ui.comun.monto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

const val POR_PAGINA = 25

private val MONTO = Regex("(importe|total|monto|saldo|valor|diferencia|subtotal|base|descuento|iva|isr|contribuc)")

/** Texto para mostrar el valor de una columna del explorador. */
fun valorVisible(c: Columna, v: JsonElement?): String {
  val t = v.textoVisible() ?: return "—"
  return when {
    c.esBooleano -> if (t == "true" || t == "1") "Sí" else "No"
    c.esFechaHora -> fechaHora(t)
    c.esDecimal && MONTO.containsMatchIn(c.nombre) -> t.toDoubleOrNull()?.let(::monto) ?: t
    else -> t
  }
}

/** Valor del formulario → JSON con el tipo que espera la API. null si no es válido (con su mensaje). */
fun convertir(c: Columna, texto: String): Result<JsonElement> {
  val s = texto.trim()
  if (s.isEmpty()) return if (c.requerida) Result.failure(IllegalArgumentException("${c.nombre} es obligatorio.")) else Result.success(JsonNull)
  return when {
    c.esBooleano -> Result.success(JsonPrimitive(s == "true" || s == "1"))
    c.esEntero -> s.toLongOrNull()?.let { Result.success(JsonPrimitive(it)) } ?: Result.failure(IllegalArgumentException("${c.nombre} debe ser un número entero."))
    c.esDecimal -> if (Regex("""^-?\d+(\.\d+)?$""").matches(s)) Result.success(JsonPrimitive(s)) else Result.failure(IllegalArgumentException("${c.nombre} debe ser un número (usa punto decimal)."))
    else -> Result.success(JsonPrimitive(s))
  }
}

class ExploradorViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(
    val tablas: List<TablaExplorador> = emptyList(),
    val tabla: String? = null,
    val filtros: Map<String, String> = emptyMap(),
    val orden: String? = null,
    val descendente: Boolean = false,
    val pagina: Int = 1,
    val total: Long = 0,
    val filas: List<JsonObject> = emptyList(),
    val cargando: Boolean = false,
    val guardando: Boolean = false,
    val error: String? = null,
  ) {
    val meta: TablaExplorador? get() = tablas.firstOrNull { it.nombre == tabla }
    val paginas: Int get() = maxOf(1, ((total + POR_PAGINA - 1) / POR_PAGINA).toInt())
  }

  val estado = MutableStateFlow(Estado())

  fun iniciar(pedido: Pair<String, Map<String, String>>?) {
    if (estado.value.tablas.isNotEmpty()) { pedido?.let { elegir(it.first, it.second) }; return }
    estado.update { it.copy(cargando = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m, cargando = false) } }) {
      val tablas = repo.tablas().sortedBy { it.nombre }
      estado.update { it.copy(tablas = tablas) }
      val (nombre, filtros) = pedido ?: ((tablas.firstOrNull { it.nombre == "cfdi" } ?: tablas.firstOrNull())?.nombre to emptyMap())
      if (nombre != null) elegir(nombre, filtros) else estado.update { it.copy(cargando = false) }
    }
  }

  fun elegir(nombre: String, filtros: Map<String, String> = emptyMap()) {
    estado.update { it.copy(tabla = nombre, filtros = filtros, orden = null, descendente = false, pagina = 1, filas = emptyList(), total = 0) }
    listar()
  }

  /** Columna que apunta a otra tabla por su llave simple (p. ej. empresa_id → empresa). */
  fun destinoDe(col: String): TablaExplorador? {
    val e = estado.value
    if (!col.endsWith("_id")) return null
    return e.tablas.firstOrNull { it.nombre != e.tabla && it.llaveSimple == col }
  }

  fun filtrar(col: String, valor: String) { estado.update { it.copy(filtros = it.filtros + (col to valor), pagina = 1) }; listar() }
  fun quitarFiltro(col: String) { estado.update { it.copy(filtros = it.filtros - col, pagina = 1) }; listar() }

  fun ordenar(col: String) {
    estado.update { it.copy(descendente = if (it.orden == col) !it.descendente else false, orden = col, pagina = 1) }
    listar()
  }

  fun irPagina(n: Int) {
    val e = estado.value
    if (n < 1 || n > e.paginas || n == e.pagina) return
    estado.update { it.copy(pagina = n) }
    listar()
  }

  fun listar() {
    val e = estado.value
    val tabla = e.tabla ?: return
    estado.update { it.copy(cargando = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargando = false) } }) {
      val r = repo.listar(tabla, e.filtros, e.pagina, POR_PAGINA, e.orden, e.descendente)
      estado.update { it.copy(filas = r.filas, total = r.total) }
    }
  }

  /** `fila` null = alta. Solo se envían los campos que cambiaron (en una edición) o que tienen valor (en un alta). */
  fun guardar(fila: JsonObject?, valores: Map<String, String>, alTerminar: () -> Unit): String? {
    val t = estado.value.meta ?: return "Elige una tabla."
    val cambios = mutableMapOf<String, JsonElement>()
    for (c in t.columnas) {
      if (c.soloLectura || (fila != null && c.llave)) continue
      val nuevo = valores[c.nombre] ?: continue
      val previo = fila?.get(c.nombre).textoVisible().orEmpty().let { if (c.esBooleano) (if (it == "1" || it == "true") "true" else if (it.isEmpty()) "" else "false") else it }
      if (fila != null && nuevo == previo) continue
      if (fila == null && nuevo.isBlank()) { if (c.requerida && !c.llave) return "${c.nombre} es obligatorio."; continue }
      cambios[c.nombre] = convertir(c, nuevo).getOrElse { return it.message }
    }
    if (cambios.isEmpty()) { avisar("Sin cambios"); alTerminar(); return null }
    estado.update { it.copy(guardando = true) }
    lanzar(alTerminar = { estado.update { it.copy(guardando = false) } }) {
      if (fila == null) {
        repo.crear(t.nombre, JsonObject(cambios))
        avisar("Registro creado")
      } else {
        val id = fila[t.llaveSimple ?: return@lanzar avisar("${t.nombre} tiene llave compuesta: no se edita fila por fila.")].textoVisible().orEmpty()
        repo.actualizar(t.nombre, id, JsonObject(cambios))
        avisar("Cambios guardados")
      }
      alTerminar()
      listar()
    }
    return null
  }

  fun eliminar(fila: JsonObject, alTerminar: () -> Unit) {
    val t = estado.value.meta ?: return
    val llave = t.llaveSimple ?: return
    val id = fila[llave].textoVisible() ?: return
    estado.update { it.copy(guardando = true) }
    lanzar(alTerminar = { estado.update { it.copy(guardando = false) } }) {
      repo.eliminar(t.nombre, id)
      avisar("Registro eliminado")
      alTerminar()
      listar()
    }
  }
}
