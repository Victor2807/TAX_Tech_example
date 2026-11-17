package com.taxtech.mobile.ui.comun

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taxtech.mobile.ui.theme.Cifras
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Marca
import com.taxtech.mobile.ui.theme.Mono

/** Fondo con tres manchas de luz que se mueven despacio (la «aurora» de TAX Tech). */
@Composable
fun FondoAurora(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
  val c = LocalColoresTax.current
  val fondo = MaterialTheme.colorScheme.background
  val t = rememberInfiniteTransition(label = "aurora")
  val fase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Reverse), label = "fase")
  Box(
    modifier.fillMaxSize().drawBehind {
      drawRect(fondo)
      fun mancha(cx: Float, cy: Float, r: Float, color: Color) =
        drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = r), radius = r, center = Offset(cx, cy))
      val w = size.width
      val h = size.height
      mancha(w * (0.1f + 0.35f * fase), h * 0.08f, w * 0.95f, c.aurora1)
      mancha(w * (1.0f - 0.3f * fase), h * (0.42f + 0.12f * fase), w * 0.8f, c.aurora2)
      mancha(w * (0.25f + 0.2f * fase), h * (1.0f - 0.18f * fase), w * 0.9f, c.aurora3)
    },
    content = content,
  )
}

/** Tarjeta translúcida con borde de luz. `acento` dibuja una franja de color a la izquierda. */
@Composable
fun Tarjeta(
  modifier: Modifier = Modifier,
  acento: Color? = null,
  onClick: (() -> Unit)? = null,
  relleno: PaddingValues = PaddingValues(16.dp),
  content: @Composable ColumnScope.() -> Unit,
) {
  val c = LocalColoresTax.current
  val forma = RoundedCornerShape(22.dp)
  Column(
    modifier
      .clip(forma)
      .background(c.vidrio)
      .border(1.dp, Brush.verticalGradient(listOf(c.bordeVidrio, c.bordeVidrio.copy(alpha = 0.04f))), forma)
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .then(
        if (acento != null) Modifier.drawBehind {
          drawRoundRect(Brush.verticalGradient(listOf(acento, acento.copy(alpha = 0.2f))), Offset(0f, 0f), Size(4.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
        } else Modifier,
      )
      .padding(relleno),
    content = content,
  )
}

/** Texto pequeño en mayúsculas sobre un título. */
@Composable
fun Ceja(texto: String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
  Text(texto.uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp), color = color, modifier = modifier)
}

@Composable
fun TituloTarjeta(titulo: String, subtitulo: String? = null, icono: ImageVector? = null, accion: (@Composable () -> Unit)? = null) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    if (icono != null) {
      Box(
        Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
      ) { Icon(icono, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
      Spacer(Modifier.width(12.dp))
    }
    Column(Modifier.weight(1f)) {
      Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
      if (subtitulo != null) Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    accion?.invoke()
  }
}

/** Pastilla de color tenue con texto del mismo tono. */
@Composable
fun Pastilla(texto: String, color: Color, modifier: Modifier = Modifier, mono: Boolean = false, punto: Boolean = false) {
  Row(
    modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 3.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (punto) {
      Box(Modifier.size(6.dp).clip(CircleShape).background(color))
      Spacer(Modifier.width(5.dp))
    }
    Text(
      texto,
      style = (if (mono) Mono else TextStyle.Default).merge(MaterialTheme.typography.labelSmall).copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
      color = tonoTexto(color),
      maxLines = 1,
    )
  }
}

@Composable
fun ChipEstatus(codigo: String?, modifier: Modifier = Modifier) {
  val info = infoEstatus(codigo)
  Pastilla(codigo ?: "—", info.color, modifier.semantics { contentDescription = "$codigo: ${info.descripcion}" }, mono = true, punto = true)
}

/** Cifra que se anima desde su valor anterior hasta el nuevo. */
@Composable
fun NumeroAnimado(valor: Double, formato: (Double) -> String, style: TextStyle, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
  var mostrado by remember { mutableDoubleStateOf(0.0) }
  LaunchedEffect(valor) {
    val inicio = mostrado
    val a = Animatable(0f)
    a.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) { mostrado = inicio + (valor - inicio) * this.value }
    mostrado = valor
  }
  Text(formato(mostrado), style = style.merge(Cifras), color = color, modifier = modifier, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Anillo de avance con degradado esmeralda → oro y contenido al centro. */
@Composable
fun Anillo(progreso: Float, modifier: Modifier = Modifier, tamano: Dp = 120.dp, grosor: Dp = 12.dp, content: @Composable BoxScope.() -> Unit = {}) {
  val c = LocalColoresTax.current
  val p by animateFloatAsState(progreso.coerceIn(0f, 1f), tween(1100, easing = FastOutSlowInEasing), label = "anillo")
  val pista = c.rejilla
  Box(modifier.size(tamano), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val g = grosor.toPx()
      val arco = Size(size.width - g, size.height - g)
      val esquina = Offset(g / 2, g / 2)
      drawArc(pista, 0f, 360f, false, esquina, arco, style = Stroke(g))
      drawArc(
        Brush.sweepGradient(listOf(Marca.Esmeralda500, Marca.Esmeralda300, Marca.Oro400, Marca.Esmeralda500)),
        -90f, 360f * p, false, esquina, arco, style = Stroke(g, cap = StrokeCap.Round),
      )
    }
    content()
  }
}

@Composable
fun BarraProgreso(fraccion: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, alto: Dp = 6.dp) {
  val f by animateFloatAsState(fraccion.coerceIn(0f, 1f), tween(800, easing = FastOutSlowInEasing), label = "barra")
  Box(modifier.fillMaxWidth().height(alto).clip(CircleShape).background(LocalColoresTax.current.rejilla)) {
    Box(Modifier.fillMaxWidth(f).height(alto).clip(CircleShape).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))))
  }
}

/** Indicador: valor grande y su contexto pequeño. */
@Composable
fun Kpi(etiqueta: String, valor: String, modifier: Modifier = Modifier, nota: String? = null, alerta: Boolean = false, icono: ImageVector? = null) {
  val c = LocalColoresTax.current
  Tarjeta(modifier, acento = if (alerta) c.peligro else null, relleno = PaddingValues(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (icono != null) {
        Icon(icono, null, tint = if (alerta) c.peligro else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
      }
      Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(6.dp))
    Text(
      valor,
      style = MaterialTheme.typography.titleLarge.merge(Cifras),
      color = if (alerta) c.peligro else MaterialTheme.colorScheme.onSurface,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (nota != null) Text(nota, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
  }
}

@Composable
fun Cargando(modifier: Modifier = Modifier, texto: String = "Cargando…") {
  Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
    Spacer(Modifier.height(12.dp))
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

/** Estado vacío con explicación y, si aplica, una acción. */
@Composable
fun Vacio(titulo: String, texto: String, icono: ImageVector = Iconos.Mosaico, modifier: Modifier = Modifier, accion: (@Composable () -> Unit)? = null) {
  Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
      Icon(icono, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
    }
    Spacer(Modifier.height(12.dp))
    Text(titulo, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(4.dp))
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 320.dp))
    if (accion != null) { Spacer(Modifier.height(12.dp)); accion() }
  }
}

@Composable
fun AvisoError(mensaje: String, modifier: Modifier = Modifier, reintentar: (() -> Unit)? = null) {
  val c = LocalColoresTax.current
  Tarjeta(modifier.fillMaxWidth(), acento = c.peligro) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(Iconos.Alerta, null, tint = c.peligro, modifier = Modifier.size(20.dp))
      Spacer(Modifier.width(10.dp))
      Text(mensaje, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
      if (reintentar != null) TextButton(onClick = reintentar) { Text("Reintentar") }
    }
  }
}

/** Botón principal en forma de píldora con degradado de la marca. */
@Composable
fun BotonPrincipal(
  texto: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  cargando: Boolean = false,
  icono: ImageVector? = null,
  peligro: Boolean = false,
) {
  val c = LocalColoresTax.current
  val activo = enabled && !cargando
  val colores = if (peligro) listOf(Color(0xFFE11D48), Color(0xFFF43F5E)) else listOf(Marca.Esmeralda600, Marca.Esmeralda400)
  Row(
    modifier
      .height(48.dp)
      .clip(CircleShape)
      .background(if (activo) Brush.horizontalGradient(colores) else Brush.horizontalGradient(listOf(c.rejilla, c.rejilla)))
      .clickable(enabled = activo, onClick = onClick)
      .padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    val color = if (activo) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    if (cargando) {
      CircularProgressIndicator(strokeWidth = 2.dp, color = color, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(10.dp))
    } else if (icono != null) {
      Icon(icono, null, tint = color, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
    }
    Text(texto, color = color, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
  }
}

/** Botón secundario: contorno sutil. */
@Composable
fun BotonSecundario(texto: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icono: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary) {
  val c = LocalColoresTax.current
  Row(
    modifier
      .height(40.dp)
      .clip(CircleShape)
      .border(1.dp, if (enabled) color.copy(alpha = 0.5f) else c.rejilla, CircleShape)
      .clickable(enabled = enabled, onClick = onClick)
      .padding(horizontal = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    val tinte = if (enabled) color else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    if (icono != null) { Icon(icono, null, tint = tinte, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
    Text(texto, color = tinte, style = MaterialTheme.typography.labelLarge, maxLines = 1)
  }
}

/** Selector compacto: ‹ Octubre 2026 › */
@Composable
fun SelectorMes(mes: String, alCambiar: (String) -> Unit, modifier: Modifier = Modifier, maximo: String? = null) {
  Row(
    modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Iconos.Izquierda, "Mes anterior", tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.clip(CircleShape).clickable { alCambiar(sumarMes(mes, -1)) }.padding(8.dp).size(18.dp),
    )
    Text(mesLargo("$mes-01"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    val puedeAvanzar = maximo == null || sumarMes(mes, 1) <= maximo
    Icon(
      Iconos.Derecha, "Mes siguiente", tint = if (puedeAvanzar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
      modifier = Modifier.clip(CircleShape).clickable(enabled = puedeAvanzar) { alCambiar(sumarMes(mes, 1)) }.padding(8.dp).size(18.dp),
    )
  }
}

/** Fila «etiqueta ······ valor». */
@Composable
fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier, destacado: Boolean = false, color: Color = Color.Unspecified, mono: Boolean = false) {
  Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(etiqueta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    Text(
      valor,
      style = (if (mono) Mono else Cifras).merge(if (destacado) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium),
      color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun DialogoConfirmar(
  titulo: String,
  texto: String,
  confirmar: String,
  alConfirmar: () -> Unit,
  alCerrar: () -> Unit,
  peligro: Boolean = false,
  extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
  AlertDialog(
    onDismissRequest = alCerrar,
    title = { Text(titulo) },
    text = {
      Column {
        Text(texto, style = MaterialTheme.typography.bodyMedium)
        extra?.invoke(this)
      }
    },
    confirmButton = {
      TextButton(onClick = { alConfirmar(); alCerrar() }) {
        Text(confirmar, color = if (peligro) LocalColoresTax.current.peligro else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
      }
    },
    dismissButton = { TextButton(onClick = alCerrar) { Text("Cancelar") } },
  )
}

/** Monograma de TAX Tech: rombo con degradado y las iniciales. */
@Composable
fun Monograma(modifier: Modifier = Modifier, tamano: Dp = 44.dp) {
  Box(
    modifier.size(tamano).clip(RoundedCornerShape(tamano * 0.32f))
      .background(Brush.linearGradient(listOf(Marca.Esmeralda500, Marca.Esmeralda700, Marca.Oro500))),
    contentAlignment = Alignment.Center,
  ) {
    Text("TT", color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = (tamano.value * 0.38f).sp, letterSpacing = (-1).sp))
  }
}

/** Texto de rol para LECTOR en botones deshabilitados. */
const val SOLO_LECTURA = "Tu perfil es de solo lectura"
