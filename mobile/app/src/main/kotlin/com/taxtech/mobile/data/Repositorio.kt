package com.taxtech.mobile.data

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

/** Una función por endpoint de la API local (ver la tabla «API» de app/CLAUDE.md en el repositorio privado). */
class Repositorio(private val api: ClienteApi) {

  // ------------------------------------------------------------ empresas y resumen
  suspend fun empresas(): List<Empresa> = api.get("/empresas").lista().map { it.aEmpresa() }

  suspend fun resumen(empresaId: Long?): Resumen = api.get("/resumen", mapOf("empresa_id" to empresaId)).aResumen()

  // ------------------------------------------------------------ explorador
  suspend fun tablas(): List<TablaExplorador> = api.get("/explorador").lista().map { it.aTablaExplorador() }

  /** `filtros`: igualdad por columna, como los parámetros de la URL del panel. */
  suspend fun listar(
    tabla: String,
    filtros: Map<String, String> = emptyMap(),
    pagina: Int = 1,
    limite: Int = 25,
    orden: String? = null,
    descendente: Boolean = false,
  ): Pagina = api.get(
    "/explorador/${seg(tabla)}",
    filtros + mapOf("pagina" to pagina, "limite" to limite, "orden" to orden, "dir" to if (descendente) "desc" else "asc"),
  ).aPagina()

  suspend fun crear(tabla: String, valores: JsonObject): JsonObject? = api.post("/explorador/${seg(tabla)}", valores).obj()

  suspend fun actualizar(tabla: String, id: String, valores: JsonObject): JsonObject? = api.patch("/explorador/${seg(tabla)}/${seg(id)}", valores).obj()

  suspend fun eliminar(tabla: String, id: String) { api.delete("/explorador/${seg(tabla)}/${seg(id)}") }

  // ------------------------------------------------------------ conciliación
  suspend fun ejecutar(regla: String, empresaId: Long, desde: String, hasta: String, toleranciaImporte: String?, toleranciaDias: String?): Ejecucion =
    api.post(
      "/ejecuciones",
      objeto(
        "regla" to regla, "empresa_id" to empresaId, "desde" to desde, "hasta" to hasta,
        "tolerancia_importe" to toleranciaImporte?.trim()?.ifEmpty { null },
        "tolerancia_dias" to toleranciaDias?.trim()?.ifEmpty { null },
      ),
    ).aEjecucion()

  suspend fun ejecutarCierre(empresaId: Long, mes: String, diasC4: Int?): ResultadoCierre =
    api.post("/cierres", objeto("empresa_id" to empresaId, "mes" to mes, "tolerancia_dias_c4" to diasC4)).aResultadoCierre()

  suspend fun ejecuciones(empresaId: Long, regla: String? = null): List<Ejecucion> =
    api.get("/ejecuciones", mapOf("empresa_id" to empresaId, "regla" to regla)).lista().map { it.aEjecucion() }

  suspend fun resultados(ejecucionId: Long): ResultadosEjecucion = api.get("/ejecuciones/$ejecucionId/resultados").aResultadosEjecucion()

  suspend fun ejecucionesOcultas(): List<EjecucionOculta> = api.get("/ejecuciones/ocultas").lista().map { it.aEjecucionOculta() }

  suspend fun visibilidad(ejecucionId: Long, ocultar: Boolean, motivo: String? = null) {
    api.patch("/ejecuciones/$ejecucionId/visibilidad", objeto("ocultar" to ocultar, "motivo" to motivo?.trim()?.ifEmpty { null }))
  }

  suspend fun revisar(resultadoId: Long, comentario: String?) {
    val nota = comentario?.trim()?.ifEmpty { null }
    api.patch("/resultados/$resultadoId/revision", JsonObject(mapOf("comentario" to (nota?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull))))
  }

  suspend fun revisarVarios(ids: Collection<Long>, comentario: String?): Long =
    api.post("/resultados/revision", objeto("resultado_ids" to ids, "comentario" to comentario?.trim()?.ifEmpty { null })).campo("revisadas").entero() ?: ids.size.toLong()

  suspend fun quitarRevision(ids: Collection<Long>): Long =
    api.post("/resultados/revision/quitar", objeto("resultado_ids" to ids)).campo("quitadas").entero() ?: ids.size.toLong()

  // ------------------------------------------------------------ tablero, alertas y comparativo
  suspend fun tableroIva(empresaId: Long, meses: Int = 12, hasta: String? = null): List<MesIva> =
    api.get("/empresas/$empresaId/tablero-iva", mapOf("meses" to meses, "hasta" to hasta)).aTableroIva()

  suspend fun alertas(empresaId: Long): Alertas = api.get("/empresas/$empresaId/alertas").aAlertas()

  suspend fun resumenesAlertas(empresaId: Long, limite: Int = 12): List<ResumenAlertas> =
    api.get("/empresas/$empresaId/resumenes-alertas", mapOf("limite" to limite)).lista().map { it.aResumenAlertas() }

  suspend fun generarResumenAlertas() { api.post("/resumenes-alertas") }

  suspend fun comparativo(empresaId: Long, meses: Int, hasta: String? = null): Comparativo =
    api.get("/empresas/$empresaId/comparativo", mapOf("meses" to meses, "hasta" to hasta)).aComparativo()

  suspend fun registrarCorte(empresaId: Long, desde: String, hasta: String, nota: String?) {
    api.post("/empresas/$empresaId/cortes", objeto("desde" to desde, "hasta" to hasta, "nota" to nota))
  }

  // ------------------------------------------------------------ cargas
  suspend fun cargas(): List<Carga> = api.get("/cargas").lista().map { it.aCarga() }

  /** `fuente`: CFDI, ERP, BANCO o DATASTAGE. Con `simular` hace todo y revierte. */
  suspend fun cargar(
    fuente: String, empresaId: Long, archivo: ArchivoSubida, simular: Boolean,
    banco: String? = null, cuenta: String? = null, saldoInicial: String? = null,
  ): ResultadoCarga = api.subir(
    "/cargas/${fuente.lowercase()}",
    mapOf(
      "empresa_id" to empresaId, "simular" to simular,
      "banco" to banco?.trim(), "cuenta" to cuenta?.trim(), "saldo_inicial" to saldoInicial?.trim(),
    ),
    archivo,
  ).aResultadoCarga()

  // ------------------------------------------------------------ accesos (solo ADMIN)
  suspend fun accesos(): Accesos = api.get("/accesos").aAccesos()

  suspend fun crearUsuario(email: String, rol: Rol, contrasena: String, empresaIds: List<Long>) {
    api.post("/accesos/usuarios", objeto("email" to email.trim().lowercase(), "rol" to rol.name, "contrasena" to contrasena, "empresa_ids" to empresaIds))
  }

  suspend fun cambiarUsuario(id: Long, rol: Rol? = null, activo: Boolean? = null) {
    api.patch("/accesos/usuarios/$id", objeto("rol" to rol?.name, "activo" to activo))
  }

  suspend fun restablecerContrasena(id: Long, contrasena: String) {
    api.post("/accesos/usuarios/$id/contrasena", objeto("contrasena" to contrasena))
  }

  suspend fun empresasDeUsuario(id: Long, empresaIds: List<Long>) {
    api.put("/accesos/usuarios/$id/empresas", objeto("empresa_ids" to empresaIds))
  }

  suspend fun crearGrupo(nombre: String) { api.post("/accesos/grupos", objeto("nombre" to nombre.trim())) }

  suspend fun crearEmpresa(rfc: String, razonSocial: String, regimen: String?, sociedadSap: String?, grupoId: Long?) {
    api.post(
      "/accesos/empresas",
      objeto(
        "rfc" to rfc.trim().uppercase(), "razon_social" to razonSocial.trim(), "regimen_fiscal" to regimen,
        "sociedad_sap" to sociedadSap?.trim()?.ifEmpty { null }, "grupo_id" to grupoId,
      ),
    )
  }

  /** Cambia el grupo de una empresa (null = sin grupo). */
  suspend fun cambiarGrupoEmpresa(empresaId: Long, grupoId: Long?) {
    api.patch("/accesos/empresas/$empresaId", JsonObject(mapOf("grupo_id" to (grupoId?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: JsonNull))))
  }

  /** Bitácora: tabla de solo lectura del explorador, visible solo para ADMIN. */
  suspend fun bitacora(pagina: Int, limite: Int = 30, filtros: Map<String, String> = emptyMap()): Pagina =
    listar("bitacora", filtros, pagina, limite, orden = "ocurrido_en", descendente = true)

  private fun seg(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
