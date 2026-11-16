# TAX Tech · App móvil (Android)

Aplicación Android de **TAX Tech**, el proyecto de conciliación fiscal en México (CFDI ↔ ERP SAP ↔ estados de cuenta bancarios ↔ pedimentos del Data Stage del SAT). Es el complemento móvil del panel web (`../_index.html`); la descripción general del proyecto, las conciliaciones C1 a C10 y los estatus de resultado están en el [README principal](../README.md).

> **Estado actual:** el proyecto está recién creado a partir de la plantilla de Android Studio (Compose + Navigation 3). Todavía no se conecta a Supabase ni muestra datos de conciliación: la pantalla principal es un ejemplo con datos fijos.

## Tecnologías

| Componente | Versión |
|---|---|
| Kotlin | 2.3.20 |
| Android Gradle Plugin | 9.0.1 |
| Gradle (wrapper) | 9.1.0 |
| Jetpack Compose (BOM) | 2026.03.01 |
| Material 3 | según el BOM |
| Navigation 3 | 1.0.1 |
| Lifecycle / ViewModel | 2.10.0 |
| kotlinx.serialization | plugin de Kotlin 2.3.20 |
| JDK | 17 (toolchain) |

- `minSdk` 24 · `targetSdk` y `compileSdk` 37.
- Paquete y `applicationId`: `com.taxtech.mobile`.
- Las versiones se centralizan en el catálogo [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Requisitos

- Android Studio reciente con soporte para AGP 9.
- JDK 17 (Gradle lo descarga con el *foojay resolver* si no está instalado).
- Android SDK con la plataforma 37. La ruta se define en `local.properties` (`sdk.dir=...`), que Android Studio genera y que **no** se sube al repositorio.

## Compilar y ejecutar

Desde Android Studio, abre la carpeta `mobile/` y ejecuta la configuración `app`.

Desde la terminal, dentro de `mobile/`:

```bash
# Windows
gradlew.bat assembleDebug        # genera app/build/outputs/apk/debug/app-debug.apk
gradlew.bat installDebug         # instala en el dispositivo o emulador conectado

# macOS / Linux
./gradlew assembleDebug
./gradlew installDebug
```

## Pruebas

```bash
./gradlew test                   # pruebas locales (JUnit + kotlinx-coroutines-test)
./gradlew connectedAndroidTest   # pruebas instrumentadas de Compose (requiere dispositivo o emulador)
```

- Locales: [`MainScreenViewModelTest.kt`](app/src/test/kotlin/com/taxtech/mobile/ui/main/MainScreenViewModelTest.kt).
- Instrumentadas: [`MainScreenTest.kt`](app/src/androidTest/kotlin/com/taxtech/mobile/ui/main/MainScreenTest.kt).

## Estructura

```
mobile/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/taxtech/mobile/
│       │   │   ├── MainActivity.kt          # punto de entrada; aplica el tema y la navegación
│       │   │   ├── Navigation.kt            # NavDisplay de Navigation 3 con la pila de pantallas
│       │   │   ├── NavigationKeys.kt        # llaves de navegación (@Serializable NavKey)
│       │   │   ├── data/DataRepository.kt   # interfaz del repositorio y su implementación
│       │   │   └── ui/
│       │   │       ├── main/                # MainScreen + MainScreenViewModel (estado de UI)
│       │   │       └── theme/               # colores, tipografía y TAXTechTheme
│       │   └── res/                         # íconos, cadenas y temas
│       ├── test/                            # pruebas locales
│       └── androidTest/                     # pruebas instrumentadas
├── gradle/libs.versions.toml                # catálogo de versiones
├── build.gradle.kts
└── settings.gradle.kts
```

### Arquitectura

La app sigue el patrón recomendado por Android:

1. **Datos**: `DataRepository` expone un `Flow`. La implementación actual (`DefaultDataRepository`) devuelve datos fijos.
2. **ViewModel**: `MainScreenViewModel` convierte el `Flow` en un `StateFlow<MainScreenUiState>` con tres estados: `Loading`, `Success` y `Error`.
3. **UI**: `MainScreen` observa el estado con `collectAsStateWithLifecycle()` y dibuja cada caso.
4. **Navegación**: cada pantalla nueva necesita una llave `@Serializable` en `NavigationKeys.kt` y su `entry<...>` en `Navigation.kt`.

## Conexión con el backend

Igual que el panel web, la app debe obtener **todos** los datos a través de la Edge Function de Supabase `conciliacion-api` (POST con `{ accion, ... }` y el JWT del usuario), nunca consultando tablas directamente. La Edge Function valida la sesión y el rol (`LECTOR` / `EDITOR` / `ADMIN`) y escribe la bitácora.

El esquema SQL, el motor de conciliación y la Edge Function están en un repositorio privado aparte. Por eso:

- No agregues aquí código del modelo de datos o del backend.
- No incluyas datos fiscales o bancarios reales; los datos de demostración son ficticios.
- No guardes credenciales ni llaves privadas en el código. La clave anon de Supabase es pública por diseño.

## Convenciones

- Todo el texto (interfaz, comentarios, commits y documentación) va en español neutro. Los identificadores nuevos también van en español, como en el panel web.
- Las cadenas visibles van en `res/values/strings.xml`, no escritas directamente en el código.
- Todo cambio debe verse bien en modo claro y oscuro.
