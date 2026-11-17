package com.taxtech.mobile.ui.cargas

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.ArchivoSubida
import com.taxtech.mobile.data.Carga
import com.taxtech.mobile.data.ResultadoCarga
import com.taxtech.mobile.data.textoVisible
import com.taxtech.mobile.ui.comun.AvisoError
import com.taxtech.mobile.ui.comun.BotonPrincipal
import com.taxtech.mobile.ui.comun.BotonSecundario
import com.taxtech.mobile.ui.comun.COLOR_FUENTE
import com.taxtech.mobile.ui.comun.Cargando
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.Dato
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.NOMBRE_FUENTE
import com.taxtech.mobile.ui.comun.Pastilla
import com.taxtech.mobile.ui.comun.SOLO_LECTURA
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.TituloTarjeta
import com.taxtech.mobile.ui.comun.Vacio
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.comun.entero
import com.taxtech.mobile.ui.comun.fecha
import com.taxtech.mobile.ui.comun.fechaHora
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Fuentes que acepta POST /api/cargas/:fuente, con los formatos que lee cada lector. */
enum class Fuente(val clave: String, val nombre: String, val formatos: String, val tipos: Array<String>) {
  CFDI("CFDI", "CFDI", "XML, ZIP de la descarga masiva o metadata", arrayOf("text/xml", "application/xml", "application/zip", "text/plain", "application/octet-stream")),
  ERP("ERP", "SAP", "CSV o TXT delimitado", arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")),
  BANCO("BANCO", "Banco", "MT940, CAMT.053 o CSV", arrayOf("text/plain", "text/xml", "application/xml", "text/csv", "application/octet-stream")),
  DATASTAGE("DATASTAGE", "Data Stage", "ZIP de pedimentos o un registro suelto", arrayOf("application/zip", "text/plain", "application/octet-stream")),
}

data class ArchivoElegido(val uri: Uri, val nombre: String, val tamano: Long, val tipo: String)

fun leerArchivo(context: Context, uri: Uri): ArchivoElegido {
  var nombre = uri.lastPathSegment ?: "archivo"
  var tamano = -1L
  context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
    if (c.moveToFirst()) {
      c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { nombre = c.getString(it) ?: nombre }
      c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { tamano = c.getLong(it) }
    }
  }
  return ArchivoElegido(uri, nombre, tamano, context.contentResolver.getType(uri) ?: "application/octet-stream")
}

private fun tamanoLegible(b: Long) = when {
  b < 0 -> "tamaño desconocido"
  b < 1024 -> "$b B"
  b < 1024 * 1024 -> "${b / 1024} KB"
  else -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
}

private const val LIMITE_BYTES = 50L * 1024 * 1024

class CargasViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(
    val historial: List<Carga> = emptyList(),
    val filtro: String? = null,
    val cargandoHistorial: Boolean = false,
    val error: String? = null,
    val subiendo: Boolean = false,
    val resultado: ResultadoCarga? = null,
    /** Archivo que pasó la simulación sin errores: solo ese se puede confirmar. */
    val simuladoOk: Uri? = null,
    val cuentas: List<Pair<String, String>> = emptyList(),
  )

  val estado = MutableStateFlow(Estado())
  private var empresaCuentas: Long? = null

  fun cargarHistorial(forzar: Boolean = false) {
    if (!forzar && estado.value.historial.isNotEmpty()) return
    estado.update { it.copy(cargandoHistorial = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargandoHistorial = false) } }) {
      estado.update { it.copy(historial = repo.cargas()) }
    }
  }

  /** Sugerencias de banco y cuenta de la empresa (tabla cuenta_bancaria). */
  fun cargarCuentas(empresaId: Long?) {
    if (empresaId == null || empresaId == empresaCuentas) return
    empresaCuentas = empresaId
    lanzar(alFallar = {}) {
      val p = repo.listar("cuenta_bancaria", mapOf("empresa_id" to empresaId.toString()), limite = 50)
      estado.update { it.copy(cuentas = p.filas.mapNotNull { f -> f["banco"].textoVisible()?.let { b -> b to (f["numero_cuenta"].textoVisible() ?: f["clabe"].textoVisible() ?: "") } }) }
    }
  }

  fun filtrar(f: String?) = estado.update { it.copy(filtro = f) }

  fun olvidarResultado() = estado.update { it.copy(resultado = null, simuladoOk = null) }

  fun subir(
    context: Context, fuente: Fuente, empresaId: Long, archivo: ArchivoElegido, simular: Boolean,
    banco: String?, cuenta: String?, saldoInicial: String?, alTerminar: () -> Unit,
  ) {
    if (archivo.tamano > LIMITE_BYTES) { avisar("El archivo pesa más de 50 MB, el máximo que acepta la API."); return }
    if (fuente == Fuente.BANCO && banco.isNullOrBlank()) { avisar("Indica el banco del estado de cuenta."); return }
    estado.update { it.copy(subiendo = true, resultado = null) }
    val subida = ArchivoSubida(archivo.nombre, archivo.tipo, archivo.tamano) {
      context.contentResolver.openInputStream(archivo.uri) ?: error("No se pudo abrir el archivo.")
    }
    lanzar(alTerminar = { estado.update { it.copy(subiendo = false) } }) {
      val r = repo.cargar(
        fuente.clave, empresaId, subida, simular,
        banco = banco.takeIf { fuente == Fuente.BANCO }?.ifBlank { null },
        cuenta = cuenta.takeIf { fuente == Fuente.BANCO }?.ifBlank { null },
        saldoInicial = saldoInicial.takeIf { fuente == Fuente.BANCO }?.ifBlank { null },
      )
      estado.update { it.copy(resultado = r, simuladoOk = if (simular && r.errores.isEmpty()) archivo.uri else null) }
      if (!simular) {
        avisar("Carga #${r.cargaId} registrada: ${entero(r.registros)} registros")
        cargarHistorial(forzar = true)
        alTerminar()
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CargasScreen(panel: PanelViewModel) {
  val c = LocalContenedor.current
  val contexto = LocalContext.current
  val vm: CargasViewModel = viewModel(key = "cargas") { CargasViewModel(c) }
  val p by panel.estado.collectAsStateWithLifecycle()
  val e by vm.estado.collectAsStateWithLifecycle()
  val editor = panel.usuario.rol.puedeEditar
  LaunchedEffect(Unit) { vm.cargarHistorial() }
  LaunchedEffect(p.empresaId) { if (editor) vm.cargarCuentas(p.empresaId) }

  var fuente by remember { mutableStateOf(Fuente.CFDI) }
  var archivo by remember { mutableStateOf<ArchivoElegido?>(null) }
  var banco by remember { mutableStateOf("") }
  var cuenta by remember { mutableStateOf("") }
  var saldo by remember { mutableStateOf("") }
  val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) { archivo = leerArchivo(contexto, uri); vm.olvidarResultado() }
  }
  val historial = e.historial.filter { it.empresaId == null || it.empresaId == p.empresaId }.filter { e.filtro == null || it.fuente == e.filtro }

  PullToRefreshBox(isRefreshing = e.cargandoHistorial && e.historial.isNotEmpty(), onRefresh = { vm.cargarHistorial(forzar = true) }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        Tarjeta(Modifier.animateContentSize()) {
          TituloTarjeta("Subir archivo", p.empresa?.let { "A ${it.razonSocial} · ${it.rfc}" } ?: "Elige una empresa arriba", Iconos.Subir)
          Spacer(Modifier.height(12.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fuente.entries.forEach { f ->
              val activo = f == fuente
              val color = COLOR_FUENTE[f.clave] ?: MaterialTheme.colorScheme.primary
              Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                  .background(color.copy(alpha = if (activo) 0.22f else 0.07f))
                  .border(1.dp, if (activo) color else color.copy(alpha = 0f), RoundedCornerShape(16.dp))
                  .clickable { fuente = f; archivo = null; vm.olvidarResultado() }
                  .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
              ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.height(6.dp))
                Text(f.nombre, style = MaterialTheme.typography.labelLarge, maxLines = 1)
              }
            }
          }
          Spacer(Modifier.height(6.dp))
          Text(fuente.formatos, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Spacer(Modifier.height(12.dp))
          ZonaArchivo(archivo, enabled = editor) { elegir.launch(fuente.tipos) }
          if (fuente == Fuente.BANCO) {
            Spacer(Modifier.height(12.dp))
            if (e.cuentas.isNotEmpty()) {
              LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(e.cuentas) { (b, cta) ->
                  Text(
                    "$b ${cta.takeLast(4).let { if (it.isNotEmpty()) "··$it" else "" }}", style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).clickable { banco = b; cuenta = cta }.padding(horizontal = 12.dp, vertical = 6.dp),
                  )
                }
              }
              Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(banco, { banco = it.take(100) }, label = { Text("Banco *") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(cuenta, { cuenta = it.take(34) }, label = { Text("Cuenta o CLABE") }, supportingText = { Text("Si el archivo no la trae") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
              OutlinedTextField(
                saldo, { saldo = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' || ch == ',' } }, label = { Text("Saldo inicial") },
                supportingText = { Text("CSV sin saldos") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
              )
            }
          }
          Spacer(Modifier.height(12.dp))
          val a = archivo
          val listoParaConfirmar = a != null && e.simuladoOk == a.uri
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            BotonSecundario(
              "Simular", {
                if (a != null && p.empresaId != null) vm.subir(contexto, fuente, p.empresaId!!, a, true, banco, cuenta, saldo) {}
              },
              enabled = editor && a != null && p.empresaId != null && !e.subiendo, icono = Iconos.Ojo,
            )
            BotonPrincipal(
              "Confirmar carga", {
                if (a != null && p.empresaId != null) vm.subir(contexto, fuente, p.empresaId!!, a, false, banco, cuenta, saldo) { archivo = null; panel.recargar() }
              },
              enabled = editor && listoParaConfirmar, cargando = e.subiendo, icono = Iconos.Subir, modifier = Modifier.weight(1f),
            )
          }
          Text(
            if (!editor) SOLO_LECTURA else "Primero simula: la API lee todo el archivo y revierte. Si no hay errores, confirma la carga.",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
      e.resultado?.let { r -> item { TarjetaResultado(r) } }
      item {
        Column {
          Ceja("Historial de la empresa", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
          LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { FiltroFuente("Todas", e.filtro == null, MaterialTheme.colorScheme.primary) { vm.filtrar(null) } }
            items(Fuente.entries) { f -> FiltroFuente(f.nombre, e.filtro == f.clave, COLOR_FUENTE[f.clave] ?: MaterialTheme.colorScheme.primary) { vm.filtrar(f.clave) } }
          }
        }
      }
      if (e.error != null) item { AvisoError(e.error!!) { vm.cargarHistorial(forzar = true) } }
      if (e.cargandoHistorial && e.historial.isEmpty()) item { Cargando() }
      if (!e.cargandoHistorial && historial.isEmpty() && e.error == null) item { Tarjeta { Vacio("Sin cargas", "Aún no hay archivos cargados para esta empresa con este filtro.", icono = Iconos.Archivo) } }
      items(historial, key = { it.id }) { cg -> NodoCarga(cg, cg == historial.last()) }
    }
  }
}

@Composable
private fun ZonaArchivo(a: ArchivoElegido?, enabled: Boolean, elegir: () -> Unit) {
  val c = LocalColoresTax.current
  val primario = MaterialTheme.colorScheme.primary
  Column(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
      .background(primario.copy(alpha = 0.05f))
      .drawBehindDiscontinuo(if (a != null) primario else c.eje)
      .clickable(enabled = enabled, onClick = elegir)
      .padding(18.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Icon(if (a != null) Iconos.Archivo else Iconos.Subir, null, tint = primario, modifier = Modifier.size(28.dp))
    Spacer(Modifier.height(8.dp))
    if (a == null) {
      Text("Toca para elegir el archivo", style = MaterialTheme.typography.titleSmall)
      Text("Hasta 50 MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
      Text(a.nombre, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(tamanoLegible(a.tamano) + " · toca para cambiarlo", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

/** Borde punteado de la zona para elegir el archivo. */
private fun Modifier.drawBehindDiscontinuo(color: androidx.compose.ui.graphics.Color) = drawBehind {
  drawRoundRect(
    color = color,
    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
    cornerRadius = CornerRadius(18.dp.toPx()),
  )
}

@Composable
private fun TarjetaResultado(r: ResultadoCarga) {
  val c = LocalColoresTax.current
  val ok = r.errores.isEmpty()
  Tarjeta(acento = if (!ok) c.peligro else if (r.simulado) c.aviso else c.exito) {
    TituloTarjeta(
      if (r.simulado) (if (ok) "Simulación correcta" else "La simulación encontró errores") else "Carga registrada",
      r.archivo + (r.formato?.let { " · $it" } ?: ""),
      if (ok) Iconos.Palomita else Iconos.Alerta,
    )
    Spacer(Modifier.height(10.dp))
    Dato("Registros", entero(r.registros), destacado = true)
    if (r.periodoDesde != null) Dato("Periodo", "${fecha(r.periodoDesde)} al ${fecha(r.periodoHasta)}")
    r.detalle.forEach { (k, v) -> Dato(k.replace('_', ' '), entero(v)) }
    if (r.simulado) Text("No se guardó nada: fue una simulación.", style = MaterialTheme.typography.labelSmall, color = c.aviso, modifier = Modifier.padding(top = 6.dp))
    r.avisos.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.aviso, modifier = Modifier.padding(top = 4.dp)) }
    r.errores.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = c.peligro, modifier = Modifier.padding(top = 4.dp)) }
  }
}

@Composable
private fun FiltroFuente(texto: String, activo: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
  Text(
    texto, style = MaterialTheme.typography.labelMedium,
    modifier = Modifier.clip(CircleShape).background(color.copy(alpha = if (activo) 0.25f else 0.07f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
  )
}

/** Línea de tiempo: un nodo por carga, con el color de su fuente. */
@Composable
private fun NodoCarga(cg: Carga, ultimo: Boolean) {
  val color = COLOR_FUENTE[cg.fuente] ?: MaterialTheme.colorScheme.primary
  val linea = LocalColoresTax.current.rejilla
  Row(Modifier.fillMaxWidth()) {
    Box(Modifier.width(24.dp).height(92.dp)) {
      if (!ultimo) Box(Modifier.align(Alignment.TopCenter).padding(top = 14.dp).width(2.dp).height(92.dp).background(linea))
      Box(Modifier.align(Alignment.TopCenter).padding(top = 6.dp).size(12.dp).clip(CircleShape).background(color))
    }
    Spacer(Modifier.width(8.dp))
    Tarjeta(Modifier.weight(1f), relleno = PaddingValues(12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Pastilla(NOMBRE_FUENTE[cg.fuente] ?: cg.fuente, color)
        Spacer(Modifier.weight(1f))
        Text(entero(cg.registros) + " reg.", style = MaterialTheme.typography.labelLarge.merge(Cifras))
      }
      Spacer(Modifier.height(4.dp))
      Text(cg.nombreArchivo, style = MaterialTheme.typography.bodySmall.merge(Mono), maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        listOfNotNull(cg.formato, cg.periodoDesde?.let { "${fecha(it)} al ${fecha(cg.periodoHasta)}" }, fechaHora(cg.cargadoEn)).joinToString(" · "),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
      )
    }
  }
}
