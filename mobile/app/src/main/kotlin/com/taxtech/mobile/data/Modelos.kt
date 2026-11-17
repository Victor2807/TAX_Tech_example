package com.taxtech.mobile.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Modelos de las respuestas de la API local de TAX Tech (backend NestJS) y su lectura desde JSON.
// Siguen los tipos de `compartido/src/tipos.ts`; los importes llegan como texto.

enum class Rol(val nombre: String) {
  LECTOR("Lector"), EDITOR("Editor"), ADMIN("Admin");

  /** EDITOR y ADMIN ejecutan, revisan, cargan y editan; LECTOR solo consulta. */
  val puedeEditar: Boolean get() = this != LECTOR
  val esAdmin: Boolean get() = this == ADMIN

  companion object {
    fun de(texto: String?): Rol = entries.firstOrNull { it.name == texto?.uppercase() } ?: LECTOR
  }
}

data class Usuario(val id: Long, val email: String, val rol: Rol, val debeCambiarContrasena: Boolean = false)

data class Empresa(val id: Long, val rfc: String, val razonSocial: String, val grupoId: Long?, val grupo: String?, val sociedadSap: String?)

data class Fuentes(
  val cfdi: Long, val cfdiRecibidos: Long, val cfdiCancelados: Long, val erpDocumentos: Long,
  val movimientos: Long, val pedimentos: Long, val terceros69b: Long,
)

data class Carga(
  val id: Long, val empresaId: Long?, val fuente: String, val archivo: String, val formato: String?, val registros: Long,
  val periodoDesde: String?, val periodoHasta: String?, val cargadoEn: String?,
) {
  val nombreArchivo: String get() = archivo.split('/', '\\').last()
}

data class Ejecucion(
  val id: Long, val regla: String, val desde: String, val hasta: String, val ejecutadoEn: String?, val ejecutadoPor: String?,
  val totalPartidas: Long, val totalConciliadas: Long, val porEstatus: Map<String, Long>, val importeDiferencias: Double,
  val parametros: JsonObject?,
) {
  val porcentaje: Float get() = if (totalPartidas > 0) totalConciliadas.toFloat() / totalPartidas else 0f
}

data class Regla(val codigo: String, val nombre: String, val toleranciaImporte: String?, val toleranciaDias: String?, val activa: Boolean)

data class Resumen(
  val fuentes: Fuentes, val empresas: List<Empresa>, val cargas: List<Carga>, val ejecuciones: List<Ejecucion>,
  val reglas: List<Regla>, val ocultas: Long,
)

data class Alerta(
  val severidad: String, val categoria: String?, val titulo: String, val detalle: String?, val accion: String?,
  val fechaLimite: String?, val dias: Long?, val importe: Double?, val regla: String?, val referencia: String?,
  val revisadoPor: String?, val revisadoEn: String?,
)

data class Alertas(val hoy: String?, val conteo: Map<String, Long>, val lista: List<Alerta>) {
  val urgentes: Long get() = (conteo["critica"] ?: 0) + (conteo["alta"] ?: 0)
}

data class ResumenAlertas(
  val id: Long, val generadoEn: String?, val origen: String?, val generadoPor: String?, val fechaCorte: String?,
  val conteo: Map<String, Long>, val nuevas: List<String>, val resueltas: List<String>,
)

data class NoAcreditable(val estatus: String, val partidas: Long, val iva: Double)

data class RiesgoRegla(val regla: String, val conciliacion: String, val hallazgos: Long, val importe: Double, val ejecutada: Boolean)

data class MesIva(
  val periodoDesde: String, val periodoHasta: String, val trasladado: Double, val cobrado: Double, val pendienteCobro: Double,
  val notasEmitidas: Double, val pagado: Double, val pendientePago: Double, val acreditableNacional: Double,
  val acreditableImportacion: Double, val acreditable: Double, val notasRecibidas: Double, val saldo: Double,
  val retenido: Double, val ejecucionC5: Long?, val ejecutadoEn: String?, val reglasEjecutadas: Long,
  val noAcreditable: List<NoAcreditable>, val riesgo: List<RiesgoRegla>,
) {
  /** IVA trasladado efectivamente cobrado, neto de notas de crédito emitidas. */
  val trasladadoCobrado: Double get() = cobrado - notasEmitidas
}

data class Corrida(val ejecucionId: Long, val regla: String, val totalPartidas: Long, val totalConciliadas: Long)

data class ResultadoCierre(val desde: String, val hasta: String, val corridas: List<Corrida>)

data class PeriodoComparativo(
  val desde: String, val hasta: String, val reglasEjecutadas: Long, val corte: String?, val alCorte: Long,
  val riesgoAlCorte: Double, val cerrados: Long, val riesgoCerrado: Double, val cambiaron: Long, val nuevos: Long,
  val abiertos: Long, val riesgoAbierto: Double, val altaAbiertos: Long, val revisados: Long,
)

data class MesRegla(val periodoDesde: String, val abiertos: Long, val riesgo: Double, val cerrados: Long)

data class ReglaComparativo(val regla: String, val conciliacion: String, val meses: List<MesRegla>)

data class Seguimiento(
  val periodoDesde: String, val estado: String, val regla: String, val conciliacion: String?, val referencia: String?,
  val contraparte: String?, val estatusCorte: String?, val estatusHoy: String?, val riesgoCorte: Double?, val riesgoHoy: Double?,
  val prioridad: String?, val accion: String?, val revisadoPor: String?,
)

data class Comparativo(val periodos: List<PeriodoComparativo>, val porRegla: List<ReglaComparativo>, val detalle: List<Seguimiento>)

/** Columna del explorador: tipos de SQLite (INTEGER, TEXT, DECIMAL, BOOLEAN, DATETIME, JSONB…). */
data class Columna(val nombre: String, val tipo: String, val requerida: Boolean, val llave: Boolean, val valores: List<String>?) {
  private val t get() = tipo.uppercase()
  val esBooleano: Boolean get() = t == "BOOLEAN"
  val esEntero: Boolean get() = t == "INTEGER"
  val esDecimal: Boolean get() = t.startsWith("DECIMAL") || t == "REAL"
  val esNumero: Boolean get() = esEntero || esDecimal
  /** Fechas con zona y JSON los asigna el sistema: nunca se escriben desde el explorador. */
  val soloLectura: Boolean get() = t == "DATETIME" || t == "JSONB"
  val esFechaHora: Boolean get() = t == "DATETIME"
}

data class TablaExplorador(val nombre: String, val columnas: List<Columna>, val editable: Boolean, val borrable: Boolean) {
  val llaves: List<String> get() = columnas.filter { it.llave }.map { it.nombre }
  /** Editar y borrar van por /:id: solo tablas con llave simple. */
  val llaveSimple: String? get() = llaves.singleOrNull()
}

data class Pagina(val total: Long, val pagina: Int, val limite: Int, val filas: List<JsonObject>)

data class ResultadosEjecucion(val ejecucion: Ejecucion, val empresaId: Long?, val filas: List<JsonObject>)

data class EjecucionOculta(
  val id: Long, val regla: String, val desde: String, val hasta: String, val totalPartidas: Long, val totalConciliadas: Long,
  val ocultaEn: String?, val ocultaPor: String?, val motivo: String?,
)

data class ResultadoCarga(
  val cargaId: Long?, val simulado: Boolean, val fuente: String, val archivo: String, val formato: String?, val registros: Long,
  val periodoDesde: String?, val periodoHasta: String?, val detalle: Map<String, Long>, val avisos: List<String>, val errores: List<String>,
)

data class UsuarioAcceso(val id: Long, val email: String, val rol: Rol, val activo: Boolean, val debeCambiarContrasena: Boolean, val empresas: List<Long>)

data class Grupo(val id: Long, val nombre: String, val empresas: Long)

data class Accesos(val usuarios: List<UsuarioAcceso>, val empresas: List<Empresa>, val grupos: List<Grupo>)

/** Catálogo c_RegimenFiscal del SAT (CFDI 4.0), igual que REGIMENES_FISCALES de compartido/dominio.ts. */
val REGIMENES_FISCALES: Map<String, String> = linkedMapOf(
  "601" to "General de Ley Personas Morales",
  "603" to "Personas Morales con Fines no Lucrativos",
  "605" to "Sueldos y Salarios e Ingresos Asimilados a Salarios",
  "606" to "Arrendamiento",
  "607" to "Régimen de Enajenación o Adquisición de Bienes",
  "608" to "Demás ingresos",
  "610" to "Residentes en el Extranjero sin Establecimiento Permanente en México",
  "611" to "Ingresos por Dividendos (socios y accionistas)",
  "612" to "Personas Físicas con Actividades Empresariales y Profesionales",
  "614" to "Ingresos por intereses",
  "615" to "Régimen de los ingresos por obtención de premios",
  "616" to "Sin obligaciones fiscales",
  "620" to "Sociedades Cooperativas de Producción que optan por diferir sus ingresos",
  "621" to "Incorporación Fiscal",
  "622" to "Actividades Agrícolas, Ganaderas, Silvícolas y Pesqueras",
  "623" to "Opcional para Grupos de Sociedades",
  "624" to "Coordinados",
  "625" to "Régimen de las Actividades Empresariales con ingresos a través de Plataformas Tecnológicas",
  "626" to "Régimen Simplificado de Confianza",
)

val FORMATO_RFC = Regex("^[A-ZÑ&]{3,4}\\d{6}[A-Z\\d]{3}$")

// ---------------------------------------------------------------- lectura

private fun JsonElement?.txt(k: String) = campo(k).texto()
private fun JsonElement?.num(k: String) = campo(k).numero() ?: 0.0
private fun JsonElement?.ent(k: String) = campo(k).entero() ?: 0L
private fun JsonElement?.textos(k: String) = campo(k).lista().mapNotNull { it.textoVisible() }

fun JsonElement.aUsuario() = Usuario(ent("usuario_id"), txt("email").orEmpty(), Rol.de(txt("rol")), campo("debe_cambiar_contrasena").booleano() ?: false)

fun JsonElement.aEmpresa() = Empresa(
  ent("empresa_id"), txt("rfc").orEmpty(), txt("razon_social").orEmpty(), campo("grupo_id").entero(), txt("grupo"), txt("sociedad_sap"),
)

fun JsonElement.aCarga() = Carga(
  ent("carga_id"), campo("empresa_id").entero(), txt("fuente").orEmpty(), txt("archivo").orEmpty(), txt("formato"),
  ent("registros"), txt("periodo_desde"), txt("periodo_hasta"), txt("cargado_en"),
)

fun JsonElement.aEjecucion() = Ejecucion(
  ent("ejecucion_id"), txt("regla_codigo").orEmpty(), txt("periodo_desde").orEmpty(), txt("periodo_hasta").orEmpty(),
  txt("ejecutado_en"), txt("ejecutado_por"), ent("total_partidas"), ent("total_conciliadas"),
  campo("por_estatus").obj()?.mapaEnteros().orEmpty(), num("importe_diferencias"), campo("parametros").obj(),
)

fun JsonElement.aResumen(): Resumen {
  val f = campo("fuentes")
  return Resumen(
    fuentes = Fuentes(
      f.ent("cfdi"), f.ent("cfdi_recibidos"), f.ent("cfdi_cancelados"), f.ent("erp_documentos"),
      f.ent("movimientos"), f.ent("pedimentos"), f.ent("terceros_69b"),
    ),
    empresas = campo("empresas").lista().map { it.aEmpresa() },
    cargas = campo("cargas").lista().map { it.aCarga() },
    ejecuciones = campo("ejecuciones").lista().map { it.aEjecucion() },
    reglas = campo("reglas").lista().map {
      Regla(it.txt("regla_codigo").orEmpty(), it.txt("nombre").orEmpty(), it.txt("tolerancia_importe"), it.txt("tolerancia_dias"), it.campo("activa").booleano() ?: true)
    },
    ocultas = ent("ocultas"),
  )
}

fun JsonElement.aAlertas() = Alertas(
  hoy = txt("hoy"),
  conteo = campo("resumen").obj()?.mapaEnteros().orEmpty(),
  lista = campo("alertas").lista().map {
    Alerta(
      it.txt("severidad") ?: "info", it.txt("categoria"), it.txt("titulo").orEmpty(), it.txt("detalle"), it.txt("accion"),
      it.txt("fecha_limite"), it.campo("dias").entero(), it.campo("importe").numero(), it.txt("regla"), it.txt("referencia"),
      it.txt("revisado_por"), it.txt("revisado_en"),
    )
  },
)

fun JsonElement.aResumenAlertas() = ResumenAlertas(
  ent("resumen_id"), txt("generado_en"), txt("origen"), txt("generado_por"), txt("fecha_corte"),
  campo("conteo").obj()?.mapaEnteros().orEmpty(), textos("nuevas"), textos("resueltas"),
)

fun JsonElement.aMesIva() = MesIva(
  periodoDesde = txt("periodo_desde").orEmpty().take(10), periodoHasta = txt("periodo_hasta").orEmpty().take(10),
  trasladado = num("trasladado"), cobrado = num("cobrado"), pendienteCobro = num("pendiente_cobro"),
  notasEmitidas = num("notas_emitidas"), pagado = num("pagado"), pendientePago = num("pendiente_pago"),
  acreditableNacional = num("acreditable_nacional"), acreditableImportacion = num("acreditable_importacion"),
  acreditable = num("acreditable"), notasRecibidas = num("notas_recibidas"), saldo = num("saldo"), retenido = num("retenido"),
  ejecucionC5 = campo("ejecucion_c5").entero(), ejecutadoEn = txt("ejecutado_en"), reglasEjecutadas = ent("reglas_ejecutadas"),
  noAcreditable = campo("no_acreditable").lista().map { NoAcreditable(it.txt("estatus").orEmpty(), it.ent("partidas"), it.num("iva")) },
  riesgo = campo("riesgo_por_regla").lista().map {
    RiesgoRegla(it.txt("regla").orEmpty(), it.txt("conciliacion").orEmpty(), it.ent("hallazgos"), it.num("importe_riesgo"), it.campo("ejecutada").booleano() ?: true)
  },
)

fun JsonElement.aTableroIva(): List<MesIva> = campo("meses").lista().map { it.aMesIva() }

fun JsonElement.aResultadoCierre() = ResultadoCierre(
  txt("desde").orEmpty(), txt("hasta").orEmpty(),
  campo("corridas").lista().map { Corrida(it.ent("ejecucion_id"), it.txt("regla_codigo").orEmpty(), it.ent("total_partidas"), it.ent("total_conciliadas")) },
)

fun JsonElement.aComparativo() = Comparativo(
  periodos = campo("periodos").lista().map {
    PeriodoComparativo(
      it.txt("periodo_desde").orEmpty().take(10), it.txt("periodo_hasta").orEmpty().take(10), it.ent("reglas_ejecutadas"), it.txt("corte"),
      it.ent("al_corte"), it.num("riesgo_al_corte"), it.ent("cerrados"), it.num("riesgo_cerrado"), it.ent("cambiaron"),
      it.ent("nuevos"), it.ent("abiertos"), it.num("riesgo_abierto"), it.ent("alta_abiertos"), it.ent("revisados"),
    )
  },
  porRegla = campo("por_regla").lista().map { r ->
    ReglaComparativo(
      r.txt("regla").orEmpty(), r.txt("conciliacion").orEmpty(),
      r.campo("meses").lista().map { MesRegla(it.txt("periodo_desde").orEmpty().take(10), it.ent("abiertos"), it.num("riesgo"), it.ent("cerrados")) },
    )
  },
  detalle = campo("detalle").lista().map {
    Seguimiento(
      it.txt("periodo_desde").orEmpty().take(10), it.txt("estado").orEmpty(), it.txt("regla").orEmpty(), it.txt("conciliacion"),
      it.txt("referencia"), it.txt("contraparte"), it.txt("estatus_corte"), it.txt("estatus_hoy"), it.campo("riesgo_corte").numero(),
      it.campo("riesgo_hoy").numero(), it.txt("prioridad"), it.txt("accion"), it.txt("revisado_por"),
    )
  },
)

fun JsonElement.aTablaExplorador() = TablaExplorador(
  nombre = txt("nombre").orEmpty(),
  columnas = campo("columnas").lista().map {
    Columna(
      it.txt("nombre").orEmpty(), it.txt("tipo").orEmpty(), it.campo("requerida").booleano() ?: false,
      it.campo("llave").booleano() ?: false, it.campo("valores").lista().mapNotNull { v -> v.texto() }.ifEmpty { null },
    )
  },
  editable = campo("editable").booleano() ?: false,
  borrable = campo("borrable").booleano() ?: false,
)

fun JsonElement.aPagina() = Pagina(
  ent("total"), campo("pagina").entero()?.toInt() ?: 1, campo("limite").entero()?.toInt() ?: 50, campo("filas").lista().mapNotNull { it.obj() },
)

fun JsonElement.aResultadosEjecucion(): ResultadosEjecucion {
  val e = campo("ejecucion")
  return ResultadosEjecucion(e?.aEjecucion() ?: error("Respuesta sin ejecución"), e.campo("empresa_id").entero(), campo("filas").lista().mapNotNull { it.obj() })
}

fun JsonElement.aEjecucionOculta() = EjecucionOculta(
  ent("ejecucion_id"), txt("regla_codigo").orEmpty(), txt("periodo_desde").orEmpty(), txt("periodo_hasta").orEmpty(),
  ent("total_partidas"), ent("total_conciliadas"), txt("oculta_en"), txt("oculta_por"), txt("motivo_oculta"),
)

fun JsonElement.aResultadoCarga() = ResultadoCarga(
  campo("carga_id").entero(), campo("simulado").booleano() ?: false, txt("fuente").orEmpty(), txt("archivo").orEmpty(), txt("formato"),
  ent("registros"), txt("periodo_desde"), txt("periodo_hasta"), campo("detalle").obj()?.mapaEnteros().orEmpty(),
  textos("avisos"), textos("errores"),
)

fun JsonElement.aAccesos() = Accesos(
  usuarios = campo("usuarios").lista().map {
    UsuarioAcceso(
      it.ent("usuario_id"), it.txt("email").orEmpty(), Rol.de(it.txt("rol")), it.campo("activo").booleano() ?: true,
      it.campo("debe_cambiar_contrasena").booleano() ?: false, it.campo("empresas").lista().mapNotNull { x -> x.entero() },
    )
  },
  empresas = campo("empresas").lista().map { it.aEmpresa() },
  grupos = campo("grupos").lista().map { Grupo(it.ent("grupo_id"), it.txt("nombre").orEmpty(), it.ent("empresas")) },
)
