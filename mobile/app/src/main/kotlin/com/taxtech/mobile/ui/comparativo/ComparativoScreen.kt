package com.taxtech.mobile.ui.comparativo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.Comparativo
import com.taxtech.mobile.data.PeriodoComparativo
import com.taxtech.mobile.data.Seguimiento
import com.taxtech.mobile.ui.comun.AvisoError
import com.taxtech.mobile.ui.comun.BarraProgreso
import com.taxtech.mobile.ui.comun.BotonSecundario
import com.taxtech.mobile.ui.comun.COLOR_ESTADO
import com.taxtech.mobile.ui.comun.Cargando
import com.taxtech.mobile.ui.comun.ChipEstatus
import com.taxtech.mobile.ui.comun.DialogoConfirmar
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.Pastilla
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.TituloTarjeta
import com.taxtech.mobile.ui.comun.Vacio
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.comun.compacto
import com.taxtech.mobile.ui.comun.fechaHora
import com.taxtech.mobile.ui.comun.mesCorto
import com.taxtech.mobile.ui.comun.mesLargo
import com.taxtech.mobile.ui.comun.pesos
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ComparativoViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(
    val clave: String? = null,
    val meses: Int = 12,
    val datos: Comparativo? = null,
    val mesSel: String? = null,
    val estadoSel: String = "Todos",
    val cargando: Boolean = false,
    val error: String? = null,
  )

  val estado = MutableStateFlow(Estado())

  fun cargar(empresaId: Long?, hasta: String, meses: Int = estado.value.meses, forzar: Boolean = false) {
    if (empresaId == null) return
    val clave = "$empresaId|$hasta|$meses"
    if (!forzar && clave == estado.value.clave) return
    estado.update { it.copy(clave = clave, meses = meses, cargando = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargando = false) } }) {
      val d = repo.comparativo(empresaId, meses, hasta)
      val sel = estado.value.mesSel?.takeIf { s -> d.periodos.any { it.desde == s } } ?: d.periodos.lastOrNull()?.desde
      estado.update { it.copy(datos = d, mesSel = sel) }
    }
  }

  fun elegirMes(m: String) = estado.update { it.copy(mesSel = m, estadoSel = "Todos") }
  fun elegirEstado(s: String) = estado.update { it.copy(estadoSel = s) }

  fun registrarCorte(empresaId: Long, p: PeriodoComparativo, hasta: String) = lanzar {
    repo.registrarCorte(empresaId, p.desde, p.hasta, "Registrado desde la app móvil")
    avisar("Corte de ${mesLargo(p.desde)} registrado")
    cargar(empresaId, hasta, forzar = true)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparativoScreen(panel: PanelViewModel) {
  val c = LocalContenedor.current
  val vm: ComparativoViewModel = viewModel(key = "comparativo") { ComparativoViewModel(c) }
  val p by panel.estado.collectAsStateWithLifecycle()
  val mes by panel.mes.collectAsStateWithLifecycle()
  val e by vm.estado.collectAsStateWithLifecycle()
  LaunchedEffect(p.empresaId, mes) { vm.cargar(p.empresaId, mes) }
  var corte by remember { mutableStateOf<PeriodoComparativo?>(null) }
  val editor = panel.usuario.rol.puedeEditar

  PullToRefreshBox(isRefreshing = e.cargando && e.datos != null, onRefresh = { vm.cargar(p.empresaId, mes, forzar = true) }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          listOf(6, 12, 24).forEachIndexed { i, n ->
            SegmentedButton(
              selected = e.meses == n, onClick = { vm.cargar(p.empresaId, mes, n) },
              shape = SegmentedButtonDefaults.itemShape(i, 3),
            ) { Text("$n meses") }
          }
        }
      }
      if (e.error != null) item { AvisoError(e.error!!) { vm.cargar(p.empresaId, mes, forzar = true) } }
      val d = e.datos
      if (d == null && e.cargando) item { Cargando() }
      if (d != null && d.periodos.isEmpty()) {
        item { Tarjeta { Vacio("Sin conciliaciones", "Todavía no hay conciliaciones ejecutadas hasta ${mesLargo("$mes-01")}.", Iconos.Balanza) } }
      }
      if (d != null && d.periodos.isNotEmpty()) {
        item {
          Text("Hallazgos de cada mes al corte contra hoy. Desliza para ver todos los meses.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        }
        item {
          val maximo = d.periodos.maxOf { it.riesgoAbierto }.takeIf { it > 0 } ?: 1.0
          LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(d.periodos.reversed()) { per -> TarjetaMes(per, maximo, per.desde == e.mesSel, editor, { vm.elegirMes(per.desde) }, { corte = per }) }
          }
        }
        item { PorRegla(d) }
        item { SeguimientoMes(d, e.mesSel, e.estadoSel, vm::elegirMes, vm::elegirEstado) }
      }
    }
  }

  corte?.let { per ->
    DialogoConfirmar(
      "Registrar corte",
      "¿Registrar ahora el corte de ${mesLargo(per.desde)}? Los hallazgos de ese mes se compararán contra los de este momento.",
      "Registrar",
      { p.empresaId?.let { vm.registrarCorte(it, per, mes) } },
      { corte = null },
    )
  }
}

@Composable
private fun TarjetaMes(per: PeriodoComparativo, maximo: Double, activo: Boolean, editor: Boolean, elegir: () -> Unit, registrar: () -> Unit) {
  val colores = LocalColoresTax.current
  val sinCorte = per.corte == null
  Tarjeta(Modifier.width(232.dp), acento = if (activo) MaterialTheme.colorScheme.primary else null, onClick = elegir) {
    Text(mesLargo(per.desde), style = MaterialTheme.typography.titleMedium)
    Text("${per.reglasEjecutadas} de 10 reglas · " + if (sinCorte) "sin corte" else "corte ${fechaHora(per.corte)}", style = MaterialTheme.typography.labelSmall, color = if (sinCorte) colores.aviso else MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.Bottom) {
      Text("${per.abiertos}", style = MaterialTheme.typography.headlineMedium.merge(Cifras))
      Spacer(Modifier.width(6.dp))
      Text("abiertos", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
      Spacer(Modifier.weight(1f))
      if (per.altaAbiertos > 0) Pastilla("${per.altaAbiertos} alta", colores.peligro)
    }
    BarraProgreso((per.riesgoAbierto / maximo).toFloat(), color = colores.aviso)
    Text("Riesgo abierto ${pesos(per.riesgoAbierto)}", style = MaterialTheme.typography.labelSmall.merge(Cifras), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    if (!sinCorte) {
      Spacer(Modifier.height(8.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Mini("Al corte", "${per.alCorte}")
        Mini("Cerrados", "${per.cerrados}", colores.exito)
        Mini("Cambiaron", "${per.cambiaron}")
        Mini("Nuevos", "${per.nuevos}", colores.peligro)
      }
    }
    if (sinCorte && editor) {
      Spacer(Modifier.height(8.dp))
      BotonSecundario("Registrar corte", registrar, icono = Iconos.Calendario)
    }
  }
}

@Composable
private fun Mini(etiqueta: String, valor: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
  Column {
    Text(valor, style = MaterialTheme.typography.titleSmall.merge(Cifras), color = color)
    Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Abiertos por conciliación y mes: matriz que se desliza a lo ancho. */
@Composable
private fun PorRegla(d: Comparativo) {
  Tarjeta {
    TituloTarjeta("Abiertos por conciliación", "Hallazgos abiertos y su riesgo por mes", Iconos.Cruce)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
      Column {
        Text("", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(vertical = 6.dp))
        d.porRegla.forEach { r ->
          Row(Modifier.width(150.dp).height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(r.regla, style = Mono.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(38.dp))
            Text(r.conciliacion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
        }
      }
      d.periodos.forEach { per ->
        Column(horizontalAlignment = Alignment.End) {
          Text(mesCorto(per.desde), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp).padding(vertical = 6.dp), textAlign = TextAlign.End)
          d.porRegla.forEach { r ->
            val m = r.meses.firstOrNull { it.periodoDesde == per.desde }
            Column(Modifier.width(88.dp).height(44.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.Center) {
              if (m == null || m.abiertos == 0L) Text("0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
              else {
                Text("${m.abiertos}", style = MaterialTheme.typography.bodyMedium.merge(Cifras).copy(fontWeight = FontWeight.SemiBold))
                Text(compacto(m.riesgo), style = MaterialTheme.typography.labelSmall.merge(Cifras), color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
              if (m != null && m.cerrados > 0) Text("${m.cerrados} cerr.", style = MaterialTheme.typography.labelSmall, color = LocalColoresTax.current.exito)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SeguimientoMes(d: Comparativo, mesSel: String?, estadoSel: String, elegirMes: (String) -> Unit, elegirEstado: (String) -> Unit) {
  val filas = d.detalle.filter { it.periodoDesde == mesSel }
  val cuenta = filas.groupingBy { it.estado }.eachCount()
  val estados = listOf("Todos") + listOf("Abierto", "Cambió de estatus", "Nuevo", "Cerrado", "Sin corte").filter { (cuenta[it] ?: 0) > 0 }
  val visibles = filas.filter { estadoSel == "Todos" || it.estado == estadoSel }
  var todas by remember(mesSel, estadoSel) { mutableStateOf(false) }
  Tarjeta {
    TituloTarjeta("Seguimiento", mesSel?.let { "Hallazgos de ${mesLargo(it)}" }, Iconos.Historial)
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(d.periodos.reversed()) { per ->
        val activo = per.desde == mesSel
        Text(
          mesCorto(per.desde), style = MaterialTheme.typography.labelMedium,
          color = if (activo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.clip(CircleShape).background(if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable { elegirMes(per.desde) }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      items(estados) { s ->
        val activo = s == estadoSel
        val color = COLOR_ESTADO[s] ?: MaterialTheme.colorScheme.primary
        Text(
          "$s · ${if (s == "Todos") filas.size else cuenta[s]}", style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.clip(CircleShape).background(color.copy(alpha = if (activo) 0.25f else 0.08f)).clickable { elegirEstado(s) }.padding(horizontal = 12.dp, vertical = 6.dp),
        )
      }
    }
    if (visibles.isEmpty()) Text("Sin hallazgos en este mes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
    (if (todas) visibles else visibles.take(15)).forEach { s -> FilaSeguimiento(s) }
    if (visibles.size > 15) TextButton(onClick = { todas = !todas }) { Text(if (todas) "Ver menos" else "Ver todos (${visibles.size})") }
  }
}

@Composable
private fun FilaSeguimiento(s: Seguimiento) {
  val colores = LocalColoresTax.current
  Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Pastilla(s.estado, COLOR_ESTADO[s.estado] ?: MaterialTheme.colorScheme.primary, punto = true)
      Spacer(Modifier.width(6.dp))
      Text(s.regla, style = Mono.merge(MaterialTheme.typography.labelLarge), color = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.weight(1f))
      s.prioridad?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = if (it == "Alta") colores.peligro else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Text(listOfNotNull(s.referencia, s.contraparte).joinToString(" · ").ifEmpty { "—" }, style = MaterialTheme.typography.bodySmall.merge(Mono), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
      if (s.estatusCorte != null) ChipEstatus(s.estatusCorte) else Text("no estaba", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      Text("  →  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
      if (s.estatusHoy != null) ChipEstatus(s.estatusHoy) else Text("resuelto", style = MaterialTheme.typography.labelSmall, color = colores.exito)
    }
    Text(
      "Riesgo al corte ${pesos(s.riesgoCorte)} · hoy ${pesos(s.riesgoHoy)}",
      style = MaterialTheme.typography.labelSmall.merge(Cifras), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
    )
    Text(
      if (s.estado == "Cerrado") "Ya no aparece en la última ejecución" else s.accion.orEmpty(),
      style = MaterialTheme.typography.bodySmall, color = if (s.estado == "Cerrado") colores.exito else MaterialTheme.colorScheme.onSurface,
    )
    s.revisadoPor?.let { Text("Revisado por $it", style = MaterialTheme.typography.labelSmall, color = colores.exito) }
  }
}
