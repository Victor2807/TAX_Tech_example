# TAX Tech – Conciliación Fiscal México

Plataforma para automatizar la **conciliación de impuestos en México** (IVA, ISR e IVA retenidos, notas de crédito y contribuciones de comercio exterior) y dejar lista la **determinación mensual de IVA en flujo de efectivo**, cruzando cuatro fuentes:

1. **CFDI** emitidos y recibidos (XML del SAT): ingreso, egreso (notas de crédito) y complementos de pago
2. **ERP** (SAP ECC / S/4HANA): registros contables, cuentas por pagar y por cobrar
3. **Estados de cuenta bancarios** (MT940, CAMT.053, CSV)
4. **Importaciones**: pedimentos del **Data Stage** del SAT

El objetivo es encontrar diferencias antes de presentar declaraciones, sustentar el IVA acreditable y trasladado, dar seguimiento a los hallazgos mes a mes y dejar evidencia de auditoría para revisiones del SAT o el dictamen fiscal.

> Este repositorio contiene la página pública (`index.html`) con la presentación del proyecto y el panel de trabajo. Los scripts de carga, la API, las migraciones y el modelo de datos están en un repositorio privado.

---

## Qué resuelve

- Que **todo gasto o ingreso registrado en el ERP** tenga un CFDI vigente y que todo CFDI esté registrado.
- El **IVA efectivamente pagado y cobrado** (art. 5 LIVA), cruzando CFDI PUE/PPD, complementos de pago (REP) y bancos.
- El efecto de las **notas de crédito** en el IVA del mes (art. 7 LIVA): las recibidas disminuyen el acreditable y las emitidas el trasladado.
- El **IVA y las contribuciones de importación** de los pedimentos contra la contabilidad y el banco.
- Las **retenciones de ISR e IVA** contra su entero del mes siguiente.
- Riesgos fiscales: CFDI cancelados, proveedores en **69-B**, diferencias de tipo de cambio, pagos sin REP o sin banco.
- Insumos para la **declaración mensual de IVA**, la **DIOT** y las constancias de retenciones.

---

## Conciliaciones

| # | Conciliación | Fuentes | Llave de cruce |
|---|---|---|---|
| C1 | CFDI recibidos vs cuentas por pagar | CFDI ↔ ERP | UUID; si falta: RFC + fecha + importe |
| C2 | CFDI emitidos vs ingresos | CFDI ↔ ERP | UUID; serie/folio; RFC + importe |
| C3 | Pagos vs complementos de pago (REP) | ERP ↔ CFDI tipo P | UUID del documento relacionado + importe pagado |
| C4 | Pagos del ERP vs banco | ERP ↔ Estado de cuenta | Importe + fecha (tolerancia de 10 días) + referencia |
| C5 | IVA acreditable en flujo de efectivo | CFDI ↔ ERP ↔ Banco ↔ Pedimentos | Resultado de C1, C3, C4, C7 y C10 |
| C6 | Pedimentos vs contabilidad | Data Stage ↔ ERP | Patente-aduana-número |
| C7 | Pago de pedimentos vs banco | Data Stage (`510`) ↔ Estado de cuenta | Contribuciones + fecha de pago |
| C8 | Facturas de importación vs pedimento | Data Stage (`505`) ↔ ERP | Factura del proveedor extranjero |
| C9 | Retenciones de ISR e IVA vs entero | CFDI ↔ ERP ↔ Estado de cuenta | UUID + cuentas de retención; entero al día 17 |
| C10 | Notas de crédito (CFDI de egreso) vs factura y ERP | CFDI ↔ ERP | CFDI relacionado 01/03/07; UUID o RFC + importe (KG / DG) |

**Reglas de cruce:** tolerancias de importe y de días configurables por regla; moneda extranjera comparada en moneda original y en pesos; cruce por niveles (UUID exacto → RFC + importe + fecha → referencia → revisión manual).

**Estatus de resultado (16):** `CONCILIADO` · `DIFERENCIA_IMPORTE` · `DIFERENCIA_TC` · `SOLO_ERP` · `SOLO_CFDI` · `SOLO_BANCO` · `SOLO_PEDIMENTO` · `CFDI_CANCELADO` · `PROVEEDOR_69B` · `PPD_SIN_REP` · `PAGO_SIN_BANCO` · `SIN_PAGO` · `NOTA_SIN_RELACION` · `RELACION_INVALIDA` · `EXCEDE_SALDO` · `IVA_NO_PROPORCIONAL`

---

## Panel de trabajo

La página incluye un panel conectado a **Supabase**. Se entra con Supabase Auth y solo pueden hacerlo los correos dados de alta, con rol **Lector**, **Editor** o **Admin**.

| Pestaña | Para qué sirve |
|---|---|
| **Resumen** | Alertas de vencimientos (IVA, DIOT, entero de retenciones, REP) y hallazgos de prioridad alta, con resúmenes guardados automáticamente cada lunes y cada día 14; fuentes cargadas y últimas conciliaciones. |
| **Tablero de IVA** | Determinación mensual: IVA a favor o a cargo, trasladado, cobrado, pendiente de cobro, acreditable, pagado y pendiente de pago, con variación contra el mes anterior. Gráficos de histórico, saldo por mes, IVA no acreditado por causa e importe en riesgo por conciliación. Botón para **ejecutar todas las conciliaciones del mes** en el orden correcto (C1, C2, C3, C4, C6, C7, C8, C10, C5, C9). |
| **Conciliar** | Ejecuta cada regla, revisa resultados con filtros por estatus y por revisión, marca o quita revisiones (una a una o en masa), oculta ejecuciones equivocadas y avisa cuando el periodo no es un mes calendario. |
| **Comparativo** | Hallazgos de cada mes al corte contra hoy: cerrados, abiertos, nuevos y que cambiaron de estatus, por conciliación. |
| **Explorador de tablas** | Consulta, filtra y corrige los registros cargados, con navegación entre registros relacionados. |

### Exportaciones

| Desde | Excel | PDF |
|---|---|---|
| Tablero de IVA | Determinación con fórmulas, histórico, IVA no acreditado y riesgo por conciliación | Tablero completo en tamaño carta |
| Conciliar | Tabla de resultados con los filtros aplicados; papeles de trabajo de **IVA** (C5), **retenciones** (C9) y **notas de crédito** (C10) | — |
| Resumen | Alertas e historial de resúmenes | — |
| Comparativo | Evolución mensual, por conciliación y seguimiento | — |

Los papeles de trabajo incluyen una hoja **Hallazgos** con prioridad, importe en riesgo y acción sugerida. Se puede marcar **Sí** en la columna *Marcar revisada* y subir el archivo de vuelta al panel para registrar la revisión de todas esas partidas, con vista previa antes de confirmar.

### Control y trazabilidad

- Cada cambio queda en una **bitácora** (quién, cuándo, antes y después), incluidas las revisiones, las importaciones desde Excel y los cierres.
- Las **revisiones se conservan** entre ejecuciones cuando la partida no cambió.
- El **corte** de cada mes se registra al generar su papel de trabajo, para el comparativo.
- Las horas se muestran en la hora local de quien consulta.

---

## Arquitectura

```
 ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌────────────┐
 │  CFDI XML │  │  ERP SAP  │  │  Bancos   │  │ Data Stage │
 └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬──────┘
       └──────────────┴──── scripts de carga ───────┘
                              ▼
            PostgreSQL (Supabase) · esquema privado
              Motor de conciliación C1–C10 en SQL
                              ▼
          API privada (Supabase Edge Function, Deno)
        sesión + rol en cada petición · bitácora de cambios
                              ▼
     index.html · Resumen · Tablero · Conciliar · Comparativo
          Papeles de trabajo (Excel) · Tablero (PDF)
```

- **Base de datos:** PostgreSQL en Supabase. El esquema **no se publica** en la API pública de Supabase; tiene RLS activo y solo lo usa la función privada.
- **API:** Edge Function que valida la sesión y el rol en cada petición. Las reglas, los papeles de trabajo, las alertas y el tablero se calculan en funciones SQL.
- **Panel:** una sola página HTML (Tailwind, SheetJS para Excel y gráficos SVG propios), en modo claro y oscuro y adaptada a celular.
- **Tareas programadas:** `pg_cron` guarda el resumen de alertas cada lunes y cada día 14.

---

## Repositorios

| Repositorio | Contenido |
|---|---|
| `TAX_Tech_example` (este, público) | `index.html`: presentación del proyecto y panel de trabajo |
| Privado | Migraciones SQL, modelo de datos, Edge Function, scripts de carga y generadores de datos de ejemplo |

---

## Seguridad y confidencialidad

- La información fiscal y bancaria es **confidencial**: los datos reales y el modelo de datos no se versionan en este repositorio.
- Las credenciales (e.firma, CIEC, accesos al ERP, al banco y a la base) se manejan fuera del repositorio.
- Para pruebas y demostraciones se usan solo **datos ficticios** (octubre y noviembre de 2026 de una empresa de ejemplo).

---

## Marco normativo de referencia

- Ley del IVA: acreditamiento en flujo de efectivo (art. 5) y descuentos, devoluciones y bonificaciones (art. 7)
- Código Fiscal de la Federación (arts. 29, 29-A, 69, 69-B)
- Resolución Miscelánea Fiscal vigente y Anexo 20 (CFDI 4.0 y complemento de pagos 2.0)
- Ley Aduanera y Anexo 22 (pedimentos)

> **Nota:** este proyecto es un ejemplo con fines demostrativos y no sustituye la asesoría fiscal profesional. Revisa las cifras antes de presentar cualquier declaración.
