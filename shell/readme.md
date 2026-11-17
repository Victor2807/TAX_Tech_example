# taxtech: consulta de la API desde la terminal

Utilitario de línea de comandos para consultar la API local de TAX Tech (el backend NestJS de la app nueva, que vive en el repositorio privado) y mostrar las respuestas en tablas con [rich](https://github.com/Textualize/rich).

Solo **consulta**: además del inicio de sesión, únicamente hace peticiones `GET`. No contiene el modelo de datos ni la lógica de conciliación: muestra lo que la API devuelve y la API aplica los roles y el acceso por empresa.

## Requisitos

- Python 3.10 o superior.
- La API corriendo en esta máquina (en el repo privado, `app/`: `npm run dev`). Escucha en `http://127.0.0.1:3001/api`.
- Un usuario dado de alta en la app local.

```bash
pip install -r requirements.txt
```

## Inicio

Ejecuta el utilitario sin comando (`python taxtech.py`, o `python taxtech.py inicio`) para abrir el tablero interactivo:

- **Encabezado** con el logo en degradado esmeralda → dorado, saludo, perfil, empresa activa y el estado de la API con su latencia.
- **IVA del mes**: saldo del último cierre (a cargo o a favor) y su variación contra el mes anterior.
- **Tendencia**: el saldo de los últimos 12 meses en una línea de bloques (rojo a cargo, verde a favor).
- **Alertas**: barra por severidad (crítica, alta, media, info) y las revisadas.
- **Fuentes**: CFDI, documentos ERP, movimientos, pedimentos y terceros 69-B cargados.
- **Mapa de cierre**: matriz regla × mes (en el orden del cierre) con el % de partidas conciliadas, en verde (≥95 %), amarillo (≥80 %) o rojo.
- **Brújula**: hasta cuatro siguientes pasos calculados con los datos (alertas críticas, cierre incompleto, la regla con más riesgo, el papel de IVA, cargas antiguas). Los que tienen letra se abren con esa tecla.

Atajos: `1`–`9` abren las consultas de la empresa activa, `a`–`d` los pasos de la brújula, `e` cambia de empresa (se recuerda en la sesión), `l` inicia o cierra sesión, Enter actualiza y `q` sale. También puedes escribir un comando completo (`resultados 42 --pendientes`) o una palabra aproximada (`tablro` → «¿Quisiste decir tablero?»).

## Uso

```bash
python taxtech.py login                     # pide correo y contraseña; guarda el token en ~/.taxtech/sesion.json
python taxtech.py sesion
python taxtech.py empresas
python taxtech.py resumen --empresa 1
python taxtech.py ejecuciones --empresa 1 --regla C5
python taxtech.py resultados 42 --pendientes
python taxtech.py resultados 42 --estatus SOLO_CFDI --todas-columnas
python taxtech.py papel iva 42              # iva (C5), retenciones (C9) o notas (C10)
python taxtech.py tablero --empresa 1 --meses 6 --detalle
python taxtech.py comparativo --empresa 1 --detalle
python taxtech.py alertas --empresa 1
python taxtech.py alertas --empresa 1 --historial
python taxtech.py cargas
python taxtech.py explorador                # lista las tablas visibles para tu rol
python taxtech.py explorador <tabla> --filtro columna=valor --orden columna --dir desc
python taxtech.py get empresas/1/alertas hoy=2026-11-16   # cualquier ruta GET
python taxtech.py logout
```

Opciones generales (van antes del comando):

| Opción | Uso |
|---|---|
| `--json` | Muestra la respuesta en JSON en lugar de tablas (sirve para `jq` o para guardarla). |
| `--api URL` | Otra URL base de la API. |
| `--limite N` | En cada comando de listas: máximo de filas a mostrar. |

Variables de entorno:

| Variable | Uso |
|---|---|
| `TAXTECH_API` | URL base de la API (por omisión `http://127.0.0.1:3001/api`). Usa `127.0.0.1` y no `localhost`: en Windows `localhost` puede resolverse a IPv6 y la API no escucha ahí. |
| `TAXTECH_TOKEN` | JWT a usar en lugar de la sesión guardada. |
| `TAXTECH_CONTRASENA` | Contraseña para `login` sin pedirla (scripts). Evítala en equipos compartidos. |

## Notas

- Los importes viajan como texto y se muestran con formato `$1,234.56`; las tablas de resultados agregan una fila de totales.
- Los estatus, severidades de alertas y prioridades se colorean igual que en el panel.
- El token dura lo que indique `JWT_DURACION_SEGUNDOS` en la API (8 horas por omisión). Si vence, la API responde 401, el utilitario borra la sesión guardada y pide volver a ejecutar `login`.
- Si tu usuario debe cambiar la contraseña, la API solo permite `sesion`: cámbiala primero en el panel web.
- El inicio de sesión tiene límite de intentos (5 fallos por correo en 15 minutos).
