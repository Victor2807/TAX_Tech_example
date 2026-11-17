package com.taxtech.mobile.ui.comun

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import kotlin.math.abs

// Gráficos propios en Canvas (igual que el panel, sin biblioteca de gráficas). Tocar un mes lo selecciona.

data class Serie(val nombre: String, val color: Color, val valores: List<Double>)

@Composable
fun Leyenda(series: List<Pair<String, Color>>) {
  Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
    series.forEach { (n, c) ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(6.dp))
        Text(n, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

/** Líneas por mes con eje en pesos compactos; el mes seleccionado lleva guía vertical. */
@Composable
fun GraficaLineas(etiquetas: List<String>, series: List<Serie>, seleccionado: Int, alSeleccionar: (Int) -> Unit, modifier: Modifier = Modifier) {
  val c = LocalColoresTax.current
  val medidor = rememberTextMeasurer()
  val estiloEje = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant).merge(Cifras)
  val superficie = MaterialTheme.colorScheme.surface
  val n = etiquetas.size
  var dibujo by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(series) { dibujo = 0f; dibujo = 1f }
  val avance by animateFloatAsState(dibujo, tween(1000, easing = FastOutSlowInEasing), label = "lineas")
  val todos = series.flatMap { it.valores }.ifEmpty { listOf(0.0) }
  val marcas = marcasEje(todos.min(), todos.max())
  val y0 = marcas.first()
  val y1 = marcas.last()
  Canvas(
    modifier.fillMaxWidth().height(200.dp)
      .semantics { contentDescription = "Histórico mensual: " + etiquetas.indices.joinToString("; ") { i -> etiquetas[i] + " " + series.joinToString { s -> s.nombre + " " + pesos(s.valores[i]) } } }
      .pointerInput(n) {
        detectTapGestures { p ->
          if (n == 0) return@detectTapGestures
          val izq = 48.dp.toPx(); val der = size.width - 12.dp.toPx()
          val paso = if (n == 1) 0f else (der - izq) / (n - 1)
          val i = if (n == 1) 0 else ((p.x - izq) / paso).let { Math.round(it) }.coerceIn(0, n - 1)
          alSeleccionar(i)
        }
      },
  ) {
    val izq = 48.dp.toPx(); val der = size.width - 12.dp.toPx(); val arr = 8.dp.toPx(); val abj = size.height - 22.dp.toPx()
    fun x(i: Int) = if (n == 1) (izq + der) / 2 else izq + (der - izq) * i / (n - 1)
    fun y(v: Double) = (abj - (v - y0) / (y1 - y0) * (abj - arr)).toFloat()
    marcas.forEach { m ->
      drawLine(if (m == 0.0) c.eje else c.rejilla, Offset(izq, y(m)), Offset(der, y(m)), 1.dp.toPx())
      val t = medidor.measure(compacto(m), estiloEje)
      drawText(t, topLeft = Offset(izq - t.size.width - 6.dp.toPx(), y(m) - t.size.height / 2))
    }
    etiquetas.forEachIndexed { i, e ->
      if (n > 7 && i % 2 == 1 && i != n - 1) return@forEachIndexed
      val t = medidor.measure(e, estiloEje)
      drawText(t, topLeft = Offset(x(i) - t.size.width / 2, abj + 6.dp.toPx()))
    }
    if (seleccionado in 0 until n) drawLine(c.eje, Offset(x(seleccionado), arr), Offset(x(seleccionado), abj), 1.dp.toPx())
    series.forEach { s ->
      if (n > 1) {
        val ruta = Path()
        s.valores.forEachIndexed { i, v -> if (i == 0) ruta.moveTo(x(i), y(v)) else ruta.lineTo(x(i), y(v)) }
        // Área tenue bajo la línea
        val area = Path().apply { addPath(ruta); lineTo(x(n - 1), abj); lineTo(x(0), abj); close() }
        drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.18f * avance), Color.Transparent), startY = arr, endY = abj))
        clipRectAvance(izq, der, avance) { drawPath(ruta, s.color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)) }
      }
      s.valores.forEachIndexed { i, v ->
        val r = if (i == seleccionado) 5.5.dp.toPx() else 3.5.dp.toPx()
        drawCircle(superficie, r + 2.dp.toPx(), Offset(x(i), y(v)))
        drawCircle(s.color, r, Offset(x(i), y(v)))
      }
    }
  }
}

private inline fun androidx.compose.ui.graphics.drawscope.DrawScope.clipRectAvance(izq: Float, der: Float, avance: Float, bloque: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
  drawContext.canvas.save()
  drawContext.canvas.clipRect(0f, 0f, izq + (der - izq) * avance + 8f, size.height)
  bloque()
  drawContext.canvas.restore()
}

/** Columnas divergentes desde el cero: a cargo hacia arriba, a favor hacia abajo. */
@Composable
fun GraficaSaldo(etiquetas: List<String>, valores: List<Double>, seleccionado: Int, alSeleccionar: (Int) -> Unit, modifier: Modifier = Modifier) {
  val c = LocalColoresTax.current
  val medidor = rememberTextMeasurer()
  val estiloEje = TextStyle(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant).merge(Cifras)
  val estiloValor = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface).merge(Cifras)
  val n = valores.size
  var crecer by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(valores) { crecer = 0f; crecer = 1f }
  val avance by animateFloatAsState(crecer, tween(900, easing = FastOutSlowInEasing), label = "saldo")
  val marcas = marcasEje(valores.minOrNull() ?: 0.0, valores.maxOrNull() ?: 0.0)
  val y0 = marcas.first(); val y1 = marcas.last()
  Canvas(
    modifier.fillMaxWidth().height(200.dp)
      .semantics { contentDescription = "Saldo de IVA por mes: " + etiquetas.indices.joinToString("; ") { i -> etiquetas[i] + (if (valores[i] >= 0) " a cargo " else " a favor ") + pesos(abs(valores[i])) } }
      .pointerInput(n) {
        detectTapGestures { p ->
          if (n == 0) return@detectTapGestures
          val izq = 48.dp.toPx(); val banda = (size.width - 8.dp.toPx() - izq) / n
          alSeleccionar(((p.x - izq) / banda).toInt().coerceIn(0, n - 1))
        }
      },
  ) {
    val izq = 48.dp.toPx(); val der = size.width - 8.dp.toPx(); val arr = 18.dp.toPx(); val abj = size.height - 22.dp.toPx()
    fun y(v: Double) = (abj - (v - y0) / (y1 - y0) * (abj - arr)).toFloat()
    marcas.forEach { m ->
      drawLine(if (m == 0.0) c.eje else c.rejilla, Offset(izq, y(m)), Offset(der, y(m)), 1.dp.toPx())
      val t = medidor.measure(compacto(m), estiloEje)
      drawText(t, topLeft = Offset(izq - t.size.width - 6.dp.toPx(), y(m) - t.size.height / 2))
    }
    if (n == 0) return@Canvas
    val banda = (der - izq) / n
    val ancho = minOf(26.dp.toPx(), banda * 0.6f)
    valores.forEachIndexed { i, v ->
      val cx = izq + banda * (i + 0.5f)
      val cero = y(0.0)
      val tope = cero + (y(v) - cero) * avance
      val sel = i == seleccionado
      val color = (if (v >= 0) c.cargo else c.favor).copy(alpha = if (sel) 1f else 0.5f)
      val arriba = minOf(cero, tope)
      drawRoundRect(color, Offset(cx - ancho / 2, arriba), Size(ancho, abs(tope - cero).coerceAtLeast(1f)), CornerRadius(5.dp.toPx()))
      if (n <= 7 || i % 2 == 0 || i == n - 1) {
        val t = medidor.measure(etiquetas[i], estiloEje)
        drawText(t, topLeft = Offset(cx - t.size.width / 2, abj + 6.dp.toPx()))
      }
      if (sel) {
        val t = medidor.measure(compacto(v), estiloValor)
        drawText(t, topLeft = Offset(cx - t.size.width / 2, if (v >= 0) tope - t.size.height - 2.dp.toPx() else tope + 2.dp.toPx()))
      }
    }
  }
}

data class FilaBarra(val nombre: String, val sub: String?, val valor: Double)

/** Barras horizontales ordenadas de un solo tono, con el valor al final. */
@Composable
fun BarrasOrdenadas(filas: List<FilaBarra>, vacio: String, modifier: Modifier = Modifier, color: Color = LocalColoresTax.current.serie1) {
  if (filas.isEmpty()) {
    Text(vacio, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = 8.dp))
    return
  }
  val max = filas.maxOf { it.valor }.takeIf { it > 0 } ?: 1.0
  Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    filas.forEach { f ->
      var objetivo by remember(f) { mutableFloatStateOf(0f) }
      LaunchedEffect(f) { objetivo = (f.valor / max).toFloat() }
      val frac by animateFloatAsState(objetivo, tween(800, easing = FastOutSlowInEasing), label = "barra")
      Column {
        Row(verticalAlignment = Alignment.Bottom) {
          Column(Modifier.weight(1f)) {
            Text(f.nombre, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (f.sub != null) Text(f.sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
          }
          Text(compacto(f.valor), style = MaterialTheme.typography.labelLarge.merge(Cifras), color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(LocalColoresTax.current.rejilla)) {
          Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
        }
      }
    }
  }
}
