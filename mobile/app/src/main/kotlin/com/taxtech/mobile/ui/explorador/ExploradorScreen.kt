package com.taxtech.mobile.ui.explorador

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.Columna
import com.taxtech.mobile.data.TablaExplorador
import com.taxtech.mobile.data.textoVisible
import com.taxtech.mobile.ui.comun.AvisoError
import com.taxtech.mobile.ui.comun.BotonPrincipal
import com.taxtech.mobile.ui.comun.BotonSecundario
import com.taxtech.mobile.ui.comun.Cargando
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.ChipEstatus
import com.taxtech.mobile.ui.comun.DialogoConfirmar
import com.taxtech.mobile.ui.comun.ESTATUS
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.Pastilla
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.Vacio
import com.taxtech.mobile.ui.comun.entero
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Mono
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploradorScreen(panel: PanelViewModel) {
  val c = LocalContenedor.current
  val vm: ExploradorViewModel = viewModel(key = "explorador") { ExploradorViewModel(c) }
  val e by vm.estado.collectAsStateWithLifecycle()
  val pedido by panel.tablaPedida.collectAsStateWithLifecycle()
  LaunchedEffect(pedido) {
    vm.iniciar(pedido)
    if (pedido != null) panel.tablaPedida.value = null
  }
  var elegirTabla by remember { mutableStateOf(false) }
  var nuevoFiltro by remember { mutableStateOf(false) }
  var elegirOrden by remember { mutableStateOf(false) }
  var detalle by remember { mutableStateOf<JsonObject?>(null) }
  var editar by remember { mutableStateOf<Pair<JsonObject?, Boolean>?>(null) }
  val t = e.meta

  LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item {
      Tarjeta(onClick = { elegirTabla = true }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Iconos.Tabla, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
          Spacer(Modifier.width(12.dp))
          Column(Modifier.weight(1f)) {
            Ceja("Tabla")
            Text(e.tabla ?: "Elige una tabla", style = Mono.merge(MaterialTheme.typography.titleMedium))
            if (t != null) Text("${entero(e.total)} registro${if (e.total == 1L) "" else "s"} · ${t.columnas.size} columnas" + if (t.editable) "" else " · solo lectura", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Icon(Iconos.Abajo, "Cambiar de tabla", modifier = Modifier.size(20.dp))
        }
      }
    }
    if (t != null) {
      item {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
          e.filtros.forEach { (col, v) ->
            Row(
              Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)).clickable { vm.quitarFiltro(col) }.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text("$col = $v", style = Mono.merge(MaterialTheme.typography.labelMedium))
              Icon(Iconos.Cerrar, "Quitar filtro", modifier = Modifier.padding(start = 4.dp).size(14.dp))
            }
          }
          BotonSecundario("Filtro", { nuevoFiltro = true }, icono = Iconos.Filtro)
          BotonSecundario(e.orden?.let { "$it ${if (e.descendente) "▼" else "▲"}" } ?: "Orden", { elegirOrden = true }, icono = Iconos.Ordenar)
          if (t.editable) BotonSecundario("Nuevo", { editar = null to true }, icono = Iconos.Mas)
        }
      }
    }
    if (e.error != null) item { AvisoError(e.error!!, reintentar = vm::listar) }
    if (e.cargando && e.filas.isEmpty()) item { Cargando() }
    if (!e.cargando && t != null && e.filas.isEmpty() && e.error == null) item { Tarjeta { Vacio("Sin registros", "No hay registros con estos filtros.", Iconos.Buscar) } }
    if (t != null) {
      items(e.filas, key = { f -> t.llaves.joinToString("|") { k -> f[k].textoVisible().orEmpty() }.ifEmpty { f.hashCode().toString() } }) { f ->
        FilaRegistro(t, f) { detalle = f }
      }
    }
    if (e.total > POR_PAGINA) {
      item {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
          val a = (e.pagina - 1) * POR_PAGINA + 1
          val b = minOf(e.total, (e.pagina * POR_PAGINA).toLong())
          Text("$a–$b de ${entero(e.total)}", style = MaterialTheme.typography.labelMedium.merge(Cifras), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
          BotonSecundario("Anterior", { vm.irPagina(e.pagina - 1) }, enabled = e.pagina > 1, icono = Iconos.Izquierda)
          Spacer(Modifier.width(8.dp))
          BotonSecundario("Siguiente", { vm.irPagina(e.pagina + 1) }, enabled = e.pagina < e.paginas, icono = Iconos.Derecha)
        }
      }
    }
  }

  if (elegirTabla) {
    HojaTablas(e.tablas, e.tabla, { elegirTabla = false }) { vm.elegir(it); elegirTabla = false }
  }
  if (nuevoFiltro && t != null) {
    DialogoFiltro(t, { nuevoFiltro = false }) { col, v -> vm.filtrar(col, v) }
  }
  if (elegirOrden && t != null) {
    ModalBottomSheet(onDismissRequest = { elegirOrden = false }, containerColor = MaterialTheme.colorScheme.surface) {
      Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
        Text("Ordenar por", style = MaterialTheme.typography.titleLarge)
        t.columnas.forEach { col ->
          Row(Modifier.fillMaxWidth().clickable { vm.ordenar(col.nombre); elegirOrden = false }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(col.nombre, style = Mono.merge(MaterialTheme.typography.bodyMedium), modifier = Modifier.weight(1f))
            if (e.orden == col.nombre) Text(if (e.descendente) "▼ descendente" else "▲ ascendente", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
  }
  detalle?.let { f ->
    if (t != null) {
      HojaDetalle(
        t, f, vm::destinoDe,
        alSeguir = { tabla, col, v -> detalle = null; vm.elegir(tabla, mapOf(col to v)) },
        alEditar = { detalle = null; editar = f to false },
        alEliminar = { vm.eliminar(f) { detalle = null } },
        alCerrar = { detalle = null },
      )
    }
  }
  editar?.let { (fila, _) ->
    if (t != null) HojaEdicion(t, fila, e.guardando, { editar = null }) { valores -> vm.guardar(fila, valores) { editar = null } }
  }
}

@Composable
private fun FilaRegistro(t: TablaExplorador, f: JsonObject, abrir: () -> Unit) {
  val llaves = t.columnas.filter { it.llave }
  val resto = t.columnas.filter { !it.llave && f[it.nombre].textoVisible() != null }.take(4)
  Tarjeta(onClick = abrir, relleno = PaddingValues(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      llaves.forEach { k -> Pastilla("${k.nombre} ${f[k.nombre].textoVisible() ?: "—"}", MaterialTheme.colorScheme.secondary, mono = true); Spacer(Modifier.width(6.dp)) }
      Spacer(Modifier.weight(1f))
      Icon(Iconos.Derecha, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
    Spacer(Modifier.height(6.dp))
    resto.forEach { col ->
      Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(col.nombre, style = MaterialTheme.typography.labelSmall.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(130.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (col.nombre == "estatus" && ESTATUS.containsKey(f[col.nombre].textoVisible())) ChipEstatus(f[col.nombre].textoVisible())
        else Text(valorVisible(col, f[col.nombre]), style = MaterialTheme.typography.bodySmall.merge(Cifras), maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaTablas(tablas: List<TablaExplorador>, actual: String?, cerrar: () -> Unit, elegir: (String) -> Unit) {
  var buscar by remember { mutableStateOf("") }
  ModalBottomSheet(onDismissRequest = cerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding()) {
      Text("Tablas", style = MaterialTheme.typography.titleLarge)
      Spacer(Modifier.height(10.dp))
      OutlinedTextField(
        buscar, { buscar = it }, placeholder = { Text("Buscar tabla") }, singleLine = true,
        leadingIcon = { Icon(Iconos.Buscar, null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
      )
      Spacer(Modifier.height(8.dp))
      LazyColumn(Modifier.heightIn(max = 520.dp)) {
        items(tablas.filter { it.nombre.contains(buscar.trim(), ignoreCase = true) }, key = { it.nombre }) { t ->
          Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { elegir(t.nombre) }.padding(vertical = 12.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t.nombre, style = Mono.merge(MaterialTheme.typography.bodyMedium), color = if (t.nombre == actual) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            if (!t.editable) Pastilla("lectura", MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
    }
  }
}

@Composable
private fun DialogoFiltro(t: TablaExplorador, cerrar: () -> Unit, aplicar: (String, String) -> Unit) {
  var col by remember { mutableStateOf(t.columnas.first()) }
  var valor by remember { mutableStateOf("") }
  AlertDialog(
    onDismissRequest = cerrar,
    title = { Text("Filtrar por igualdad") },
    text = {
      Column {
        Text("Columna", style = MaterialTheme.typography.labelMedium)
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          t.columnas.forEach { c ->
            Text(
              c.nombre, style = Mono.merge(MaterialTheme.typography.labelSmall),
              modifier = Modifier.clip(CircleShape)
                .background(if (c == col) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable { col = c; valor = "" }.padding(horizontal = 10.dp, vertical = 6.dp),
            )
          }
        }
        Spacer(Modifier.height(12.dp))
        val opciones = col.valores ?: if (col.esBooleano) listOf("1", "0") else null
        if (opciones != null) {
          @OptIn(ExperimentalLayoutApi::class)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            opciones.forEach { o ->
              Text(
                if (col.esBooleano) (if (o == "1") "Sí" else "No") else o, style = Mono.merge(MaterialTheme.typography.labelMedium),
                modifier = Modifier.clip(CircleShape)
                  .background(if (o == valor) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                  .clickable { valor = o }.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
          }
        } else {
          OutlinedTextField(
            valor, { valor = it }, label = { Text("Valor exacto") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (col.esNumero) KeyboardType.Decimal else KeyboardType.Text),
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = { if (valor.isNotEmpty()) { aplicar(col.nombre, valor.trim()); cerrar() } }, enabled = valor.isNotEmpty()) { Text("Aplicar", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaDetalle(
  t: TablaExplorador,
  f: JsonObject,
  destinoDe: (String) -> TablaExplorador?,
  alSeguir: (String, String, String) -> Unit,
  alEditar: () -> Unit,
  alEliminar: () -> Unit,
  alCerrar: () -> Unit,
) {
  var confirmar by remember { mutableStateOf(false) }
  ModalBottomSheet(onDismissRequest = alCerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
      Ceja(if (t.editable) "Detalle · editable" else "Detalle · solo lectura")
      Text(t.nombre, style = Mono.merge(MaterialTheme.typography.titleLarge))
      Spacer(Modifier.height(8.dp))
      Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
        t.columnas.forEach { col ->
          val v = f[col.nombre].textoVisible()
          val destino = if (v != null) destinoDe(col.nombre) else null
          HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(col.nombre, style = Mono.merge(MaterialTheme.typography.labelMedium), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
              Text(col.tipo.lowercase() + (if (col.llave) " · llave" else ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            if (destino != null && v != null) {
              Text(
                "$v → ${destino.nombre}", style = Mono.merge(MaterialTheme.typography.bodyMedium), color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable { alSeguir(destino.nombre, col.nombre, v) },
              )
            } else if (col.nombre == "estatus" && ESTATUS.containsKey(v)) {
              ChipEstatus(v)
            } else {
              Text(valorVisible(col, f[col.nombre]), style = MaterialTheme.typography.bodyMedium.merge(if (col.esNumero) Cifras else Mono))
            }
          }
        }
      }
      Spacer(Modifier.height(12.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (t.editable && t.llaveSimple != null) BotonPrincipal("Editar", alEditar, icono = Iconos.Lapiz, modifier = Modifier.weight(1f))
        if (t.borrable && t.llaveSimple != null) BotonSecundario("Eliminar", { confirmar = true }, icono = Iconos.Basura, color = LocalColoresTax.current.peligro)
      }
      if (t.editable && t.llaveSimple == null) Text("Esta tabla tiene llave compuesta: solo admite altas.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
  if (confirmar) {
    DialogoConfirmar("Eliminar registro", "Se borrará el registro de ${t.nombre} y quedará en la bitácora. No se puede deshacer.", "Eliminar", alEliminar, { confirmar = false }, peligro = true)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaEdicion(t: TablaExplorador, fila: JsonObject?, guardando: Boolean, cerrar: () -> Unit, guardar: (Map<String, String>) -> String?) {
  val valores = remember(fila) {
    mutableStateMapOf<String, String>().apply {
      t.columnas.forEach { c ->
        val v = fila?.get(c.nombre).textoVisible().orEmpty()
        put(c.nombre, if (c.esBooleano && v.isNotEmpty()) (if (v == "1" || v == "true") "true" else "false") else v)
      }
    }
  }
  var error by remember { mutableStateOf<String?>(null) }
  ModalBottomSheet(onDismissRequest = cerrar, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding()) {
      Ceja(if (fila == null) "Nuevo registro" else "Editar")
      Text(t.nombre, style = Mono.merge(MaterialTheme.typography.titleLarge))
      Spacer(Modifier.height(8.dp))
      Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        t.columnas.forEach { c -> Campo(c, valores[c.nombre].orEmpty(), bloqueado = c.soloLectura || (fila != null && c.llave), nuevo = fila == null) { valores[c.nombre] = it } }
      }
      if (error != null) Text(error!!, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
      Spacer(Modifier.height(12.dp))
      BotonPrincipal(if (fila == null) "Crear registro" else "Guardar cambios", { error = guardar(valores.toMap()) }, cargando = guardando, icono = Iconos.Palomita, modifier = Modifier.fillMaxWidth())
    }
  }
}

@Composable
private fun Campo(c: Columna, valor: String, bloqueado: Boolean, nuevo: Boolean, cambiar: (String) -> Unit) {
  val etiqueta = c.nombre + if (c.requerida && !c.llave) " *" else ""
  val opciones = c.valores ?: if (c.esBooleano) listOf("true", "false") else null
  if (opciones != null && !bloqueado) {
    Column {
      Text(etiqueta, style = Mono.merge(MaterialTheme.typography.labelMedium), color = MaterialTheme.colorScheme.onSurfaceVariant)
      Spacer(Modifier.height(4.dp))
      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (if (!c.requerida) listOf("") + opciones else opciones).forEach { o ->
          Text(
            when { o.isEmpty() -> "(vacío)"; c.esBooleano -> if (o == "true") "Sí" else "No"; else -> o },
            style = Mono.merge(MaterialTheme.typography.labelMedium),
            modifier = Modifier.clip(CircleShape)
              .background(if (o == valor) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
              .clickable { cambiar(o) }.padding(horizontal = 12.dp, vertical = 7.dp),
          )
        }
      }
    }
  } else {
    OutlinedTextField(
      value = if (bloqueado && c.esFechaHora) valorVisible(c, kotlinx.serialization.json.JsonPrimitive(valor).takeIf { valor.isNotEmpty() }) else valor,
      onValueChange = cambiar,
      enabled = !bloqueado,
      label = { Text(etiqueta) },
      placeholder = { if (nuevo && (c.llave || c.soloLectura)) Text("automático") },
      supportingText = { Text(c.tipo.lowercase() + if (c.soloLectura) " · lo asigna el sistema" else "") },
      singleLine = !c.tipo.equals("TEXT", ignoreCase = true),
      keyboardOptions = KeyboardOptions(keyboardType = if (c.esEntero) KeyboardType.Number else if (c.esDecimal) KeyboardType.Decimal else KeyboardType.Text),
      textStyle = MaterialTheme.typography.bodyMedium.merge(Mono),
      shape = RoundedCornerShape(14.dp),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}
