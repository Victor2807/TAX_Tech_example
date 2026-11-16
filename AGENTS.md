# AGENTS.md

This file provides guidance to Codex (claude.ai/code) when working with code in this repository.

## Idioma

Trabaja siempre en **español neutro**: respuestas, explicaciones, preguntas, comentarios en el código, mensajes de commit, descripciones de PR, textos de la interfaz y documentación.

- Usa un español comprensible en cualquier país hispanohablante. Evita regionalismos, modismos y voseo, y trata al usuario de **tú**.
- Conserva tal cual los términos técnicos y fiscales de México, que son propios del dominio: CFDI, UUID, RFC, REP, PUE/PPD, DIOT, pedimento, Data Stage, 69-B, LIVA, SAT, e.firma, CIEC, entero, papel de trabajo.
- Los identificadores de código ya están en español (`cargarTablero`, `manejarError`, `st.usuario`). Los nuevos deben seguir la misma convención.

## Qué es este repositorio

Es la parte pública de **TAX Tech**, un proyecto de conciliación fiscal en México (CFDI ↔ ERP SAP ↔ estados de cuenta bancarios ↔ pedimentos del Data Stage del SAT, conciliaciones C1 a C10 e IVA mensual en flujo de efectivo). Solo contiene `_index.html` (presentación del proyecto y panel de trabajo con sesión) y `README.md`.

El esquema SQL, el motor de conciliación (funciones SQL), la Edge Function de Supabase, las migraciones y los scripts de carga están en un **repositorio privado aparte**. Por eso `.gitignore` excluye a propósito `db/`, `scripts/`, `supabase/` y `docs/modelo-er.*`. Nunca agregues aquí código del modelo de datos o del backend, ni datos fiscales o bancarios reales. Los datos de demostración son ficticios (octubre y noviembre de 2026).

`README.md` funciona como especificación: tabla de conciliaciones (fuentes y llaves de cruce), los 16 estatus de resultado, las pestañas del panel y las exportaciones. Léelo antes de cambiar la interfaz relacionada con las conciliaciones.

## Desarrollo

No hay compilación, gestor de paquetes, linter ni pruebas. `_index.html` es una sola página autocontenida que carga sus dependencias desde CDN:
- Tailwind Play CDN (`cdn.tailwindcss.com/3.4.16`), configurado en línea (`darkMode: 'class'`, paleta `brand` esmeralda y `gold`).
- three.js r128 (animación de la portada).
- `@supabase/supabase-js@2.45.4` (autenticación).
- SheetJS `xlsx 0.18.5`, que `cargarXLSX()` carga solo al exportar.

Para ejecutarla, abre el archivo en el navegador o sirve la carpeta (por ejemplo, `python -m http.server`). El panel se conecta al proyecto de Supabase en producción, así que para probarlo se necesita una cuenta dada de alta con un rol.

## Estructura de `_index.html`

El archivo tiene unas 3,500 líneas, en este orden:
1. **Encabezado**: un script temprano de tema (`localStorage['taxtech-theme']`, que evita el parpadeo de tema incorrecto), la configuración de Tailwind y un bloque `<style>` con las reglas `@media print` que usa la exportación a PDF del tablero de IVA.
2. **Secciones de la presentación** (`#inicio`, `#objetivos`, `#fuentes`, `#conciliaciones`, `#simulador`, `#flujo`, `#entregables`) y el marcado del panel dentro de `#datos`. Cada pestaña es un `div#tab-<nombre>.db-panel`: `resumen`, `tablero`, `conciliar`, `comparativo` y `explorador`.
3. **Script de la presentación** (cerca de la línea 800): cambio de tema, three.js y efectos. Expone `window.TAXTECH = { chip, STATUS, toast }`, que el panel reutiliza. `STATUS` contiene las etiquetas y colores de los estatus de resultado.
4. **Script del panel** (cerca de la línea 1470): una IIFE con toda la lógica del panel.

Puntos clave del script del panel:
- **Todos los datos pasan por una sola función**: `api(accion, cuerpo)` hace un POST a `CFG.url + '/functions/v1/conciliacion-api'` con `{ accion, ...cuerpo }` y el JWT de Supabase del usuario, y devuelve `j.datos`. La página nunca consulta tablas directamente. La clave anon de `CFG` es pública por diseño. La Edge Function privada valida la sesión y el rol (`LECTOR` / `EDITOR` / `ADMIN`) y escribe la bitácora. Acciones en uso: `sesion`, `meta`, `resumen`, `listar`, `crear`, `actualizar`, `eliminar`, `ejecutar`, `ejecutar_cierre`, `revisar`, `revisar_varios`, `quitar_revision_varios`, `importar_revision`, `ocultar_ejecucion`, `ejecuciones_ocultas`, `papel_iva`, `papel_notas_credito`, `papel_retenciones`, `tablero_iva`, `alertas`, `resumenes_alertas`, `generar_resumen_alertas`, `comparativo` y `registrar_corte`. Si una función nueva necesita datos, también necesita su acción en la Edge Function privada: indícalo en lugar de suponer que ya existe.
- Los errores pasan por `manejarError`: un 401 cierra la sesión y cualquier otro error muestra un `toast`.
- El estado vive en el objeto `st` (y en `tb` para el tablero). `salir()` lo reinicia todo, así que **cualquier estado nuevo también debe limpiarse en `salir()`**.
- `tab(nombre)` cambia de pestaña y carga cada una al abrirla (`prepararConciliar`, `cargarComparativo`, `cargarTablero`, o `elegirTabla` para el explorador).
- El rol `LECTOR` es de solo lectura. Los botones que modifican datos se deshabilitan para ese rol en la interfaz, y el servidor aplica la misma regla.
- Los gráficos son SVG hechos a mano (`grafHistorico`, `grafSaldo`, `barrasOrdenadas`, con los auxiliares compartidos `tip` y `ticks` y navegación con teclado mediante `navegarTeclado`). No se usa ninguna biblioteca de gráficos.
- Las exportaciones a Excel se arman con `hojaTabla()` (título, encabezados, columnas de dinero y fila de totales con fórmulas `SUM()`). Los papeles de trabajo (`generarPapel`, `generarPapelNotas`, `generarPapelRetenciones`) incluyen una hoja **Hallazgos**. Cuando esa hoja se vuelve a subir, `leerHallazgos` la lee e `importar_revision` registra las revisiones en masa. Mantén la estructura de columnas de esa hoja compatible con `leerHallazgos`.
- La exportación a PDF usa `window.print()` con `prepararPDF()` / `restaurarPDF()` y el CSS de impresión.
- Las fechas con zona horaria se muestran en la hora local del navegador mediante `fechaHora`. Las fechas sin zona y las horas de los CFDI se muestran como vienen.
- Escapa todo el HTML interpolado con `esc()`. Envuelve el acceso a `localStorage` en try/catch: la autenticación de Supabase usa el respaldo en memoria `almacen`.

## Convenciones

- Los asuntos de commit son breves y en español neutro. Los cambios del panel llevan el prefijo `Panel: …` (por ejemplo, `Panel: exportar el tablero de IVA a Excel y a PDF`).
- Todo cambio debe funcionar en modo claro y oscuro (clases `dark:`) y en ancho de celular.
- Actualiza `README.md` cuando cambie una pestaña, una exportación o una conciliación: ahí se registra el estado actual del proyecto.
- El orden de la ejecución mensual completa importa: C1, C2, C3, C4, C6, C7, C8, C10, C5, C9 (C5 depende de C1, C3, C4, C7 y C10).
