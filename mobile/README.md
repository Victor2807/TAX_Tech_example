# TAX Tech · App móvil (Android)

Aplicación Android de **TAX Tech**, el proyecto de conciliación fiscal en México (CFDI ↔ ERP SAP ↔ estados de cuenta bancarios ↔ pedimentos del Data Stage del SAT). Es el complemento móvil del panel web (`../_index.html`); la descripción general del proyecto, las conciliaciones C1 a C10 y los estatus de resultado están en el [README principal](../README.md).

> **Estado actual:** es la estructura base, creada a partir de la plantilla de Android Studio (Compose + Navigation 3) y traducida al español. Todavía no se conecta a Supabase ni muestra datos de conciliación: la pantalla principal muestra un saludo (`¡Hola, mundo!`) y el mensaje *Bienvenido a TAX Tech* con datos fijos.

## Tecnologías

| Componente | Versión |
|---|---|
| Kotlin | 2.4.21 |
| Android Gradle Plugin | 9.5.1 |
| Gradle (wrapper) | 9.9.1 |
| Jetpack Compose (BOM) | 2026.09.00 |
| Material 3 | según el BOM |
| Navigation 3 | 1.2.1 |
| Lifecycle / ViewModel | 2.11.0 |
| Core KTX | 1.19.1 |
| Coroutines (pruebas) | 1.11.0 |
| kotlinx.serialization | plugin de Kotlin 2.4.21 |
| JDK para compilar la app | 17 (`jvmToolchain(17)`) |
| JDK del daemon de Gradle | 25, Adoptium (`gradle/gradle-daemon-jvm.properties`) |

- `minSdk` 24 · `targetSdk` y `compileSdk` 37.
- Paquete y `applicationId`: `com.taxtech.mobile`.
- Las versiones se centralizan en el catálogo [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Requisitos

- Android Studio reciente con soporte para AGP 9.
- JDK 17 y JDK 25. Si no están instalados, Gradle los descarga con el *foojay resolver*.
- Gradle tiene activados la caché de compilación y la *configuration cache* (`gradle.properties`).
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

- Locales: [`MainScreenViewModelTest.kt`](app/src/test/kotlin/com/taxtech/mobile/ui/main/MainScreenViewModelTest.kt). Revisa que el estado empiece en `Loading`, que pase a `Success` con los datos del repositorio y que pase a `Error` cuando el repositorio falla. Sustituye `Dispatchers.Main` por un `UnconfinedTestDispatcher`, porque `viewModelScope` lo necesita y no existe fuera de Android.
- Instrumentadas: [`MainScreenTest.kt`](app/src/androidTest/kotlin/com/taxtech/mobile/ui/main/MainScreenTest.kt). Revisa que se muestre `¡Hola, <nombre>!` para cada dato.

## Estructura

```
mobile/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro                   # reglas de R8 (solo si se activa la minificación)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/taxtech/mobile/
│       │   │   ├── MainActivity.kt          # punto de entrada; aplica el tema y la navegación
│       │   │   ├── Navigation.kt            # NavDisplay de Navigation 3 con la pila de pantallas
│       │   │   ├── NavigationKeys.kt        # llaves de navegación (@Serializable NavKey)
│       │   │   ├── data/DataRepository.kt   # interfaz del repositorio y su implementación
│       │   │   └── ui/
│       │   │       ├── main/                # MainScreen, Saludo y MainScreenViewModel (estado de UI)
│       │   │       └── theme/               # colores, tipografía y TAXTechTheme (claro y oscuro)
│       │   └── res/                         # íconos, cadenas (strings.xml) y temas
│       ├── test/                            # pruebas locales
│       └── androidTest/                     # pruebas instrumentadas
├── gradle/
│   ├── libs.versions.toml                   # catálogo de versiones
│   └── gradle-daemon-jvm.properties         # JDK con el que corre Gradle
├── gradle.properties
├── build.gradle.kts
└── settings.gradle.kts
```

### Arquitectura

La app sigue el patrón recomendado por Android:

1. **Datos**: `DataRepository` expone un `Flow`. La implementación actual (`DefaultDataRepository`) devuelve datos fijos.
2. **ViewModel**: `MainScreenViewModel` convierte el `Flow` en un `StateFlow<MainScreenUiState>` con tres estados: `Loading`, `Success` y `Error`.
3. **UI**: `MainScreen` observa el estado con `collectAsStateWithLifecycle()` y dibuja cada caso: un indicador de carga, la lista de saludos o el mensaje `error_carga`.
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
- Todo cambio debe verse bien en modo claro y oscuro. El color dinámico de Android 12+ está desactivado (`dynamicColor = false`) para conservar la paleta de la app.
