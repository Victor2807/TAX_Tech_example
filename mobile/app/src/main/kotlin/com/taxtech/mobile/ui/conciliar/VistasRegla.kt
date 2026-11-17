package com.taxtech.mobile.ui.conciliar

import com.taxtech.mobile.data.entero
import com.taxtech.mobile.data.numero
import com.taxtech.mobile.data.texto
import com.taxtech.mobile.ui.comun.fecha
import com.taxtech.mobile.ui.comun.pesos
import kotlinx.serialization.json.JsonObject

// Qué se muestra de cada partida según la regla. Equivale a las columnas por regla del panel
// (componentes/conciliar/columnas.tsx), adaptadas a una tarjeta: un título, detalles y dos importes.

private fun JsonObject.t(k: String): String? = this[k].texto()?.takeIf { it.isNotBlank() }
private fun JsonObject.n(k: String): Double? = this[k].numero()

data class VistaRegla(
  val etiquetaA: String,
  val etiquetaB: String,
  val titulo: (JsonObject) -> String,
  val detalles: (JsonObject) -> List<String>,
)

private fun docErp(f: JsonObject): String? = f.t("num_documento")?.let { num ->
  listOfNotNull(f.t("sociedad"), f.t("ejercicio"), num).joinToString("/")
}

private fun masDocs(f: JsonObject): String = (f["documentos_erp"].entero() ?: 0).let { if (it > 1) " (+${it - 1} doc.)" else "" }

private fun tipoCfdi(f: JsonObject) = if (f.t("direccion") == "EMITIDO") "Emitida" else "Recibida"

private fun banco(f: JsonObject): String? = f.t("banco")?.let { b -> b + (f.t("fecha_banco")?.let { " · ${fecha(it)}" } ?: "") }

val VISTAS: Map<String, VistaRegla> = mapOf(
  "C1" to VistaRegla(
    "Importe ERP", "Importe CFDI",
    { f -> docErp(f)?.let { "Doc. ERP $it" } ?: f.t("serie_folio") ?: "CFDI sin registro" },
    { f -> listOfNotNull(f.t("uuid")?.let { "UUID $it" }, f.t("rfc_emisor")?.let { "RFC emisor $it" }, f.t("fecha_emision")?.let { "Emitido ${fecha(it)}" }) },
  ),
  "C2" to VistaRegla(
    "Importe ERP", "Importe CFDI",
    { f -> f.t("serie_folio")?.let { "CFDI $it" } ?: docErp(f)?.let { "Doc. ERP $it" } ?: "Sin CFDI emitido" },
    { f -> listOfNotNull(f.t("uuid")?.let { "UUID $it" }, (f.t("rfc_tercero") ?: f.t("rfc_receptor"))?.let { "Cliente $it" }, docErp(f)?.let { "Doc. ERP $it" }) },
  ),
  "C3" to VistaRegla(
    "Pago en ERP", "Importe REP",
    { f -> if (f.t("uuid") == null) "Pago sin factura PPD" else "Factura PPD ${f.t("serie_folio").orEmpty()} · ${tipoCfdi(f)}".trim() },
    { f ->
      listOfNotNull(
        f.t("uuid")?.let { "UUID $it" },
        (if (f.t("direccion") == "EMITIDO") f.t("rfc_receptor") else f.t("rfc_emisor"))?.let { "RFC $it" },
        f.t("num_documento")?.let { "Doc. ERP $it" },
        f.t("uuid_rep")?.let { "REP $it" + (f.t("fecha_rep")?.let { d -> " · pago ${fecha(d)}" } ?: "") + ((f["reps"].entero() ?: 0).takeIf { r -> r > 1 }?.let { r -> " · $r REP" } ?: "") },
      )
    },
  ),
  "C4" to VistaRegla(
    "Importe SAP", "Importe banco",
    { f -> f.t("num_documento")?.let { "Pago SAP $it${masDocs(f)}" } ?: "Movimiento sin pago en SAP" },
    { f -> listOfNotNull(f.t("rfc_tercero")?.let { "RFC $it" }, banco(f)?.let { "Banco $it" }, f.t("referencia_banco")?.let { "Referencia $it" }) },
  ),
  "C5" to VistaRegla(
    "IVA", "IVA acreditable",
    { f ->
      if (f.t("uuid") == null && f.t("pedimento") != null) "Pedimento ${f.t("pedimento")} · Importación"
      else "CFDI" + (f.t("metodo_pago")?.let { " $it" } ?: "") + (f.t("serie_folio")?.let { " $it" } ?: "")
    },
    { f -> listOfNotNull(f.t("uuid")?.let { "UUID $it" }, f.t("rfc_emisor")?.let { "RFC emisor $it" }, f.t("num_documento")?.let { "Doc. ERP $it" }, banco(f)?.let { "Pago en banco $it" }) },
  ),
  "C6" to VistaRegla(
    "Contribuciones ERP", "Contribuciones pedimento",
    { f -> (f.t("pedimento") ?: f.t("pedimento_ref"))?.let { "Pedimento $it" } ?: "Póliza sin pedimento" },
    { f ->
      listOfNotNull(
        f.t("fecha_pago_pedimento")?.let { "Pago ${fecha(it)}" } ?: f.t("pedimento_ref")?.let { "No está en el Data Stage" },
        f.t("num_documento")?.let { "Póliza $it${masDocs(f)}" + (f.t("fecha_contab")?.let { d -> " · ${fecha(d)}" } ?: "") },
        if (f.n("iva_pedimento") != null || f.n("iva_erp_pedimento") != null) "IVA pedimento ${pesos(f.n("iva_pedimento"))} · ERP ${pesos(f.n("iva_erp_pedimento"))}" else null,
      )
    },
  ),
  "C7" to VistaRegla(
    "Contribuciones", "Pagado en banco",
    { f -> f.t("pedimento")?.let { "Pedimento $it" } ?: "Cargo aduanero sin pedimento" },
    { f -> listOfNotNull(f.t("fecha_pago_pedimento")?.let { "Pago ${fecha(it)}" }, banco(f)?.let { "Banco $it" }, f.t("referencia_banco")?.let { "Referencia $it" }) },
  ),
  "C8" to VistaRegla(
    "Importe ERP", "Importe pedimento",
    { f -> f.t("concepto")?.let { "Factura $it" } ?: "Factura de importación" },
    { f ->
      listOfNotNull(
        f.t("pedimento")?.let { "Pedimento $it" + (f.t("fecha_pago_pedimento")?.let { d -> " · pago ${fecha(d)}" } ?: "") } ?: "Sin pedimento",
        f.t("num_documento")?.let { "Doc. ERP $it" + (f.t("fecha_contab")?.let { d -> " · ${fecha(d)}" } ?: "") },
        f.t("rfc_tercero")?.let { "Proveedor $it" },
      )
    },
  ),
  "C9" to VistaRegla(
    "ERP / enterado", "Retenido CFDI",
    { f ->
      val concepto = f.t("concepto")
      if (concepto != null && concepto != "Factura") "Entero de $concepto" else f.t("serie_folio")?.let { "CFDI $it" } ?: f.t("uuid")?.let { "CFDI $it" } ?: "Sin CFDI"
    },
    { f ->
      val factura = f.t("concepto") == "Factura"
      listOfNotNull(
        (f.t("rfc_emisor") ?: f.t("rfc_tercero"))?.let { "RFC $it" },
        f.t("num_documento")?.let { "Doc. ERP $it" + (banco(f)?.let { b -> " · $b" } ?: "") },
        if (factura) "ISR CFDI ${pesos(f.n("ret_isr_cfdi"))} · ERP ${pesos(f.n("ret_isr_erp"))}" else null,
        if (factura) "IVA CFDI ${pesos(f.n("ret_iva_cfdi"))} · ERP ${pesos(f.n("ret_iva_erp"))}" else null,
      )
    },
  ),
  "C10" to VistaRegla(
    "Importe ERP", "Importe nota",
    { f -> if (f.t("uuid") == null) "Sin CFDI de egreso" else "Nota de crédito ${f.t("serie_folio").orEmpty()} · ${tipoCfdi(f)}" },
    { f ->
      listOfNotNull(
        f.t("uuid")?.let { "UUID $it" },
        (f.t("rfc_tercero") ?: if (f.t("direccion") == "EMITIDO") f.t("rfc_receptor") else f.t("rfc_emisor"))?.let { "RFC $it" },
        f.t("concepto")?.let { "Factura relacionada $it" },
        f.t("num_documento")?.let { "Doc. ERP $it" + (f.t("fecha_contab")?.let { d -> " · ${fecha(d)}" } ?: "") },
      )
    },
  ),
)

fun vista(regla: String): VistaRegla = VISTAS[regla] ?: VISTAS.getValue("C1")
