package com.taxtech.mobile.ui.resumen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.Alerta
import com.taxtech.mobile.data.Alertas
import com.taxtech.mobile.data.Ejecucion
import com.taxtech.mobile.data.Resumen
import com.taxtech.mobile.data.ResumenAlertas
import com.taxtech.mobile.ui.comun.Anillo
import com.taxtech.mobile.ui.comun.AvisoError
import com.taxtech.mobile.ui.comun.BarraProgreso
import com.taxtech.mobile.ui.comun.BotonSecundario
import com.taxtech.mobile.ui.comun.COLOR_FUENTE
import com.taxtech.mobile.ui.comun.Cargando
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.ChipEstatus
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.Kpi
import com.taxtech.mobile.ui.comun.NOMBRE_REGLA
import com.taxtech.mobile.ui.comun.NumeroAnimado
import com.taxtech.mobile.ui.comun.Pastilla
import com.taxtech.mobile.ui.comun.SEVERIDADES
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.TituloTarjeta
import com.taxtech.mobile.ui.comun.Vacio
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.comun.entero
import com.taxtech.mobile.ui.comun.fecha
import com.taxtech.mobile.ui.comun.fechaHora
import com.taxtech.mobile.ui.comun.pesos
import com.taxtech.mobile.ui.comun.severidad
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.panel.Seccion
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Mono
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class ResumenViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(
    val empresaId: Long? = null,
    val alertas: Alertas? = null,
    val resumenes: List<ResumenAlertas> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null,
    val generando: Boolean = false,
  )

  val estado = MutableStateFlow(Estado())

  fun cargar(empresaId: Long?, forzar: Boolean = false) {
    if (empresaId == null) { estado.value = Estado(); return }
    if (!forzar && estado.value.empresaId == empresaId && estado.value.alertas != null) return
    estado.update { it.copy(empresaId = empresaId, cargando = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargando = false) } }) {
      val a = repo.alertas(empresaId)
      // El historial es complementario: si falla, solo se oculta
      val r = runCatching { repo.resumenesAlertas(empresaId) }.getOrDefault(emptyList())
      estado.update { it.copy(alertas = a, resumenes = r) }
    }
  }

  fun generarResumen() {
    val id = estado.value.empresaId ?: return
    estado.update { it.copy(generando = true) }
    lanzar(alTerminar = { estado.update { it.copy(generando = false) } }) {
      repo.generarResumenAlertas()
      avisar("Resumen de alertas guardado")
      estado.update { it.copy(resumenes = repo.resumenesAlertas(id)) }
    }
  }
}

/** Saludo según la hora del dispositivo. */
private fun saludo(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
  in 5..11 -> "Buenos días"
  in 12..18 -> "Buenas tardes"
  else -> "Buenas noches"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResumenScreen(panel: PanelViewModel, ir: (Seccion) -> Unit) {
  val c = LocalContenedor.current
  val vm: ResumenViewModel = viewModel(key = "resumen") { ResumenViewModel(c) }
  val p by panel.estado.collectAsStateWithLifecycle()
  val e by vm.estado.collectAsStateWithLifecycle()
  LaunchedEffect(p.empresaId) { vm.cargar(p.empresaId) }

  PullToRefreshBox(
    isRefreshing = (p.cargando && p.resumen != null) || (e.cargando && e.alertas != null),
    onRefresh = { panel.recargar(); vm.cargar(p.empresaId, forzar = true) },
    modifier = Modifier.fillMaxSize(),
  ) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item {
        Text(
          saludo(),
          style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground,
          modifier = Modifier.padding(horizontal = 4.dp),
        )
      }
      if (p.error != null) item { AvisoError(p.error!!, reintentar = panel::cargarEmpresas) }
      if (!p.cargando && p.empresas.isEmpty() && p.error == null) {
        item { Tarjeta { Vacio("Sin empresas asignadas", "Pide a un administrador que te asigne al menos una empresa en Accesos.", icono = Iconos.Edificio) } }
      }
      val r = p.resumen
      if (r == null && p.cargando) item { Cargando() }
      if (r != null) {
        item { Pulso(r, e.alertas) }
        item { TarjetaAlertas(e.alertas, e.cargando, e.error) }
        if (e.resumenes.isNotEmpty() || panel.usuario.rol.puedeEditar) {
          item { TarjetaResumenes(e.resumenes, panel.usuario.rol.puedeEditar, e.generando, vm::generarResumen) }
        }
        item { Fuentes(r) }
        item { Ejecuciones(r.ejecuciones) { ej -> panel.ejecucionPedida.value = ej.regla to ej.id; ir(Seccion.CONCILIAR) } }
        item { UltimasCargas(r) { ir(Seccion.CARGAS) } }
      }
    }
  }
}

/** Avance de conciliación: última ejecución visible de cada regla. */
@Composable
private fun Pulso(r: Resumen, alertas: Alertas?) {
  val colores = LocalColoresTax.current
  val ultimas = r.ejecuciones.groupBy { it.regla }.map { (_, l) -> l.first() }
  val partidas = ultimas.sumOf { it.totalPartidas }
  val conciliadas = ultimas.sumOf { it.totalConciliadas }
  val diferencias = ultimas.sumOf { it.importeDiferencias }
  val pct = if (partidas > 0) conciliadas.toFloat() / partidas else 0f
  Tarjeta(relleno = PaddingValues(18.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Anillo(pct, tamano = 112.dp, grosor = 11.dp) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          NumeroAnimado((pct * 100).toDouble(), { "${it.toInt()}%" }, MaterialTheme.typography.headlineSmall)
          Text("conciliado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(Modifier.width(18.dp))
      Column(Modifier.weight(1f)) {
        Ceja("Pulso fiscal")
        Spacer(Modifier.height(4.dp))
        Text(
          if (partidas > 0) "${entero(conciliadas)} de ${entero(partidas)} partidas" else "Sin conciliaciones todavía",
          style = MaterialTheme.typography.titleMedium.merge(Cifras),
        )
        Text("Última ejecución de ${ultimas.size} regla${if (ultimas.size == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Text("Diferencias abiertas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NumeroAnimado(diferencias, ::pesos, MaterialTheme.typography.titleMedium, color = if (diferencias > 0) colores.aviso else MaterialTheme.colorScheme.onSurface)
        val urgentes = alertas?.urgentes ?: 0
        if (urgentes > 0) {
          Spacer(Modifier.height(8.dp))
          Pastilla("$urgentes alerta${if (urgentes == 1L) "" else "s"} urgente${if (urgentes == 1L) "" else "s"}", colores.peligro, punto = true)
        }
      }
    }
  }
}

@Composable
private fun TarjetaAlertas(a: Alertas?, cargando: Boolean, error: String?) {
  var todas by rememberSaveable { mutableStateOf(false) }
  Tarjeta(Modifier.animateContentSize()) {
    TituloTarjeta("Alertas", a?.hoy?.let { "al ${fecha(it)}" }, Iconos.Alerta)
    Spacer(Modifier.height(10.dp))
    when {
      error != null -> Text(error, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall)
      a == null && cargando -> Cargando()
      a != null -> {
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          SEVERIDADES.filter { (a.conteo[it.clave] ?: 0) > 0 }.forEach { s -> Pastilla("${s.nombre} · ${a.conteo[s.clave]}", s.color) }
          val rev = a.conteo["revisadas"] ?: 0
          if (rev > 0) Pastilla("$rev revisada${if (rev == 1L) "" else "s"}", LocalColoresTax.current.exito)
        }
        if (a.lista.isEmpty()) {
          Spacer(Modifier.height(8.dp))
          Text("No hay vencimientos próximos ni hallazgos de prioridad alta abiertos.", style = MaterialTheme.typography.bodySmall, color = LocalColoresTax.current.exito)
        }
        val visibles = if (todas) a.lista else a.lista.take(5)
        visibles.forEachIndexed { i, al ->
          if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
          FilaAlerta(al)
        }
        if (a.lista.size > 5) TextButton(onClick = { todas = !todas }) { Text(if (todas) "Ver menos" else "Ver todas (${a.lista.size})") }
      }
    }
  }
}

@Composable
private fun FilaAlerta(a: Alerta) {
  val s = severidad(a.severidad)
  val colores = LocalColoresTax.current
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 10.dp)) {
    Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(s.color))
    Spacer(Modifier.width(10.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Pastilla(s.nombre.uppercase(), s.color)
        Spacer(Modifier.width(6.dp))
        Text(a.categoria.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(Modifier.height(4.dp))
      Text(a.titulo, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
      if (a.detalle != null) Text(a.detalle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      when {
        a.revisadoPor != null -> Text("Revisada por ${a.revisadoPor}${a.revisadoEn?.let { " el ${fechaHora(it)}" } ?: ""}", style = MaterialTheme.typography.labelSmall, color = colores.exito)
        a.accion != null -> Text("Acción: ${a.accion}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
    Spacer(Modifier.width(8.dp))
    Column(horizontalAlignment = Alignment.End) {
      if (a.dias != null && a.fechaLimite != null) {
        val vencida = a.dias < 0
        Text(if (vencida) "Venció ${fecha(a.fechaLimite)}" else "Vence ${fecha(a.fechaLimite)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = if (vencida) colores.peligro else MaterialTheme.colorScheme.onSurface)
        Text(
          when { vencida -> "hace ${-a.dias} día${if (a.dias == -1L) "" else "s"}"; a.dias == 0L -> "hoy"; else -> "en ${a.dias} día${if (a.dias == 1L) "" else "s"}" },
          style = MaterialTheme.typography.labelSmall,
          color = if (vencida) colores.peligro else if (a.dias <= 5) colores.aviso else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (a.importe != null) Text(pesos(a.importe), style = MaterialTheme.typography.labelLarge.merge(Cifras))
    }
  }
}

@Composable
private fun TarjetaResumenes(lista: List<ResumenAlertas>, puedeGenerar: Boolean, generando: Boolean, generar: () -> Unit) {
  var abierto by rememberSaveable { mutableStateOf(false) }
  val conteoTxt = { c: Map<String, Long> ->
    SEVERIDADES.joinToString(" · ") { "${c[it.clave] ?: 0} ${it.nombre.lowercase()}" } + ((c["revisadas"] ?: 0).takeIf { it > 0 }?.let { " · $it revisadas" } ?: "")
  }
  Tarjeta(Modifier.animateContentSize()) {
    TituloTarjeta(
      "Resúmenes guardados",
      lista.firstOrNull()?.let { "Último: ${fechaHora(it.generadoEn)} (${if (it.origen == "programado") "programado" else "manual"})" } ?: "Todavía no hay resúmenes guardados",
      Iconos.Historial,
    )
    lista.firstOrNull()?.let { Text(conteoTxt(it.conteo), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp)) }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      if (puedeGenerar) BotonSecundario(if (generando) "Guardando…" else "Guardar resumen ahora", generar, enabled = !generando, icono = Iconos.Palomita)
      if (lista.isNotEmpty()) BotonSecundario(if (abierto) "Ocultar historial" else "Ver historial", { abierto = !abierto }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (abierto) {
      lista.forEach { r ->
        HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(fechaHora(r.generadoEn) + " · corte ${fecha(r.fechaCorte)}", style = MaterialTheme.typography.labelLarge)
        Text(
          (if (r.origen == "programado") "Programado" else "Manual" + (r.generadoPor?.let { " · $it" } ?: "")) + " · " + conteoTxt(r.conteo),
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        r.nuevas.forEach { Text("+ $it", style = MaterialTheme.typography.bodySmall, color = LocalColoresTax.current.peligro) }
        r.resueltas.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall, color = LocalColoresTax.current.exito) }
      }
    }
  }
}

@Composable
private fun Fuentes(r: Resumen) {
  val f = r.fuentes
  Column {
    Ceja("Fuentes cargadas", modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      val kpis = listOf(
        Triple("CFDI", f.cfdi, false), Triple("CFDI recibidos", f.cfdiRecibidos, false), Triple("CFDI cancelados", f.cfdiCancelados, f.cfdiCancelados > 0),
        Triple("Documentos ERP", f.erpDocumentos, false), Triple("Movimientos bancarios", f.movimientos, false),
        Triple("Pedimentos", f.pedimentos, false), Triple("Proveedores 69-B", f.terceros69b, f.terceros69b > 0),
      )
      items(kpis) { (n, v, alerta) -> Kpi(n, entero(v), Modifier.width(150.dp), alerta = alerta) }
    }
  }
}

@Composable
private fun Ejecuciones(lista: List<Ejecucion>, abrir: (Ejecucion) -> Unit) {
  Tarjeta {
    TituloTarjeta("Últimas conciliaciones", "Toca una para ver sus resultados", Iconos.Cruce)
    if (lista.isEmpty()) {
      Vacio("Aún no hay conciliaciones", "Ejecuta el cierre del mes desde el Tablero de IVA o una regla desde Conciliar.", icono = Iconos.Cruce)
    }
    lista.take(5).forEach { e ->
      Spacer(Modifier.height(10.dp))
      Tarjeta(onClick = { abrir(e) }, relleno = PaddingValues(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(e.regla, style = Mono.merge(MaterialTheme.typography.titleSmall), color = MaterialTheme.colorScheme.primary)
          Spacer(Modifier.width(8.dp))
          Text(NOMBRE_REGLA[e.regla].orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
          Text("${(e.porcentaje * 100).toInt()}%", style = MaterialTheme.typography.labelLarge.merge(Cifras))
        }
        Text("${fecha(e.desde)} a ${fecha(e.hasta)} · ${fechaHora(e.ejecutadoEn)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        BarraProgreso(e.porcentaje)
        Spacer(Modifier.height(8.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          e.porEstatus.forEach { (s, n) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              ChipEstatus(s)
              Text(" $n", style = MaterialTheme.typography.labelSmall.merge(Mono), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        if (e.importeDiferencias > 0) {
          Spacer(Modifier.height(6.dp))
          Text("Diferencias ${pesos(e.importeDiferencias)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun UltimasCargas(r: Resumen, verTodas: () -> Unit) {
  Tarjeta {
    TituloTarjeta("Últimas cargas", null, Iconos.Subir) { TextButton(onClick = verTodas) { Text("Ir a Cargas") } }
    if (r.cargas.isEmpty()) Text("Sin cargas todavía. Sube CFDI, SAP, bancos o Data Stage desde Cargas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    r.cargas.forEach { cg ->
      Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Pastilla(cg.fuente, COLOR_FUENTE[cg.fuente] ?: MaterialTheme.colorScheme.primary, mono = true)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
          Text(cg.nombreArchivo, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(fechaHora(cg.cargadoEn), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(entero(cg.registros), style = MaterialTheme.typography.labelLarge.merge(Cifras))
      }
    }
  }
}
