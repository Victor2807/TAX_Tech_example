package com.taxtech.mobile.ui.comun

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.taxtech.mobile.ui.theme.LocalColoresTax

// Constantes de dominio para la interfaz. Siguen `compartido/src/dominio.ts` de la app local.

data class InfoEstatus(val descripcion: String, val color: Color)

private val Esmeralda = Color(0xFF10B981)
private val Ambar = Color(0xFFF59E0B)
private val Naranja = Color(0xFFF97316)
private val Violeta = Color(0xFF8B5CF6)
private val Cielo = Color(0xFF0EA5E9)
private val Turquesa = Color(0xFF14B8A6)
private val Rosa = Color(0xFFF43F5E)
private val Rojo = Color(0xFFEF4444)
private val Fucsia = Color(0xFFD946EF)
private val Amarillo = Color(0xFFEAB308)
private val Pizarra = Color(0xFF64748B)
private val RosaClaro = Color(0xFFEC4899)
private val Indigo = Color(0xFF6366F1)

/** Los 16 estatus de resultado con su descripción y tono (INFO_ESTATUS). */
val ESTATUS: Map<String, InfoEstatus> = linkedMapOf(
  "CONCILIADO" to InfoEstatus("Cruce correcto en todas las fuentes", Esmeralda),
  "DIFERENCIA_IMPORTE" to InfoEstatus("Importes fuera de tolerancia", Ambar),
  "DIFERENCIA_TC" to InfoEstatus("Diferencia por tipo de cambio", Naranja),
  "SOLO_ERP" to InfoEstatus("Registro contable sin CFDI", Violeta),
  "SOLO_CFDI" to InfoEstatus("CFDI no registrado en el ERP", Cielo),
  "SOLO_BANCO" to InfoEstatus("Movimiento bancario sin registro", Turquesa),
  "SOLO_PEDIMENTO" to InfoEstatus("Pedimento sin registro contable", Ambar),
  "CFDI_CANCELADO" to InfoEstatus("CFDI cancelado ante el SAT", Rosa),
  "PROVEEDOR_69B" to InfoEstatus("Emisor en lista 69-B", Rojo),
  "PPD_SIN_REP" to InfoEstatus("Pago diferido sin complemento", Fucsia),
  "PAGO_SIN_BANCO" to InfoEstatus("Pago sin movimiento bancario que lo confirme", Amarillo),
  "SIN_PAGO" to InfoEstatus("Factura sin pagar: IVA pendiente", Pizarra),
  "NOTA_SIN_RELACION" to InfoEstatus("Nota de crédito sin factura relacionada", RosaClaro),
  "RELACION_INVALIDA" to InfoEstatus("La factura relacionada no es válida", Rosa),
  "EXCEDE_SALDO" to InfoEstatus("Las notas superan el total de la factura", Rojo),
  "IVA_NO_PROPORCIONAL" to InfoEstatus("IVA de la nota distinto a la tasa de la factura", Indigo),
)

fun infoEstatus(codigo: String?): InfoEstatus = ESTATUS[codigo] ?: InfoEstatus(codigo.orEmpty(), Pizarra)

val REGLAS = listOf("C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10")

/** Orden del cierre mensual: C5 depende de C1, C3, C4, C7 y C10; C9 va al final. */
val ORDEN_CIERRE = listOf("C1", "C2", "C3", "C4", "C6", "C7", "C8", "C10", "C5", "C9")

val NOMBRE_REGLA = mapOf(
  "C1" to "CFDI recibidos vs cuentas por pagar",
  "C2" to "CFDI emitidos vs ingresos",
  "C3" to "Pagos vs REP",
  "C4" to "Pagos ERP vs banco",
  "C5" to "IVA acreditable en flujo de efectivo",
  "C6" to "Pedimentos vs contabilidad",
  "C7" to "Pago de pedimentos vs banco",
  "C8" to "Facturas de importación vs pedimento",
  "C9" to "Retenciones de ISR e IVA vs entero",
  "C10" to "Notas de crédito vs factura y ERP",
)

/** Causas del IVA no acreditado en el tablero. */
val CAUSA = mapOf(
  "PROVEEDOR_69B" to "Proveedor en lista 69-B", "CFDI_CANCELADO" to "CFDI cancelado", "PPD_SIN_REP" to "Pagada sin complemento de pago",
  "PAGO_SIN_BANCO" to "Pago sin confirmar en el banco", "DIFERENCIA_IMPORTE" to "Diferencia de importe en el pago",
  "DIFERENCIA_TC" to "Diferencia por tipo de cambio", "SIN_PAGO" to "Factura sin pagar (pendiente)", "SOLO_CFDI" to "CFDI sin registro en el ERP",
)

data class Severidad(val clave: String, val nombre: String, val color: Color)

val SEVERIDADES = listOf(
  Severidad("critica", "Crítica", Color(0xFFE11D48)),
  Severidad("alta", "Alta", Ambar),
  Severidad("media", "Media", Cielo),
  Severidad("info", "Info", Color(0xFF94A3B8)),
)

fun severidad(clave: String?) = SEVERIDADES.firstOrNull { it.clave == clave } ?: SEVERIDADES.last()

/** Estados del seguimiento del comparativo. */
val COLOR_ESTADO = mapOf(
  "Abierto" to Ambar, "Cambió de estatus" to Violeta, "Nuevo" to Rosa, "Cerrado" to Esmeralda, "Sin corte" to Pizarra,
)

val COLOR_FUENTE = mapOf("CFDI" to Cielo, "ERP" to Violeta, "BANCO" to Turquesa, "DATASTAGE" to Ambar)

val NOMBRE_FUENTE = mapOf("CFDI" to "CFDI (SAT)", "ERP" to "ERP SAP", "BANCO" to "Estados de cuenta", "DATASTAGE" to "Data Stage")

/** Color de texto legible sobre el fondo tenue del mismo tono, en claro y en oscuro. */
@Composable
fun tonoTexto(c: Color): Color = if (LocalColoresTax.current.oscuro) lerp(c, Color.White, 0.35f) else lerp(c, Color.Black, 0.28f)
