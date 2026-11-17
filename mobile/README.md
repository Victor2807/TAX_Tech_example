# TAX Tech · App móvil (Android)

Aplicación Android de **TAX Tech**, el proyecto de conciliación fiscal en México (CFDI ↔ ERP SAP ↔ estados de cuenta bancarios ↔ pedimentos del Data Stage del SAT). Lleva al celular las funciones del panel: resumen con alertas, tablero de IVA, conciliaciones C1 a C10, comparativo, explorador de tablas, cargas de archivos y control de accesos. La descripción general del proyecto, las conciliaciones y los 16 estatus están en el [README principal](../README.md).

> **Backend:** la app consume la **API REST local** de la nueva plataforma (backend NestJS de `app/` en el repositorio privado), no la Edge Function de Supabase. Esa API solo escucha en `127.0.0.1:3001` de la máquina de desarrollo.

## Pantallas

| Pantalla | Qué hace | Endpoints |
|---|---|---|
| **Inicio de sesión** | Correo y contraseña, con la dirección del servidor editable. Si un ADMIN creó la cuenta o restableció la contraseña, pide cambiarla antes de entrar. | `POST /auth/login`, `GET /auth/sesion`, `POST /auth/contrasena` |
| **Resumen** | Anillo de avance (última ejecución de cada regla), diferencias abiertas, alertas por severidad con su plazo, historial de resúmenes y «Guardar resumen ahora», fuentes cargadas, últimas conciliaciones (abren Conciliar) y últimas cargas. | `GET /resumen`, `GET /empresas/:id/alertas`, `GET /empresas/:id/resumenes-alertas`, `POST /resumenes-alertas` |
| **Tablero de IVA** | Saldo del mes (a favor o a cargo) con cifra animada, seis indicadores con variación, gráfica histórica, saldo por mes, IVA no acreditado por causa, importe en riesgo por conciliación, tabla por mes y **cierre mensual** (las 10 reglas en orden, con tolerancia de días de C4). | `GET /empresas/:id/tablero-iva`, `POST /cierres` |
| **Conciliar** | Regla C1 a C10, ejecución de una regla suelta (mes activo o periodo especial, con aviso de periodo no mensual), ejecuciones de la regla, resumen de la ejecución (cifras de C5, C7, C9 y C10), filtros por estatus y por revisión, revisión individual y masiva, quitar revisión, ocultar y volver a mostrar ejecuciones. | `GET /ejecuciones`, `POST /ejecuciones`, `GET /ejecuciones/:id/resultados`, `GET /ejecuciones/ocultas`, `PATCH /ejecuciones/:id/visibilidad`, `PATCH /resultados/:id/revision`, `POST /resultados/revision`, `POST /resultados/revision/quitar` |
| **Comparativo** | Hallazgos de cada mes al corte contra hoy (6, 12 o 24 meses), registro de cortes, matriz de abiertos por conciliación y seguimiento por estado. | `GET /empresas/:id/comparativo`, `POST /empresas/:id/cortes` |
| **Explorador** | Todas las tablas visibles para el perfil: filtros por igualdad, orden, paginación, detalle con ligas a tablas relacionadas, alta, edición y borrado según el perfil. | `GET /explorador`, `GET/POST /explorador/:tabla`, `PATCH/DELETE /explorador/:tabla/:id` |
| **Cargas** | Sube CFDI, SAP, estados de cuenta o Data Stage desde el celular (hasta 50 MB): primero **simula** y, sin errores, **confirma**. Banco, cuenta y saldo inicial con sugerencias de `cuenta_bancaria`. Historial de la empresa por fuente. | `POST /cargas/:fuente`, `GET /cargas` |
| **Accesos** | Para todos: el propio perfil y lo que permite cada rol. Para ADMIN: usuarios (alta con contraseña generada, perfil, activo, empresas, restablecer contraseña), empresas (alta con RFC y régimen del SAT, grupo), grupos y bitácora. El propio usuario no puede cambiarse el perfil ni desactivarse. | `GET /accesos`, `POST/PATCH /accesos/usuarios…`, `PUT /accesos/usuarios/:id/empresas`, `POST/PATCH /accesos/empresas…`, `POST /accesos/grupos`, `GET /explorador/bitacora` |

La empresa activa se elige en el encabezado (si el usuario tiene más de una) y el **mes activo** en el selector de periodo de Tablero, Conciliar y Comparativo, igual que en el panel. Para el perfil LECTOR los botones que modifican datos se muestran deshabilitados; la API aplica la misma regla.

### Diseño

- Fondo «aurora» animado con la paleta esmeralda y oro del panel, tarjetas translúcidas con borde de luz y un **dock flotante** con indicador que se desliza entre pestañas (Explorador, Cargas, Accesos y la cuenta están en «Más»).
- Cifras que se animan al cambiar, anillo de avance con degradado y gráficas propias en Canvas (sin bibliotecas), con lectura para TalkBack.
- Íconos de trazo propios (`ui/comun/Iconos.kt`), sin dependencia de una biblioteca de íconos.
- Modo claro y oscuro según el sistema; los colores de las gráficas son los mismos del tablero web.

## Conectar con la API local

1. En el repositorio privado, arranca la API (`cd app && npm run dev`, ver `app/CLAUDE.md`). Escucha en `http://127.0.0.1:3001/api`.
2. Con el emulador o el celular conectado por USB, redirige el puerto:

   ```bash
   adb reverse tcp:3001 tcp:3001
   ```

3. En la app, el servidor por omisión es `http://127.0.0.1:3001`. Se cambia en la pantalla de inicio («Cambiar»).

- `adb reverse` se pierde al reiniciar el emulador o desconectar el celular: vuelve a ejecutarlo.
- La app solo permite HTTP sin cifrar hacia `127.0.0.1`, `localhost` y `10.0.2.2` (`res/xml/network_security_config.xml`); cualquier otro servidor debe usar HTTPS.
- En el emulador con API 37, `10.0.2.2` no respondió desde la app (sí desde `adb shell`); por eso se usa `adb reverse`.
- La API limita los intentos de inicio de sesión (5 fallos por correo en 15 minutos).

## Tecnologías

| Componente | Versión |
|---|---|
| Kotlin | 2.4.21 |
| Android Gradle Plugin | 9.5.1 |
| Gradle (wrapper) | 9.9.1 |
| Jetpack Compose (BOM) | 2026.09.00 · Material 3 |
| Navigation 3 | 1.2.1 |
| Lifecycle / ViewModel | 2.11.0 |
| kotlinx.serialization (JSON) | 1.9.0 |
| OkHttp (HTTP, multipart) | 4.12.0 · MockWebServer en pruebas |
| Coroutines (pruebas) | 1.11.0 |
| JDK para compilar la app | 17 (`jvmToolchain(17)`) |
| JDK del daemon de Gradle | 25, Adoptium (`gradle/gradle-daemon-jvm.properties`) |

- `minSdk` 24 · `targetSdk` y `compileSdk` 37 · paquete `com.taxtech.mobile`.
- Las versiones se centralizan en [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
- OkHttp en lugar de `HttpURLConnection` porque la API usa PATCH y las cargas van en multipart.

## Compilar, ejecutar y probar

Desde Android Studio, abre la carpeta `mobile/` y ejecuta la configuración `app`. Desde la terminal, dentro de `mobile/` (en Windows, `gradlew.bat`):

```bash
./gradlew installDebug           # instala en el emulador o celular conectado
./gradlew testDebugUnitTest      # pruebas locales
./gradlew connectedAndroidTest   # pruebas de interfaz (requiere emulador o celular)
./gradlew lintDebug
```

Requisitos: Android Studio con soporte para AGP 9, Android SDK con la plataforma 37 (`sdk.dir` en `local.properties`, que no se sube al repositorio) y JDK 17 y 25 (Gradle los descarga si faltan).

Pruebas locales (`app/src/test`):
- [`ClienteApiTest`](app/src/test/kotlin/com/taxtech/mobile/data/ClienteApiTest.kt): contra un servidor simulado (MockWebServer) revisa el prefijo `/api`, el token, los filtros como parámetros, que se omitan los valores vacíos, el mensaje de error de Nest, que un 401 cierre la sesión (pero no en el inicio de sesión), el 403 `CAMBIAR_CONTRASENA` y el flujo de inicio de sesión.
- [`ModelosTest`](app/src/test/kotlin/com/taxtech/mobile/data/ModelosTest.kt): lectura del JSON con la forma de `compartido/src/tipos.ts` (importes como texto).
- [`FormatoTest`](app/src/test/kotlin/com/taxtech/mobile/ui/comun/FormatoTest.kt): pesos, meses, fechas con zona, aviso de periodo, ejes de las gráficas, contraseñas y conversión de valores del explorador.

Pruebas de interfaz (`app/src/androidTest`): [`ComponentesTest`](app/src/androidTest/kotlin/com/taxtech/mobile/ui/ComponentesTest.kt) revisa el chip de estatus y el selector de mes.

## Estructura

```
mobile/app/src/main/kotlin/com/taxtech/mobile/
├── MainActivity.kt          # tema, flujo de sesión y ViewModels del panel (se descartan al salir)
├── Contenedor.kt            # dependencias: cliente, sesión, repositorio y avisos
├── data/
│   ├── Http.kt              # ConfigApi, ErrorApi y transporte OkHttp (JSON y multipart)
│   ├── Sesion.kt            # almacén del token, ClienteApi (401 / 403) y GestorSesion
│   ├── Modelos.kt           # modelos de respuesta y su lectura desde JSON
│   ├── Jsons.kt             # lectura tolerante (texto, número, booleano)
│   └── Repositorio.kt       # una función por endpoint
└── ui/
    ├── comun/               # componentes, gráficas, íconos, formato, dominio (16 estatus) y VmBase
    ├── theme/               # paleta esmeralda y oro, claro y oscuro
    ├── login/  panel/       # inicio de sesión, cambio de contraseña, encabezado, dock y navegación
    └── resumen/  tablero/  conciliar/  comparativo/  explorador/  cargas/  accesos/
```

- **Datos:** todo pasa por `Repositorio` → `ClienteApi`. Un 401 cierra la sesión y un 403 con código `CAMBIAR_CONTRASENA` lleva a cambiar la contraseña; los demás errores se muestran como aviso con el mensaje de la API.
- **Estado:** cada pantalla tiene su ViewModel; `PanelViewModel` guarda la empresa y el mes activos. Los ViewModels del panel viven mientras dura la sesión y se descartan al salir.
- **Sesión:** el JWT se guarda en preferencias privadas excluidas del respaldo. La API no tiene renovación de token: al vencer (8 horas por omisión) se vuelve a iniciar sesión.

## Fuera del alcance (se hacen en el panel web)

- Exportaciones a Excel y PDF y los papeles de trabajo de C5, C9 y C10.
- Importar revisiones desde la hoja **Hallazgos** del Excel.

## Convenciones

- Todo el texto (interfaz, comentarios, commits y documentación) va en español neutro, y los identificadores nuevos también. La app solo está en español: los textos viven en las pantallas; si se agrega otro idioma habrá que moverlos a `strings.xml`.
- Todo cambio debe verse bien en modo claro y oscuro y a partir de 360 dp de ancho.
- Si una pantalla necesita datos nuevos, también necesita su endpoint en la API local (repositorio privado): indícalo en lugar de suponer que ya existe.
- No agregues aquí código del modelo de datos o del backend, ni datos fiscales o bancarios reales: los de demostración son ficticios. No guardes credenciales en el código.
