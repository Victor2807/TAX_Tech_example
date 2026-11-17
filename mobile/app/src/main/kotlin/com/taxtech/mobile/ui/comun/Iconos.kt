package com.taxtech.mobile.ui.comun

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Íconos de trazo propios (24×24, línea de 1.75), sin depender de una biblioteca de íconos.
// Se tiñen con el `tint` de Icon como cualquier ImageVector.

private fun trazo(nombre: String, vararg rutas: String): ImageVector =
  ImageVector.Builder(nombre, 24.dp, 24.dp, 24f, 24f).apply {
    rutas.forEach {
      addPath(
        pathData = addPathNodes(it),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.75f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }
  }.build()

object Iconos {
  val Inicio by lazy { trazo("inicio", "M3 11l9-8 9 8", "M5 10v10h5v-6h4v6h5V10") }
  val Grafica by lazy { trazo("grafica", "M4 20V11", "M10 20V4", "M16 20v-7", "M21 20H3") }
  val Cruce by lazy { trazo("cruce", "M4 7h15", "M16 4l3 3-3 3", "M20 17H5", "M8 14l-3 3 3 3") }
  val Balanza by lazy { trazo("balanza", "M12 3v18", "M8 21h8", "M5 7h14", "M5 7l-3 6a3 3 0 0 0 6 0z", "M19 7l-3 6a3 3 0 0 0 6 0z") }
  val Tabla by lazy { trazo("tabla", "M3 5h18v14H3z", "M3 10h18", "M3 15h18", "M9 5v14") }
  val Subir by lazy { trazo("subir", "M12 15V3", "M7 8l5-5 5 5", "M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4") }
  val Escudo by lazy { trazo("escudo", "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z", "M9 12l2 2 4-4") }
  val Mosaico by lazy { trazo("mosaico", "M4 4h6v6H4z", "M14 4h6v6h-6z", "M4 14h6v6H4z", "M14 14h6v6h-6z") }
  val Salir by lazy { trazo("salir", "M15 4h4a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1h-4", "M10 17l5-5-5-5", "M15 12H3") }
  val Buscar by lazy { trazo("buscar", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14", "M21 21l-5-5") }
  val Mas by lazy { trazo("mas", "M12 5v14", "M5 12h14") }
  val Cerrar by lazy { trazo("cerrar", "M6 6l12 12", "M18 6L6 18") }
  val Palomita by lazy { trazo("palomita", "M5 12l5 5 9-10") }
  val Recargar by lazy { trazo("recargar", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5") }
  val Lapiz by lazy { trazo("lapiz", "M4 20h4L19 9l-4-4L4 16z", "M14 6l4 4") }
  val Basura by lazy { trazo("basura", "M4 7h16", "M10 11v6", "M14 11v6", "M6 7l1 13h10l1-13", "M9 7V4h6v3") }
  val Alerta by lazy { trazo("alerta", "M12 3l10 18H2z", "M12 10v5", "M12 18v.01") }
  val Ocultar by lazy { trazo("ocultar", "M3 3l18 18", "M10.6 10.6a2 2 0 0 0 2.8 2.8", "M9.9 5.1A10 10 0 0 1 12 5c5 0 9 4.5 10 7a12 12 0 0 1-3 4", "M6.6 6.6C4.4 8 2.9 10 2 12c1 2.5 5 7 10 7a10 10 0 0 0 4.4-1") }
  val Ojo by lazy { trazo("ojo", "M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z", "M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6") }
  val Filtro by lazy { trazo("filtro", "M3 5h18l-7 8v6l-4 2v-8z") }
  val Derecha by lazy { trazo("derecha", "M9 6l6 6-6 6") }
  val Izquierda by lazy { trazo("izquierda", "M15 6l-6 6 6 6") }
  val Abajo by lazy { trazo("abajo", "M6 9l6 6 6-6") }
  val Usuario by lazy { trazo("usuario", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M4 21a8 8 0 0 1 16 0") }
  val Usuarios by lazy { trazo("usuarios", "M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M2 21a7 7 0 0 1 14 0", "M17 3.5a4 4 0 0 1 0 7.5", "M22 21a7 7 0 0 0-4-6.3") }
  val Candado by lazy { trazo("candado", "M6 11h12v10H6z", "M8 11V7a4 4 0 0 1 8 0v4") }
  val Llave by lazy { trazo("llave", "M8 15a4 4 0 1 0 0-8 4 4 0 0 0 0 8z", "M11 12l9 9", "M17 18l2-2", "M15 20l2-2") }
  val Correo by lazy { trazo("correo", "M3 6h18v12H3z", "M3 7l9 6 9-6") }
  val Rayo by lazy { trazo("rayo", "M13 2L4 14h7l-1 8 9-12h-7z") }
  val Calendario by lazy { trazo("calendario", "M4 6h16v15H4z", "M4 10h16", "M8 3v4", "M16 3v4") }
  val Historial by lazy { trazo("historial", "M3 12a9 9 0 1 0 3-6.7", "M3 4v5h5", "M12 7v5l3 3") }
  val Enlace by lazy { trazo("enlace", "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1", "M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1") }
  val Servidor by lazy { trazo("servidor", "M4 4h16v6H4z", "M4 14h16v6H4z", "M8 7h.01", "M8 17h.01") }
  val Edificio by lazy { trazo("edificio", "M4 21V5l8-3v19", "M12 21V9l8 3v9", "M2 21h20", "M7 8h2", "M7 12h2", "M7 16h2", "M15 14h2", "M15 17h2") }
  val Archivo by lazy { trazo("archivo", "M14 3H6v18h12V7z", "M14 3v4h4") }
  val Ordenar by lazy { trazo("ordenar", "M7 4v16", "M4 7l3-3 3 3", "M17 20V4", "M14 17l3 3 3-3") }
  val Nota by lazy { trazo("nota", "M5 4h14v16H5z", "M9 9h6", "M9 13h6", "M9 17h3") }
  val Sol by lazy { trazo("sol", "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8z", "M12 2v2", "M12 20v2", "M4.9 4.9l1.4 1.4", "M17.7 17.7l1.4 1.4", "M2 12h2", "M20 12h2", "M4.9 19.1l1.4-1.4", "M17.7 6.3l1.4-1.4") }
}
