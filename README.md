# TAX Tech – Conciliación Fiscal México

Proyecto para automatizar la **conciliación de impuestos en México** (IVA, ISR retenido y contribuciones de comercio exterior) cruzando cuatro fuentes:

1. **CFDI** emitidos y recibidos (XML del SAT)
2. **ERP** (p. ej. SAP ECC / S/4HANA): registros contables y de cuentas por pagar y por cobrar
3. **Estados de cuenta bancarios**
4. **Importaciones**: pedimentos obtenidos del **Data Stage** del SAT

El objetivo es encontrar diferencias antes de presentar declaraciones, sustentar el IVA acreditable y trasladado, y dejar evidencia de auditoría lista para revisiones del SAT o para el dictamen fiscal.

---

## Objetivos

- Validar que **todo gasto o ingreso registrado en el ERP** tenga un CFDI vigente que lo respalde, y que todo CFDI recibido o emitido esté registrado.
- Calcular el **IVA efectivamente pagado o cobrado** (flujo de efectivo) cruzando CFDI PUE/PPD, complementos de pago (REP) y movimientos bancarios.
- Conciliar el **IVA y las contribuciones de importación** de los pedimentos contra la contabilidad y los pagos bancarios.
- Detectar riesgos fiscales: CFDI cancelados, proveedores en listas **69 y 69-B**, diferencias de tipo de cambio y pagos sin REP.
- Generar insumos para la **DIOT**, la declaración mensual de IVA y la contabilidad electrónica.

---

## Fuentes de datos

### 1. CFDI (SAT)

| Elemento | Detalle |
|---|---|
| Origen | Descarga masiva del SAT (web service), portal o PAC |
| Formato | XML CFDI 4.0 (y 3.3 para periodos históricos) + metadata |
| Tipos | Ingreso (I), Egreso (E), Pago (P), Nómina (N), Traslado (T) |
| Complementos | Recepción de Pagos 2.0, Comercio Exterior, Nómina 1.2 |
| Campos clave | `UUID`, RFC emisor/receptor, fecha, `MetodoPago` (PUE/PPD), `FormaPago`, moneda, `TipoCambio`, subtotal, impuestos trasladados/retenidos, `UsoCFDI`, CFDI relacionados |
| Validación | Estatus en el SAT (vigente/cancelado) y lista 69-B del emisor |

### 2. ERP (SAP)

| Elemento | Detalle |
|---|---|
| Origen | Extracción de SAP (ECC: `BKPF`/`BSEG`, `BSIK`/`BSAK`, `BSID`/`BSAD`; S/4HANA: `ACDOCA`) |
| Formato | CSV / Excel / extracción vía RFC u OData |
| Campos clave | Sociedad, ejercicio, documento contable, fecha de contabilización, proveedor/cliente, RFC, referencia, **UUID** (según la localización México que se use), importe, moneda, tipo de cambio, indicador de IVA, cuenta de mayor, documento de compensación |
| Uso | Base del registro contable de ingresos, gastos, IVA y pagos |

### 3. Estados de cuenta bancarios

| Elemento | Detalle |
|---|---|
| Origen | Banca electrónica / host-to-host |
| Formato | MT940, CAMT.053, CSV o Excel según el banco |
| Campos clave | Cuenta, fecha valor, importe, cargo/abono, referencia, concepto, beneficiario/ordenante, clave de rastreo SPEI |
| Uso | Comprobar el pago efectivo (IVA en flujo de efectivo) y el pago de contribuciones de comercio exterior |

### 4. Importaciones – Data Stage (pedimentos)

| Elemento | Detalle |
|---|---|
| Origen | Data Stage del SAT (información de pedimentos de la empresa) |
| Formato | Archivos de texto delimitados por `\|`, un archivo por tipo de registro |
| Registros principales | `501` datos generales · `505` facturas · `506` fechas · `510` contribuciones del pedimento · `551` partidas · `557` contribuciones por partida · `701` rectificaciones |
| Campos clave | Patente, número de pedimento, aduana, clave de pedimento, fecha de pago, tipo de cambio, valor aduana, IVA, IGI, DTA, PRV, forma de pago |
| Uso | Sustentar el IVA acreditable de importación y conciliar contra la contabilidad y el banco |

---

## Conciliaciones

| # | Conciliación | Fuentes | Llave de cruce |
|---|---|---|---|
| C1 | CFDI recibidos vs cuentas por pagar | CFDI ↔ ERP | UUID; si falta: RFC + fecha + importe |
| C2 | CFDI emitidos vs ingresos | CFDI ↔ ERP | UUID; serie/folio |
| C3 | Pagos vs REP | ERP (compensaciones) ↔ CFDI tipo P | UUID del documento relacionado + importe pagado |
| C4 | Pagos ERP vs banco | ERP ↔ Estado de cuenta | Importe + fecha (con tolerancia) + referencia |
| C5 | IVA acreditable en flujo de efectivo | CFDI PUE/REP ↔ Banco ↔ ERP | Resultado de C1 + C3 + C4 |
| C6 | Pedimentos vs contabilidad | Data Stage ↔ ERP | Número de pedimento (patente-aduana-número) |
| C7 | Pago de pedimentos vs banco | Data Stage (`510`) ↔ Estado de cuenta | Importe de contribuciones + fecha de pago |
| C8 | Facturas de importación vs pedimento | Data Stage (`505`) ↔ ERP | Número de factura del proveedor extranjero |
| C9 | Retenciones de ISR e IVA vs entero | CFDI ↔ ERP ↔ Estado de cuenta | UUID + cuentas de retención; entero del mes siguiente (día 17) |

### Reglas de cruce

- **Tolerancia de importe**: configurable (por ejemplo, ±1.00 MXN por redondeo).
- **Tolerancia de fecha**: configurable (por ejemplo, ±3 días hábiles entre el pago en el ERP y el movimiento bancario).
- **Moneda extranjera**: se compara en moneda original y en MXN, con el tipo de cambio del CFDI, del ERP y del DOF.
- **Cruce por niveles**: 1) exacto por UUID → 2) RFC + importe + fecha → 3) aproximado (*fuzzy*) sobre referencia o concepto → 4) revisión manual.

### Estatus de resultado

`CONCILIADO` · `DIFERENCIA_IMPORTE` · `DIFERENCIA_TC` · `SOLO_ERP` · `SOLO_CFDI` · `SOLO_BANCO` · `SOLO_PEDIMENTO` · `CFDI_CANCELADO` · `PROVEEDOR_69B` · `PPD_SIN_REP` · `PAGO_SIN_BANCO` · `SIN_PAGO`

---

## Modelo de datos

Las cuatro fuentes y los resultados de conciliación se guardan en un modelo entidad-relación de 24 tablas (PostgreSQL), organizado en seis dominios: catálogos comunes, CFDI, ERP, bancos, Data Stage y conciliación.

- El DDL (`db/schema.sql`), su documentación y el diagrama (`docs/`) se mantienen fuera del repositorio por seguridad (ver `.gitignore`).

---

## Panel de datos (Supabase)

La página `index.html` incluye un panel conectado a Supabase para consultar las fuentes cargadas, corregir registros y ejecutar las nueve conciliaciones (C1 a C9).

- **Acceso:** inicio de sesión con Supabase Auth. Solo entran los correos dados de alta por un administrador, con rol Lector, Editor o Admin.
- **Seguridad:** la base no está publicada en la API pública de Supabase. La página habla con una función privada (Edge Function) que valida la sesión y el rol en cada petición, y registra cada cambio en una bitácora.
- **Funciones:** resumen de cargas y conciliaciones, explorador de tablas con búsqueda, filtros, orden y navegación entre registros relacionados, editor de registros, papel de trabajo mensual de IVA exportable a Excel, ejecución de C1 (CFDI contra cuentas por pagar), C2 (CFDI emitidos contra ingresos), C3 (pagos contra complementos de pago REP), C4 (pagos SAP contra banco), C5 (IVA acreditable en flujo de efectivo, nacional y de importación), C6 (pedimentos contra contabilidad), C7 (pago de pedimentos contra banco), C8 (facturas de importación contra pedimento) y C9 (retenciones de ISR e IVA contra su entero) con revisión de resultados y exportación a CSV.

Los scripts de carga (CFDI, SAP, estados de cuenta y Data Stage), la función y las migraciones se mantienen en un repositorio privado.

---

## Flujo del proceso

```
 ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌────────────┐
 │  CFDI XML │  │  ERP SAP  │  │  Bancos   │  │ Data Stage │
 └─────┬─────┘  └─────┬─────┘  └─────┬─────┘  └─────┬──────┘
       └──────────────┴───────┬──────┴──────────────┘
                              ▼
                  1. Ingesta y validación
                  2. Normalización (RFC, fechas, moneda, TC)
                  3. Modelo de datos común
                  4. Motor de conciliación (C1–C9)
                  5. Cálculo de IVA / retenciones
                              ▼
          Reportes de diferencias · Papeles de trabajo · DIOT
```

---

## Entregables

- Papel de trabajo de **IVA mensual** (trasladado, acreditable, retenido, importación).
- Reporte de **diferencias por conciliación**, con detalle de cada partida.
- Base para la **DIOT** por proveedor y tipo de operación.
- Alertas de cumplimiento: CFDI cancelados, proveedores 69-B y PPD sin complemento de pago.
- Tablero de indicadores: % conciliado, importe en diferencias y antigüedad de partidas abiertas.

---

## Estructura propuesta del repositorio

```
TAX_Tech_example/
├── data/
│   ├── raw/            # Insumos originales (no se versionan)
│   │   ├── cfdi/
│   │   ├── erp/
│   │   ├── bancos/
│   │   └── datastage/
│   └── processed/      # Datos normalizados
├── src/
│   ├── ingestion/      # Lectores: XML CFDI, SAP, MT940/CAMT, Data Stage
│   ├── normalization/  # Limpieza y modelo de datos común
│   ├── reconciliation/ # Reglas C1–C9
│   └── reporting/      # Papeles de trabajo y reportes
├── config/             # Tolerancias, mapeos de cuentas e indicadores de IVA
├── db/
│   └── schema.sql      # Modelo entidad-relación (local, no se versiona)
├── docs/
│   ├── modelo-er.md    # Diagrama ER y explicación (local, no se versiona)
│   └── modelo-er.png   # Imagen del diagrama (local, no se versiona)
├── tests/
└── README.md
```

---

## Seguridad y confidencialidad

- La información fiscal y bancaria es **confidencial**: los datos reales **no se versionan** (`data/raw/` va en `.gitignore`).
- Las credenciales (e.firma, CIEC, accesos al ERP y al banco) se manejan fuera del repositorio, en un gestor de secretos.
- Para pruebas y demos se usan solo **datos ficticios o anonimizados**.

---

## Marco normativo de referencia

- Ley del IVA (acreditamiento con base en flujo de efectivo, art. 5)
- Código Fiscal de la Federación (arts. 29, 29-A, 69, 69-B)
- Resolución Miscelánea Fiscal vigente y Anexo 20 (CFDI)
- Ley Aduanera y Anexo 22 (claves de pedimento)

> **Nota:** este proyecto es un ejemplo con fines demostrativos y no sustituye la asesoría fiscal profesional.
