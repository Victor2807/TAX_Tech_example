package com.taxtech.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

// Lectura tolerante del JSON de la API: los importes (Decimal) viajan como texto y los ids
// como número, así que todo se convierte al leer.

val JsonApi = Json { ignoreUnknownKeys = true; isLenient = true }

fun JsonElement?.campo(clave: String): JsonElement? = (this as? JsonObject)?.get(clave)

fun JsonElement?.obj(): JsonObject? = this as? JsonObject

fun JsonElement?.lista(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

fun JsonElement?.texto(): String? {
  val p = this as? JsonPrimitive ?: return null
  if (p is JsonNull) return null
  return p.content
}

fun JsonElement?.numero(): Double? = texto()?.toDoubleOrNull()

fun JsonElement?.entero(): Long? = texto()?.let { it.toLongOrNull() ?: it.toDoubleOrNull()?.toLong() }

fun JsonElement?.booleano(): Boolean? = when (val t = texto()?.lowercase()) {
  null -> null
  "true", "t", "1" -> true
  "false", "f", "0" -> false
  else -> t.toBooleanStrictOrNull()
}

fun JsonElement?.esNulo(): Boolean = this == null || this is JsonNull

/** Texto para mostrar cualquier valor: objetos y listas como JSON compacto. */
fun JsonElement?.textoVisible(): String? = when {
  this.esNulo() -> null
  this is JsonPrimitive -> content
  else -> toString()
}

fun JsonObject.mapaEnteros(): Map<String, Long> = mapNotNull { (k, v) -> v.entero()?.let { k to it } }.toMap()

