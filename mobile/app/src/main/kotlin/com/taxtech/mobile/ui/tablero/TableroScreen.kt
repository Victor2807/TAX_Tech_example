package com.taxtech.mobile.ui.tablero

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.MesIva
import com.taxtech.mobile.ui.comun.AvisoError
import com.taxtech.mobile.ui.comun.BarrasOrdenadas
import com.taxtech.mobile.ui.comun.BotonPrincipal
import com.taxtech.mobile.ui.comun.CAUSA
import com.taxtech.mobile.ui.comun.Cargando
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.DialogoConfirmar
import com.taxtech.mobile.ui.comun.FilaBarra
import com.taxtech.mobile.ui.comun.GraficaLineas
import com.taxtech.mobile.ui.comun.GraficaSaldo
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.Leyenda
import com.taxtech.mobile.ui.comun.NumeroAnimado
import com.taxtech.mobile.ui.comun.ORDEN_CIERRE
import com.taxtech.mobile.ui.comun.SOLO_LECTURA
import com.taxtech.mobile.ui.comun.Serie
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.TituloTarjeta
import com.taxtech.mobile.ui.comun.Vacio
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.comun.compacto
import com.taxtech.mobile.ui.comun.fechaHora
import com.taxtech.mobile.ui.comun.mesCorto
import com.taxtech.mobile.ui.comun.mesLargo
import com.taxtech.mobile.ui.comun.monto
import com.taxtech.mobile.ui.comun.pesos
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class TableroViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(
    val clave: String? = null,
    val meses: List<MesIva> = emptyList(),
    val sel: String? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val cerrando: Boolean = false,
    val resultadoCierre: String? = null,
  )

  val estado = MutableStateFlow(Estado())

  /** Los 12 meses que terminan en el mes activo (`hasta`), como el tablero del panel. */
  fun cargar(empresaId: Long?, mes: String, forzar: Boolean = false) {
    if (empresaId == null) return
    val clave = "$empresaId|$mes"
    if (!forzar && estado.value.clave == clave) return
    estado.update { it.copy(clave = clave, cargando = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargando = false) } }) {
      val meses = repo.tableroIva(empresaId, 12, mes)
      val sel = meses.firstOrNull { it.periodoDesde.startsWith(mes) }?.periodoDesde ?: meses.lastOrNull()?.periodoDesde
      estado.update { it.copy(meses = meses, sel = sel) }
    }
  }

  fun elegir(periodo: String) = estado.update { it.copy(sel = periodo) }

  fun cerrar(empresaId: Long, mes: String, diasC4: Int?, alTerminar: () -> Unit) {
    estado.update { it.copy(cerrando = true, resultadoCierre = null) }
    lanzar(alTerminar = { estado.update { it.copy(cerrando = false) } }) {
      val r = repo.ejecutarCierre(empresaId, mes, diasC4)
      val ok = r.corridas.sumOf { it.totalConciliadas }
      val tot = r.corridas.sumOf { it.totalPartidas }
      estado.update {
        it.copy(resultadoCierre = "${mesLargo("$mes-01")}: ${r.corridas.size} conciliaciones (" + r.corridas.joinToString { c -> "${c.regla} #${c.ejecucionId}" } + ") · $ok de $tot partidas conciliadas.")
      }
      avisar("Conciliaciones de ${mesLargo("$mes-01")} ejecutadas")
      cargar(empresaId, mes, forzar = true)
      alTerminar()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableroScreen(panel: PanelViewModel) {
  val c = LocalContenedor.current
  val vm: TableroViewModel = viewModel(key = "tablero") { TableroViewModel(c) }
  val p by panel.estado.collectAsStateWithLifecycle()
  val mes by panel.mes.collectAsStateWithLifecycle()
  val e by vm.estado.collectAsStateWithLifecycle()
  LaunchedEffect(p.empresaId, mes) { vm.cargar(p.empresaId, mes) }

  val i = e.meses.indexOfFirst { it.periodoDesde == e.sel }
  val x = e.meses.getOrNull(i)
  val previo = if (i > 0) e.meses[i - 1] else null

  PullToRefreshBox(isRefreshing = e.cargando && e.meses.isNotEmpty(), onRefresh = { vm.cargar(p.empresaId, mes, forzar = true) }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      if (e.error != null) item { AvisoError(e.error!!) { vm.cargar(p.empresaId, mes, forzar = true) } }
      if (e.cargando && e.meses.isEmpty()) item { Cargando() }
      if (!e.cargando && e.meses.isEmpty() && e.error == null) {
        item {
          Tarjeta {
            Vacio(
              "Sin determinación de IVA",
              "Todavía no hay una determinación de IVA hasta ${mesLargo("$mes-01")} para esta empresa: ejecuta todas las conciliaciones del mes.", icono = Iconos.Grafica,
            )
          }
        }
      }
      if (x != null) {
        if (!x.periodoDesde.startsWith(mes)) {
          item { AvisoError("${mesLargo("$mes-01")} no tiene C5 ejecutada: se muestra ${mesLargo(x.periodoDesde)}.") }
        }
        item {
          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(e.meses.reversed()) { m ->
              val activo = m.periodoDesde == e.sel
              Text(
                mesCorto(m.periodoDesde),
                style = MaterialTheme.typography.labelLarge,
                color = if (activo) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clip(CircleShape)
                  .background(if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                  .clickable { vm.elegir(m.periodoDesde) }
                  .padding(horizontal = 14.dp, vertical = 8.dp),
              )
            }
          }
        }
        item { Heroe(x, previo) }
        item { Indicadores(x, previo) }
        item {
          val colores = LocalColoresTax.current
          Tarjeta {
            TituloTarjeta("Histórico", "Toca un mes para verlo", Iconos.Grafica)
            Spacer(Modifier.height(12.dp))
            GraficaLineas(
              e.meses.map { mesCorto(it.periodoDesde) },
              listOf(
                Serie("IVA trasladado cobrado", colores.serie1, e.meses.map { it.trasladadoCobrado }),
                Serie("IVA acreditable", colores.serie2, e.meses.map { it.acreditable }),
              ),
              i,
              { k -> vm.elegir(e.meses[k].periodoDesde) },
            )
            Spacer(Modifier.height(8.dp))
            Leyenda(listOf("Trasladado cobrado" to colores.serie1, "Acreditable" to colores.serie2))
          }
        }
        item {
          val colores = LocalColoresTax.current
          Tarjeta {
            TituloTarjeta("Saldo por mes", "A cargo hacia arriba, a favor hacia abajo", Iconos.Balanza)
            Spacer(Modifier.height(12.dp))
            GraficaSaldo(e.meses.map { mesCorto(it.periodoDesde) }, e.meses.map { it.saldo }, i, { k -> vm.elegir(e.meses[k].periodoDesde) })
            Spacer(Modifier.height(8.dp))
            Leyenda(listOf("A cargo" to colores.cargo, "A favor" to colores.favor))
          }
        }
        item {
          Tarjeta {
            TituloTarjeta("IVA no acreditado", "Por causa, en ${mesLargo(x.periodoDesde)}", Iconos.Alerta)
            Spacer(Modifier.height(12.dp))
            BarrasOrdenadas(
              x.noAcreditable.sortedByDescending { it.iva }.map { r -> FilaBarra(CAUSA[r.estatus] ?: r.estatus, "${r.partidas} partida${if (r.partidas == 1L) "" else "s"} · ${r.estatus}", r.iva) },
              "Todo el IVA del mes quedó acreditado.",
            )
          }
        }
        item {
          Tarjeta {
            TituloTarjeta("Importe en riesgo", "Hallazgos por conciliación", Iconos.Cruce)
            Spacer(Modifier.height(12.dp))
            BarrasOrdenadas(
              x.riesgo.filter { it.importe > 0 }.sortedByDescending { it.importe }.map { r -> FilaBarra("${r.regla} · ${r.conciliacion}", "${r.hallazgos} hallazgo${if (r.hallazgos == 1L) "" else "s"}", r.importe) },
              "Sin hallazgos con importe en riesgo en el mes.",
              color = LocalColoresTax.current.serie2,
            )
          }
        }
        item { TablaMeses(e.meses, e.sel) }
      }
      if (p.empresaId != null) {
        item {
          Cierre(
            mes = mes,
            puedeEditar = panel.usuario.rol.puedeEditar,
            politicaC4 = p.resumen?.reglas?.firstOrNull { it.codigo == "C4" }?.toleranciaDias,
            cerrando = e.cerrando,
            resultado = e.resultadoCierre,
          ) { dias -> vm.cerrar(p.empresaId!!, mes, dias) { panel.recargar() } }
        }
      }
    }
  }
}

@Composable
private fun Heroe(x: MesIva, p: MesIva?) {
  val colores = LocalColoresTax.current
  val favor = x.saldo < 0
  val color = if (favor) colores.favor else colores.cargo
  Tarjeta(acento = color, relleno = PaddingValues(20.dp)) {
    Ceja((if (favor) "IVA a favor" else if (x.saldo > 0) "IVA a cargo" else "IVA en cero") + " · " + mesLargo(x.periodoDesde), color = color)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(12.dp).clip(CircleShape).background(color))
      Spacer(Modifier.width(10.dp))
      NumeroAnimado(abs(x.saldo), ::pesos, MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold))
    }
    Spacer(Modifier.height(8.dp))
    Text("Trasladado cobrado ${pesos(x.trasladadoCobrado)} − acreditable ${pesos(x.acreditable)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
      p?.let { "Mes anterior: ${if (it.saldo < 0) "a favor" else "a cargo"} ${pesos(abs(it.saldo))}." } ?: "Primer mes con determinación.",
      style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
      "IVA retenido a enterar: ${pesos(x.retenido)} (se declara aparte). C5 #${x.ejecucionC5 ?: "—"} del ${fechaHora(x.ejecutadoEn)} · ${x.reglasEjecutadas} de 10 conciliaciones.",
      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun Indicadores(x: MesIva, p: MesIva?) {
  val datos = listOf<Triple<String, Double, Double?>>(
    Triple("IVA trasladado", x.trasladado, p?.trasladado),
    Triple("IVA cobrado", x.cobrado, p?.cobrado),
    Triple("Pendiente de cobro", x.pendienteCobro, p?.pendienteCobro),
    Triple("IVA acreditable", x.acreditable, p?.acreditable),
    Triple("IVA pagado", x.pagado, p?.pagado),
    Triple("Pendiente de pago", x.pendientePago, p?.pendientePago),
  )
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    datos.chunked(2).forEach { fila ->
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        fila.forEach { (n, v, ant) ->
          Tarjeta(Modifier.weight(1f), relleno = PaddingValues(14.dp)) {
            Text(n, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            NumeroAnimado(v, ::pesos, MaterialTheme.typography.titleMedium)
            val nota = if (ant == null) "Sin mes anterior" else {
              val dv = v - ant
              (if (dv > 0) "▲ +" else if (dv < 0) "▼ −" else "= ") + (if (dv != 0.0) compacto(abs(dv)) else "sin cambio") + " vs mes ant."
            }
            Text(nota, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
          }
        }
      }
    }
  }
}

@Composable
private fun TablaMeses(meses: List<MesIva>, sel: String?) {
  val cols = listOf<Pair<String, (MesIva) -> Double>>(
    "Trasladado" to { it.trasladado }, "Cobrado" to { it.cobrado }, "Pend. cobro" to { it.pendienteCobro },
    "Notas emitidas" to { it.notasEmitidas }, "Pagado" to { it.pagado }, "Pend. pago" to { it.pendientePago },
    "Acreditable" to { it.acreditable }, "Notas recibidas" to { it.notasRecibidas }, "Saldo" to { it.saldo }, "Retenido" to { it.retenido },
  )
  Tarjeta {
    TituloTarjeta("Determinación por mes", "Saldo: + a cargo / − a favor. Desliza para ver todo.", Iconos.Tabla)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState())) {
      Column {
        Text("Mes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp).padding(vertical = 6.dp))
        meses.forEach { m -> Text(mesLargo(m.periodoDesde), style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (m.periodoDesde == sel) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.width(110.dp).padding(vertical = 6.dp)) }
      }
      cols.forEach { (n, f) ->
        Column(horizontalAlignment = Alignment.End) {
          Text(n, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(112.dp).padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
          meses.forEach { m ->
            Text(
              monto(f(m)), style = MaterialTheme.typography.bodySmall.merge(Cifras).copy(fontWeight = if (m.periodoDesde == sel) FontWeight.Bold else FontWeight.Normal),
              modifier = Modifier.width(112.dp).padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
          }
        }
      }
    }
  }
}

/** Cierre mensual: las 10 reglas del mes activo, en el orden del cierre, en una transacción. */
@Composable
private fun Cierre(mes: String, puedeEditar: Boolean, politicaC4: String?, cerrando: Boolean, resultado: String?, ejecutar: (Int?) -> Unit) {
  var dias by remember(politicaC4) { mutableStateOf(politicaC4.orEmpty()) }
  var confirmar by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  Tarjeta(acento = MaterialTheme.colorScheme.secondary) {
    TituloTarjeta("Cierre mensual", "Ejecuta las 10 conciliaciones de ${mesLargo("$mes-01")}", Iconos.Rayo)
    Spacer(Modifier.height(10.dp))
    Text("Orden: ${ORDEN_CIERRE.joinToString(", ")}. C5 usa los cruces de C1, C3, C4, C7 y C10.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
      value = dias, onValueChange = { dias = it.filter(Char::isDigit).take(2); error = null },
      label = { Text("Tolerancia de días de C4") },
      supportingText = { Text("Política: ${politicaC4 ?: "—"} días. Un cambio solo aplica a este cierre.") },
      enabled = puedeEditar, singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
    )
    if (error != null) Text(error!!, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    BotonPrincipal(
      if (cerrando) "Conciliando…" else "Ejecutar todas las conciliaciones",
      onClick = {
        val d = dias.toIntOrNull()
        if (dias.isNotEmpty() && (d == null || d !in 0..60)) error = "La tolerancia de días de C4 debe ser un entero entre 0 y 60." else confirmar = true
      },
      enabled = puedeEditar, cargando = cerrando, icono = Iconos.Rayo, modifier = Modifier.fillMaxWidth(),
    )
    if (!puedeEditar) Text(SOLO_LECTURA, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    if (resultado != null) {
      Spacer(Modifier.height(10.dp))
      Text(resultado, style = MaterialTheme.typography.bodySmall, color = LocalColoresTax.current.exito)
    }
  }
  if (confirmar) {
    val d = dias.toIntOrNull()
    val pol = politicaC4?.toIntOrNull()
    DialogoConfirmar(
      titulo = "Cierre de ${mesLargo("$mes-01")}",
      texto = "Se ejecutarán las 10 conciliaciones en este orden: ${ORDEN_CIERRE.joinToString(", ")}.\n\n" +
        "C4 usará una tolerancia de ${d ?: pol ?: "—"} días" + (if (d != null && pol != null && d != pol) " (la política es de $pol; el cambio solo aplica a este cierre)" else "") + ".\n\n" +
        "Cada regla usa las fuentes cargadas hasta hoy. Las revisiones de partidas que no cambiaron se conservan.",
      confirmar = "Ejecutar",
      alConfirmar = { ejecutar(if (dias.isEmpty()) null else d) },
      alCerrar = { confirmar = false },
    )
  }
}
