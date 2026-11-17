package com.taxtech.mobile.ui.conciliar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.Ejecucion
import com.taxtech.mobile.data.entero
import com.taxtech.mobile.data.numero
import com.taxtech.mobile.data.texto
import com.taxtech.mobile.ui.comun.Anillo
import com.taxtech.mobile.ui.comun.AvisoError
import com.taxtech.mobile.ui.comun.BotonPrincipal
import com.taxtech.mobile.ui.comun.BotonSecundario
import com.taxtech.mobile.ui.comun.Cargando
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.ChipEstatus
import com.taxtech.mobile.ui.comun.Dato
import com.taxtech.mobile.ui.comun.DialogoConfirmar
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.NOMBRE_REGLA
import com.taxtech.mobile.ui.comun.Pastilla
import com.taxtech.mobile.ui.comun.REGLAS
import com.taxtech.mobile.ui.comun.SOLO_LECTURA
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.TituloTarjeta
import com.taxtech.mobile.ui.comun.Vacio
import com.taxtech.mobile.ui.comun.avisoPeriodo
import com.taxtech.mobile.ui.comun.entero as enteroTexto
import com.taxtech.mobile.ui.comun.fecha
import com.taxtech.mobile.ui.comun.fechaHora
import com.taxtech.mobile.ui.comun.infoEstatus
import com.taxtech.mobile.ui.comun.pesos
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Mono
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConciliarScreen(panel: PanelViewModel) {
  val c = LocalContenedor.current
  val vm: ConciliarViewModel = viewModel(key = "conciliar") { ConciliarViewModel(c) }
  val p by panel.estado.collectAsStateWithLifecycle()
  val mes by panel.mes.collectAsStateWithLifecycle()
  val pedido by panel.ejecucionPedida.collectAsStateWithLifecycle()
  val e by vm.estado.collectAsStateWithLifecycle()
  val editor = panel.usuario.rol.puedeEditar
  LaunchedEffect(p.empresaId, mes, pedido) {
    vm.sincronizar(p.empresaId, mes, pedido)
    if (pedido != null) panel.ejecucionPedida.value = null
  }
  var notaPara by remember { mutableStateOf<JsonObject?>(null) }
  var notaMasiva by remember { mutableStateOf(false) }
  var confirmarQuitar by remember { mutableStateOf(false) }
  var ocultar by remember { mutableStateOf(false) }

  Box(Modifier.fillMaxSize()) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 190.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item { SelectorRegla(e.regla, vm::elegirRegla) }
      item {
        FormularioEjecutar(
          regla = e.regla,
          periodo = vm.periodoDelMes(),
          politica = p.resumen?.reglas?.firstOrNull { it.codigo == e.regla },
          editor = editor,
          ejecutando = e.ejecutando,
        ) { d, h, ti, td -> vm.ejecutar(d, h, ti, td) { panel.recargar() } }
      }
      if (e.error != null) item { AvisoError(e.error!!) }
      item {
        ListaEjecuciones(
          e.ejecuciones, e.ejecucionId, e.cargandoEjecuciones, editor,
          ocultasEnTotal = p.resumen?.ocultas ?: 0,
          elegir = vm::elegirEjecucion,
          alOcultar = { ocultar = true },
          alVerOcultas = vm::cargarOcultas,
        )
      }
      val ej = e.ejecucion
      val r = e.resultados
      if (ej != null && r != null && r.ejecucion.id == ej.id) {
        item { ResumenEjecucion(ej, r.filas) }
        item { Filtros(r.filas, e.estatus, e.revision, e.porEstatus, vm::filtrarEstatus, vm::filtrarRevision) }
        if (editor && e.visibles.isNotEmpty()) {
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text("${e.visibles.size} partida${if (e.visibles.size == 1) "" else "s"} con estos filtros", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
              TextButton(onClick = if (e.seleccion.size == e.visibles.size) vm::limpiarSeleccion else vm::seleccionarVisibles) {
                Text(if (e.seleccion.size == e.visibles.size) "Quitar selección" else "Seleccionar todas")
              }
            }
          }
        }
        if (e.visibles.isEmpty()) item { Tarjeta { Vacio("Sin resultados", "No hay partidas con este filtro.", icono = Iconos.Filtro) } }
        items(e.visibles, key = { it.idResultado() }) { f ->
          TarjetaPartida(
            f, ej.regla, editor,
            seleccionada = f.idResultado() in e.seleccion,
            alternar = { vm.alternar(f.idResultado()) },
            revisar = { notaPara = f },
          )
        }
      } else if (e.cargandoResultados) {
        item { Cargando(texto = "Cargando resultados…") }
      } else if (!e.cargandoEjecuciones && e.ejecucionId == null) {
        item {
          Tarjeta {
            Vacio(
              "Sin ejecución en el mes",
              "${e.regla} no se ha ejecutado en el mes activo. " + if (editor) "Ejecútala arriba o elige una ejecución anterior." else "Elige una ejecución anterior.", icono = Iconos.Cruce,
            )
          }
        }
      }
    }

    AnimatedVisibility(
      visible = editor && e.seleccion.isNotEmpty(),
      enter = slideInVertically { it },
      exit = slideOutVertically { it },
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 88.dp),
    ) {
      val revisadas = e.resultados?.filas.orEmpty().count { it.idResultado() in e.seleccion && it["revisado_por"].texto() != null }
      Tarjeta(Modifier.widthIn(max = 520.dp).fillMaxWidth(), acento = MaterialTheme.colorScheme.primary, relleno = PaddingValues(12.dp)) {
        Text("${e.seleccion.size} seleccionada${if (e.seleccion.size == 1) "" else "s"}", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          BotonPrincipal("Marcar revisadas", { notaMasiva = true }, cargando = e.trabajando, icono = Iconos.Palomita, modifier = Modifier.weight(1f))
          if (revisadas > 0) BotonSecundario("Quitar ($revisadas)", { confirmarQuitar = true }, color = LocalColoresTax.current.peligro)
          Icon(Iconos.Cerrar, "Cancelar selección", modifier = Modifier.clip(CircleShape).clickable(onClick = vm::limpiarSeleccion).padding(8.dp).size(20.dp))
        }
      }
    }
  }

  notaPara?.let { f ->
    DialogoNota(
      titulo = "Revisión de la partida #${f.idResultado()}",
      inicial = f["comentario"].texto().orEmpty(),
      confirmar = "Guardar revisión",
      alGuardar = { nota -> vm.revisar(f.idResultado(), nota) },
      alCerrar = { notaPara = null },
    )
  }
  if (notaMasiva) {
    DialogoNota(
      titulo = "Marcar ${e.seleccion.size} como revisadas",
      inicial = "",
      ayuda = "Sin nota, cada partida conserva el comentario que ya tenía.",
      confirmar = "Marcar revisadas",
      alGuardar = vm::revisarSeleccion,
      alCerrar = { notaMasiva = false },
    )
  }
  if (confirmarQuitar) {
    DialogoConfirmar(
      "Quitar revisión",
      "Las partidas volverán a contar como pendientes en las alertas y se recupera el comentario original de la regla.",
      "Quitar revisión", vm::quitarRevisionSeleccion, { confirmarQuitar = false }, peligro = true,
    )
  }
  if (ocultar) {
    val ej = e.ejecucion
    DialogoNota(
      titulo = "Ocultar la ejecución #${ej?.id}",
      inicial = "",
      etiqueta = "Motivo (opcional)",
      ayuda = "Se conserva con sus resultados, pero deja de contar en el comparativo, las alertas y los papeles de trabajo. Puedes volver a mostrarla en «Ocultas».",
      confirmar = "Ocultar",
      alGuardar = { m -> vm.ocultar(m) { panel.recargar() } },
      alCerrar = { ocultar = false },
    )
  }
  e.ocultas?.let { lista ->
    ModalBottomSheet(onDismissRequest = vm::cerrarOcultas, containerColor = MaterialTheme.colorScheme.surface) {
      Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
        Text("Ejecuciones ocultas", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        if (lista.isEmpty()) Text("No hay ejecuciones ocultas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        lista.forEach { o ->
          HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text("#${o.id} ${o.regla} · ${fecha(o.desde)} al ${fecha(o.hasta)}", style = MaterialTheme.typography.bodyMedium.merge(Cifras))
              Text("${o.totalConciliadas} de ${o.totalPartidas} conciliadas · oculta por ${o.ocultaPor.orEmpty()} el ${fechaHora(o.ocultaEn)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
              if (o.motivo != null) Text("Motivo: ${o.motivo}", style = MaterialTheme.typography.labelSmall)
            }
            if (editor) TextButton(onClick = { vm.mostrar(o.id) { panel.recargar() } }, enabled = !e.trabajando) { Text("Mostrar") }
          }
        }
      }
    }
  }
}

@Composable
private fun SelectorRegla(actual: String, elegir: (String) -> Unit) {
  LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    items(REGLAS) { r ->
      val activo = r == actual
      Column(
        Modifier.width(118.dp).clip(RoundedCornerShape(18.dp))
          .background(if (activo) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else LocalColoresTax.current.vidrio)
          .border(1.dp, if (activo) MaterialTheme.colorScheme.primary else LocalColoresTax.current.bordeVidrio, RoundedCornerShape(18.dp))
          .clickable { elegir(r) }
          .padding(12.dp),
      ) {
        Text(r, style = Mono.merge(MaterialTheme.typography.titleMedium), color = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        Text(NOMBRE_REGLA[r].orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

@Composable
private fun FormularioEjecutar(
  regla: String,
  periodo: Pair<String, String>,
  politica: com.taxtech.mobile.data.Regla?,
  editor: Boolean,
  ejecutando: Boolean,
  ejecutar: (String, String, String?, String?) -> Unit,
) {
  var abierto by rememberSaveable { mutableStateOf(false) }
  var personalizar by rememberSaveable(regla, periodo) { mutableStateOf(false) }
  var desde by rememberSaveable(regla, periodo) { mutableStateOf(periodo.first) }
  var hasta by rememberSaveable(regla, periodo) { mutableStateOf(periodo.second) }
  var tolImporte by rememberSaveable(regla) { mutableStateOf("") }
  var tolDias by rememberSaveable(regla) { mutableStateOf("") }
  var confirmar by remember { mutableStateOf<String?>(null) }
  val sinTolerancia = regla == "C5"
  val formato = Regex("""^\d{4}-\d{2}-\d{2}$""")
  val valido = formato.matches(desde) && formato.matches(hasta) && desde <= hasta
  val aviso = avisoPeriodo(desde, hasta, regla)

  Tarjeta(Modifier.animateContentSize()) {
    Row(Modifier.fillMaxWidth().clickable { abierto = !abierto }, verticalAlignment = Alignment.CenterVertically) {
      TituloTarjeta("Ejecutar $regla", "${fecha(desde)} al ${fecha(hasta)}", Iconos.Rayo)
    }
    if (abierto) {
      Spacer(Modifier.height(12.dp))
      if (personalizar) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(desde, { desde = it.take(10) }, label = { Text("Desde") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
          OutlinedTextField(hasta, { hasta = it.take(10) }, label = { Text("Hasta") }, placeholder = { Text("AAAA-MM-DD") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f))
        }
      } else {
        Text("Periodo: el mes activo completo. Cámbialo con el selector de mes de arriba.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      TextButton(onClick = { personalizar = !personalizar; if (!personalizar) { desde = periodo.first; hasta = periodo.second } }) {
        Text(if (personalizar) "Usar el mes activo" else "Periodo especial")
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          tolImporte, { tolImporte = it.filter { ch -> ch.isDigit() || ch == '.' } }, enabled = !sinTolerancia,
          label = { Text("Tolerancia $") }, placeholder = { Text(if (sinTolerancia) "no aplica" else politica?.toleranciaImporte ?: "") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          tolDias, { tolDias = it.filter(Char::isDigit) }, enabled = !sinTolerancia,
          label = { Text("Tolerancia días") }, placeholder = { Text(if (sinTolerancia) "no aplica" else politica?.toleranciaDias ?: "") },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
        )
      }
      if (sinTolerancia) Text("C5 no usa tolerancias: toma los cruces de C1, C3, C4, C7 y C10.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (aviso != null) {
        Spacer(Modifier.height(8.dp))
        Row {
          Icon(Iconos.Alerta, null, tint = LocalColoresTax.current.aviso, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(aviso, style = MaterialTheme.typography.bodySmall, color = LocalColoresTax.current.aviso)
        }
      }
      Spacer(Modifier.height(12.dp))
      BotonPrincipal(
        if (ejecutando) "Conciliando…" else "Ejecutar $regla",
        onClick = { if (aviso != null) confirmar = aviso else ejecutar(desde, hasta, tolImporte.takeIf { !sinTolerancia }, tolDias.takeIf { !sinTolerancia }) },
        enabled = editor && valido, cargando = ejecutando, icono = Iconos.Rayo, modifier = Modifier.fillMaxWidth(),
      )
      if (!editor) Text(SOLO_LECTURA, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
  }
  confirmar?.let { a ->
    DialogoConfirmar(
      "Periodo no mensual", "$a\n\n¿Ejecutar $regla de todos modos del ${fecha(desde)} al ${fecha(hasta)}?", "Ejecutar",
      { ejecutar(desde, hasta, tolImporte.takeIf { !sinTolerancia }, tolDias.takeIf { !sinTolerancia }) }, { confirmar = null },
    )
  }
}

@Composable
private fun ListaEjecuciones(
  lista: List<Ejecucion>,
  actual: Long?,
  cargando: Boolean,
  editor: Boolean,
  ocultasEnTotal: Long,
  elegir: (Long) -> Unit,
  alOcultar: () -> Unit,
  alVerOcultas: () -> Unit,
) {
  Column {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Ceja("Ejecuciones", modifier = Modifier.padding(start = 4.dp).weight(1f))
      if (editor && actual != null) TextButton(onClick = alOcultar) { Text("Ocultar") }
      if (ocultasEnTotal > 0) TextButton(onClick = alVerOcultas) { Text("Ocultas ($ocultasEnTotal)") }
    }
    when {
      cargando && lista.isEmpty() -> Cargando()
      lista.isEmpty() -> Text("Sin ejecuciones de esta regla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
      else -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(lista, key = { it.id }) { ej ->
          val activo = ej.id == actual
          Column(
            Modifier.clip(RoundedCornerShape(16.dp))
              .background(if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
              .clickable { elegir(ej.id) }
              .padding(horizontal = 12.dp, vertical = 8.dp),
          ) {
            val color = if (activo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Text("#${ej.id} · ${fecha(ej.desde)} a ${fecha(ej.hasta)}", style = MaterialTheme.typography.labelLarge.merge(Cifras), color = color)
            Text("${(ej.porcentaje * 100).toInt()}% · ${fechaHora(ej.ejecutadoEn)}", style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.75f))
          }
        }
      }
    }
  }
}

@Composable
private fun ResumenEjecucion(ej: Ejecucion, filas: List<JsonObject>) {
  val colores = LocalColoresTax.current
  val p = ej.parametros
  fun par(k: String): Double? = p?.get(k).numero()
  Tarjeta {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Anillo(ej.porcentaje, tamano = 76.dp, grosor = 8.dp) {
        Text("${(ej.porcentaje * 100).toInt()}%", style = MaterialTheme.typography.titleMedium.merge(Cifras))
      }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text("${ej.regla} #${ej.id}", style = Mono.merge(MaterialTheme.typography.titleSmall), color = MaterialTheme.colorScheme.primary)
        Text("${enteroTexto(ej.totalConciliadas)} de ${enteroTexto(ej.totalPartidas)} partidas conciliadas", style = MaterialTheme.typography.bodyMedium.merge(Cifras))
        Text("${fecha(ej.desde)} al ${fecha(ej.hasta)} · ${ej.ejecutadoPor.orEmpty()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    val cifras = buildList {
      when (ej.regla) {
        "C5" -> {
          add(Triple("IVA acreditable", par("iva_acreditable"), true))
          if (par("iva_importacion") != null) { add(Triple("Nacional", par("iva_nacional"), false)); add(Triple("Importación", par("iva_importacion"), false)) }
          if ((par("iva_notas_credito") ?: 0.0) != 0.0) add(Triple("Menos notas de crédito recibidas", par("iva_notas_credito"), false))
          add(Triple("Pendiente por falta de pago", par("iva_pendiente"), false))
        }
        "C7" -> if (par("iva_importacion_pagado") != null) add(Triple("IVA de importación pagado y conciliado", par("iva_importacion_pagado"), true))
        "C10" -> if (par("iva_notas_recibidas") != null) {
          add(Triple("IVA de notas recibidas (resta al acreditable)", par("iva_notas_recibidas"), true))
          add(Triple("IVA de notas emitidas (resta al trasladado)", par("iva_notas_emitidas"), true))
        }
        "C9" -> {
          fun porEnterar(concepto: String) = filas.firstOrNull { it["concepto"].texto() == concepto }?.let { (it["importe_b"].numero() ?: 0.0) - (it["importe_a"].numero() ?: 0.0) }
          add(Triple("ISR por enterar", porEnterar("ISR retenido"), true))
          add(Triple("IVA por enterar", porEnterar("IVA retenido"), true))
        }
      }
    }
    if (cifras.isNotEmpty()) {
      HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
      cifras.forEach { (n, v, fuerte) -> Dato(n, pesos(v), destacado = fuerte, color = if (fuerte) colores.exito else MaterialTheme.colorScheme.onSurface) }
      if (ej.regla == "C9") p?.get("fecha_limite_entero").texto()?.let { Dato("Fecha límite del entero", fecha(it)) }
      Text("Los papeles de trabajo en Excel se generan desde el panel web.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
  }
}

@Composable
private fun Filtros(
  filas: List<JsonObject>,
  estatus: String?,
  revision: FiltroRevision,
  porEstatus: List<JsonObject>,
  alEstatus: (String?) -> Unit,
  alRevision: (FiltroRevision) -> Unit,
) {
  val cuenta = filas.groupingBy { it["estatus"].texto().orEmpty() }.eachCount()
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      item { Filtro("Todos · ${filas.size}", estatus == null) { alEstatus(null) } }
      items(cuenta.keys.toList()) { s ->
        val info = infoEstatus(s)
        Filtro("$s · ${cuenta[s]}", estatus == s, info.color, mono = true) { alEstatus(if (estatus == s) null else s) }
      }
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(FiltroRevision.entries) { r ->
        val n = if (r == FiltroRevision.TODAS) null else porEstatus.count(r.cumple)
        Filtro(r.etiqueta + (n?.let { " · $it" } ?: ""), revision == r) { alRevision(r) }
      }
    }
  }
}

@Composable
private fun Filtro(texto: String, activo: Boolean, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary, mono: Boolean = false, onClick: () -> Unit) {
  Text(
    texto,
    style = if (mono) MaterialTheme.typography.labelMedium.merge(Mono) else MaterialTheme.typography.labelMedium,
    color = if (activo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.clip(CircleShape)
      .background(if (activo) color.copy(alpha = 0.22f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
      .border(1.dp, if (activo) color else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 7.dp),
  )
}

@Composable
private fun TarjetaPartida(f: JsonObject, regla: String, editor: Boolean, seleccionada: Boolean, alternar: () -> Unit, revisar: () -> Unit) {
  val v = vista(regla)
  val colores = LocalColoresTax.current
  val estatus = f["estatus"].texto()
  val revisadoPor = f["revisado_por"].texto()
  val a = f["importe_a"].numero()
  val b = f["importe_b"].numero()
  val dif = f["diferencia"].numero()
  Tarjeta(
    acento = infoEstatus(estatus).color,
    onClick = if (editor) alternar else null,
    relleno = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
  ) {
    Row(verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f)) {
        Text(v.titulo(f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          ChipEstatus(estatus)
          f["nivel_cruce"].entero()?.let { Spacer(Modifier.width(6.dp)); Pastilla("N$it", MaterialTheme.colorScheme.onSurfaceVariant, mono = true) }
          Spacer(Modifier.width(6.dp))
          Text("#${f.idResultado()}", style = MaterialTheme.typography.labelSmall.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      if (editor) Checkbox(checked = seleccionada, onCheckedChange = { alternar() })
    }
    Spacer(Modifier.height(6.dp))
    v.detalles(f).forEach { d -> Text(d, style = MaterialTheme.typography.bodySmall.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Importe(v.etiquetaA, a, Modifier.weight(1f))
      Importe(v.etiquetaB, b, Modifier.weight(1f))
      if (dif != null && dif != 0.0) Importe("Diferencia", dif, Modifier.weight(1f), colores.aviso)
    }
    f["comentario"].texto()?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp)) }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (revisadoPor != null) {
        Icon(Iconos.Palomita, null, tint = colores.exito, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
          revisadoPor + (if (f["revision_heredada_de"].texto() != null) " · conservada" else ""),
          style = MaterialTheme.typography.labelSmall, color = colores.exito, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
      } else {
        Text("Sin revisar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
      }
      if (editor) TextButton(onClick = revisar) { Text(if (revisadoPor != null) "Editar nota" else "Marcar revisado") }
    }
  }
}

@Composable
private fun Importe(etiqueta: String, valor: Double?, modifier: Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
  Column(modifier) {
    Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(if (valor == null) "—" else pesos(valor), style = MaterialTheme.typography.bodyMedium.merge(Cifras).copy(fontWeight = FontWeight.Medium), color = color, maxLines = 1)
  }
}

@Composable
private fun DialogoNota(
  titulo: String,
  inicial: String,
  confirmar: String,
  alGuardar: (String?) -> Unit,
  alCerrar: () -> Unit,
  etiqueta: String = "Nota de revisión",
  ayuda: String? = null,
) {
  var texto by remember { mutableStateOf(inicial) }
  AlertDialog(
    onDismissRequest = alCerrar,
    title = { Text(titulo) },
    text = {
      Column {
        if (ayuda != null) { Text(ayuda, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(10.dp)) }
        OutlinedTextField(texto, { texto = it.take(2000) }, label = { Text(etiqueta) }, minLines = 3, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
      }
    },
    confirmButton = { TextButton(onClick = { alGuardar(texto.trim().ifEmpty { null }); alCerrar() }) { Text(confirmar, fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
  )
}
