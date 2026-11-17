package com.taxtech.mobile.ui.comun

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

// Formatos de cifras y fechas, iguales a los del panel web (es-MX).

val LocaleMx: Locale = Locale.forLanguageTag("es-MX")

private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")
private val MESES_CORTOS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

fun monto(v: Double?): String = if (v == null) "—" else String.format(LocaleMx, "%,.2f", v)

fun pesos(v: Double?): String = if (v == null) "—" else (if (v < 0) "−$" else "$") + monto(abs(v))

fun entero(v: Long): String = String.format(LocaleMx, "%,d", v)

/** $1.2M, $45K, $980: para ejes y etiquetas de gráficos. */
fun compacto(v: Double): String {
  val a = abs(v)
  val sg = if (v < 0) "−" else ""
  return when {
    a >= 1e6 -> sg + "$" + (if (a >= 1e7) String.format(Locale.US, "%.0f", a / 1e6) else String.format(Locale.US, "%.1f", a / 1e6).removeSuffix(".0")) + "M"
    a >= 1e4 -> sg + "$" + (a / 1e3).roundToLong() + "K"
    else -> sg + "$" + entero(a.roundToLong())
  }
}

private fun anioMes(fecha: String): Pair<Int, Int>? {
  val m = Regex("""^(\d{4})-(\d{2})""").find(fecha) ?: return null
  val mes = m.groupValues[2].toInt()
  return if (mes in 1..12) m.groupValues[1].toInt() to mes else null
}

/** "2026-10-01" → "Octubre 2026" */
fun mesLargo(fecha: String): String = anioMes(fecha)?.let { (a, m) -> MESES[m - 1].replaceFirstChar { it.uppercase() } + " " + a } ?: fecha

/** "2026-10-01" → "oct 26" */
fun mesCorto(fecha: String): String = anioMes(fecha)?.let { (a, m) -> MESES_CORTOS[m - 1] + " " + (a % 100).toString().padStart(2, '0') } ?: fecha

/** "2026-10-31" → "31/10/2026". Las fechas sin zona se muestran como vienen. */
fun fecha(f: String?): String {
  if (f.isNullOrBlank()) return "—"
  val m = Regex("""^(\d{4})-(\d{2})-(\d{2})""").find(f) ?: return f
  return "${m.groupValues[3]}/${m.groupValues[2]}/${m.groupValues[1]}"
}

private val RE_TSTZ = Regex("""^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?(?:\.\d+)?(Z|[+-]\d{2}(?::?\d{2})?)$""")

/** Fecha con zona horaria (timestamptz) en la hora local del dispositivo: dd/mm/aaaa hh:mm. */
fun fechaHora(f: String?, zona: TimeZone = TimeZone.getDefault()): String {
  if (f.isNullOrBlank()) return "—"
  val m = RE_TSTZ.find(f) ?: return f.take(16).replace('T', ' ')
  val g = m.groupValues
  val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
  cal.clear()
  cal.set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
  val z = g[7]
  var desfase = 0
  if (z != "Z") {
    val signo = if (z[0] == '-') -1 else 1
    val digitos = z.substring(1).replace(":", "")
    desfase = signo * (digitos.take(2).toInt() * 60 + (digitos.drop(2).ifEmpty { "0" }.toInt()))
  }
  val millis = cal.timeInMillis - desfase * 60_000L
  val formato = SimpleDateFormat("dd/MM/yyyy HH:mm", LocaleMx)
  formato.timeZone = zona
  return formato.format(Date(millis))
}

/** Primer y último día del mes "AAAA-MM". */
fun limitesMes(mes: String): Pair<String, String> {
  val (a, m) = anioMes("$mes-01") ?: error("Mes no válido: $mes")
  val dias = intArrayOf(31, if ((a % 4 == 0 && a % 100 != 0) || a % 400 == 0) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)[m - 1]
  val mm = m.toString().padStart(2, '0')
  return "$a-$mm-01" to "$a-$mm-${dias.toString().padStart(2, '0')}"
}

/** Suma meses a "AAAA-MM". */
fun sumarMes(mes: String, n: Int): String {
  val (a, m) = anioMes("$mes-01") ?: return mes
  val total = a * 12 + (m - 1) + n
  return "${total / 12}-${(total % 12 + 1).toString().padStart(2, '0')}"
}

/** Mes anterior al actual, "AAAA-MM": el periodo que normalmente se concilia. */
fun mesAnterior(hoy: Calendar = Calendar.getInstance()): String {
  val actual = "${hoy.get(Calendar.YEAR)}-${(hoy.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}"
  return sumarMes(actual, -1)
}

/** Aviso si el periodo no es un mes calendario completo (las reglas se diseñaron por mes). */
fun avisoPeriodo(desde: String, hasta: String, regla: String): String? {
  if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(desde) || !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(hasta) || desde > hasta) return null
  val (_, fin) = limitesMes(desde.take(7))
  if (desde.endsWith("-01") && hasta == fin) return null
  val inicio = if (desde.take(7) != hasta.take(7)) "El periodo abarca más de un mes. " else "El periodo no es un mes completo. "
  return inicio + when (regla) {
    "C5" -> "C5 calcula el IVA acreditable del mes; con varios meses el papel de trabajo y la declaración no cuadran."
    "C9" -> "C9 compara las retenciones del periodo contra el entero del mes siguiente al final; con varios meses aparecen diferencias de entero que no son reales."
    "C10" -> "C10 calcula el IVA de las notas de crédito del mes; con varios meses se resta en un solo periodo."
    else -> "Los hallazgos, las alertas y el comparativo se agrupan por mes; ejecuta del día 1 al último día del mes."
  }
}

/** Marcas "limpias" para un eje entre min y max; siempre incluyen el cero. */
fun marcasEje(min: Double, max: Double, n: Int = 4): List<Double> {
  var lo = minOf(0.0, min)
  var hi = maxOf(0.0, max)
  if (hi == lo) hi = lo + 1.0
  val bruto = (hi - lo) / n
  val mag = 10.0.pow(floor(log10(bruto)))
  val norm = bruto / mag
  val paso = mag * when {
    norm <= 1.0 -> 1.0
    norm <= 2.0 -> 2.0
    norm <= 2.5 -> 2.5
    norm <= 5.0 -> 5.0
    else -> 10.0
  }
  lo = floor(lo / paso) * paso
  hi = ceil(hi / paso) * paso
  val salida = mutableListOf<Double>()
  var v = lo
  while (v <= hi + paso / 2) {
    salida += if (abs(v) < paso / 1e6) 0.0 else v
    v += paso
  }
  return salida
}

/** Parte local del correo, con mayúscula: "ana.lopez@x.mx" → "Ana". */
fun nombreDeCorreo(email: String): String =
  email.substringBefore('@').split('.', '_', '-').firstOrNull { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: email
