#!/usr/bin/env python3
"""
taxtech.py: consulta la API local de TAX Tech desde la terminal, con tablas de rich.

La API es el backend NestJS de la app nueva (repositorio privado, `app/backend`), que
escucha en http://127.0.0.1:3001/api. Este utilitario solo **consulta**: además del
inicio de sesión, únicamente hace peticiones GET. No conoce el modelo de datos; muestra
lo que la API devuelve.

Uso rápido:
    python taxtech.py login
    python taxtech.py empresas
    python taxtech.py resumen --empresa 1
    python taxtech.py tablero --empresa 1 --meses 6
    python taxtech.py -h                      (y -h en cada comando)

Variables de entorno:
    TAXTECH_API     URL base de la API (por omisión http://127.0.0.1:3001/api)
    TAXTECH_TOKEN   JWT a usar en lugar de la sesión guardada
"""
from __future__ import annotations

import argparse
import difflib
import getpass
import json
import os
import re
import shlex
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any

try:
    from rich import box
    from rich.columns import Columns
    from rich.console import Console
    from rich.panel import Panel
    from rich.prompt import Prompt
    from rich.table import Table
    from rich.text import Text
except ImportError:  # pragma: no cover
    sys.exit("Falta la librería rich. Instálala con: pip install -r requirements.txt")

# En Windows, localhost puede resolverse primero a ::1 y la API solo escucha en 127.0.0.1.
API_POR_OMISION = "http://127.0.0.1:3001/api"
ARCHIVO_SESION = Path.home() / ".taxtech" / "sesion.json"
TIEMPO_ESPERA = 60

consola = Console()
errores = Console(stderr=True)


# =============================================================================
# Sesión y llamadas a la API
# =============================================================================
class ErrorApi(Exception):
    def __init__(self, mensaje: str, estado: int | None = None):
        super().__init__(mensaje)
        self.estado = estado


def leer_sesion() -> dict[str, Any]:
    try:
        return json.loads(ARCHIVO_SESION.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}


def guardar_sesion(datos: dict[str, Any]) -> None:
    ARCHIVO_SESION.parent.mkdir(parents=True, exist_ok=True)
    ARCHIVO_SESION.write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")
    try:
        os.chmod(ARCHIVO_SESION, 0o600)  # En Windows solo quita la escritura de otros; no falla.
    except OSError:
        pass


def borrar_sesion() -> None:
    try:
        ARCHIVO_SESION.unlink()
    except FileNotFoundError:
        pass


def url_api(args: argparse.Namespace) -> str:
    return (args.api or os.environ.get("TAXTECH_API") or leer_sesion().get("api") or API_POR_OMISION).rstrip("/")


def token_actual() -> str | None:
    return os.environ.get("TAXTECH_TOKEN") or leer_sesion().get("token")


def llamar(args: argparse.Namespace, metodo: str, ruta: str, consulta: dict[str, Any] | None = None,
           cuerpo: dict[str, Any] | None = None, con_token: bool = True) -> Any:
    """Hace la petición y devuelve el JSON. Lanza ErrorApi con el mensaje de la API."""
    base = url_api(args)
    parametros = {k: v for k, v in (consulta or {}).items() if v is not None and v != ""}
    url = f"{base}/{ruta.lstrip('/')}"
    if parametros:
        url += "?" + urllib.parse.urlencode(parametros)

    encabezados = {"Accept": "application/json"}
    datos = None
    if cuerpo is not None:
        datos = json.dumps(cuerpo).encode("utf-8")
        encabezados["Content-Type"] = "application/json"
    if con_token:
        token = token_actual()
        if not token:
            raise ErrorApi("No hay sesión. Ejecuta: python taxtech.py login", 401)
        encabezados["Authorization"] = f"Bearer {token}"

    peticion = urllib.request.Request(url, data=datos, headers=encabezados, method=metodo)
    try:
        with urllib.request.urlopen(peticion, timeout=TIEMPO_ESPERA) as r:
            texto = r.read().decode("utf-8")
            return json.loads(texto) if texto else None
    except urllib.error.HTTPError as e:
        mensaje = f"Error {e.code}"
        try:
            j = json.loads(e.read().decode("utf-8"))
            m = j.get("message")
            mensaje = "; ".join(map(str, m)) if isinstance(m, list) else str(m or mensaje)
            if j.get("codigo") == "CAMBIAR_CONTRASENA":
                mensaje += " (cambia tu contraseña desde el panel web)"
        except (ValueError, AttributeError):
            pass
        if e.code == 401 and con_token and not os.environ.get("TAXTECH_TOKEN"):
            borrar_sesion()
            mensaje += " La sesión se cerró; vuelve a ejecutar: python taxtech.py login"
        raise ErrorApi(mensaje, e.code) from None
    except urllib.error.URLError as e:
        raise ErrorApi(
            f"No se pudo conectar con la API en {base} ({e.reason}). "
            "¿Está corriendo el backend? (en app/: npm run dev)"
        ) from None


# =============================================================================
# Formato
# =============================================================================
# Los importes (Decimal) viajan como texto; se reconocen por el nombre de la columna.
PATRON_DINERO = re.compile(
    r"importe|^iva|_iva$|iva_|saldo|diferencia|trasladado|cobrado|pagado|acreditable|retenido|"
    r"riesgo|notas_|causado|registrado|enterado|por_enterar|^isr$|^total$|^pendiente|^ret_|tolerancia_importe"
)
PATRON_NUMERO = re.compile(r"^-?\d+(\.\d+)?$")

# Tono de cada estatus de resultado (mismos colores que el panel).
COLOR_ESTATUS = {
    "CONCILIADO": "green", "DIFERENCIA_IMPORTE": "yellow", "DIFERENCIA_TC": "dark_orange",
    "SOLO_ERP": "medium_purple", "SOLO_CFDI": "sky_blue1", "SOLO_BANCO": "dark_cyan",
    "SOLO_PEDIMENTO": "yellow", "CFDI_CANCELADO": "light_coral", "PROVEEDOR_69B": "bold red",
    "PPD_SIN_REP": "magenta", "PAGO_SIN_BANCO": "gold1", "SIN_PAGO": "grey62",
    "NOTA_SIN_RELACION": "hot_pink", "RELACION_INVALIDA": "light_coral", "EXCEDE_SALDO": "bold red",
    "IVA_NO_PROPORCIONAL": "slate_blue1",
}
COLOR_SEVERIDAD = {"critica": "bold white on red", "alta": "bold red", "media": "yellow", "info": "cyan"}
COLOR_PRIORIDAD = {"Alta": "bold red", "Media": "yellow", "Baja": "grey62"}


def es_dinero(clave: str, valor: Any) -> bool:
    return isinstance(valor, str) and bool(PATRON_NUMERO.match(valor)) and bool(PATRON_DINERO.search(clave))


def dinero(valor: Any) -> Text:
    try:
        d = Decimal(str(valor))
    except InvalidOperation:
        return Text(str(valor))
    return Text(f"-${-d:,.2f}" if d < 0 else f"${d:,.2f}", style="red" if d < 0 else "")


def celda(clave: str, valor: Any, ancho_max: int = 60) -> Text:
    if valor is None:
        return Text("—", style="dim")
    if isinstance(valor, bool):
        return Text("Sí" if valor else "No", style="green" if valor else "dim")
    if es_dinero(clave, valor):
        return dinero(valor)
    if isinstance(valor, (int, float)):
        return Text(f"{valor:,}")
    if isinstance(valor, (dict, list)):
        texto = json.dumps(valor, ensure_ascii=False)
    else:
        texto = str(valor)
    if len(texto) > ancho_max:
        texto = texto[: ancho_max - 1] + "…"
    if clave.startswith("estatus"):
        return Text(texto, style=COLOR_ESTATUS.get(texto, ""))
    if clave == "severidad":
        return Text(texto, style=COLOR_SEVERIDAD.get(texto, ""))
    if clave == "prioridad":
        return Text(texto, style=COLOR_PRIORIDAD.get(texto, ""))
    return Text(texto)


SIGLAS = {"rfc", "uuid", "iva", "isr", "erp", "sap", "cfdi", "rep", "id", "c4", "tc"}


def encabezado(clave: str) -> str:
    palabras = [w.upper() if w in SIGLAS else w for w in clave.split("_")]
    texto = " ".join(palabras)
    return texto[:1].upper() + texto[1:]


def tabla(filas: list[dict[str, Any]], titulo: str | None = None, columnas: list[str] | None = None,
          limite: int | None = None, totales: bool = False) -> None:
    if not filas:
        consola.print(Panel(Text("Sin datos.", style="dim"), title=titulo, border_style="dim"))
        return
    if columnas is None:
        columnas = []
        for f in filas:
            columnas += [k for k in f if k not in columnas]
    t = Table(title=titulo, box=box.SIMPLE_HEAVY, header_style="bold cyan", title_justify="left",
              title_style="bold", show_footer=totales)
    suma: dict[str, Decimal] = {}
    for c in columnas:
        muestra = next((f.get(c) for f in filas if f.get(c) is not None), None)
        derecha = es_dinero(c, muestra) or isinstance(muestra, (int, float)) and not isinstance(muestra, bool)
        if totales and es_dinero(c, muestra):
            suma[c] = sum((Decimal(str(f[c])) for f in filas if es_dinero(c, f.get(c))), Decimal(0))
        pie = dinero(suma[c]) if c in suma else (Text("Total", style="bold") if c == columnas[0] else "")
        t.add_column(encabezado(c), justify="right" if derecha else "left", overflow="fold", footer=pie)
    mostradas = filas if limite is None else filas[:limite]
    for f in mostradas:
        t.add_row(*(celda(c, f.get(c)) for c in columnas))
    consola.print(t)
    if len(mostradas) < len(filas):
        consola.print(f"[dim]Se muestran {len(mostradas):,} de {len(filas):,} filas (usa --limite).[/dim]")


def ficha(datos: dict[str, Any], titulo: str | None = None) -> None:
    t = Table.grid(padding=(0, 2))
    t.add_column(style="bold cyan", no_wrap=True)
    t.add_column()
    for k, v in datos.items():
        t.add_row(encabezado(k), celda(k, v, ancho_max=120))
    consola.print(Panel(t, title=titulo, title_align="left", border_style="cyan", expand=False))


def mostrar(dato: Any, titulo: str | None = None, limite: int | None = None) -> None:
    """Muestra cualquier respuesta: escalares en una ficha y listas de objetos en tablas."""
    if isinstance(dato, list):
        if dato and all(isinstance(x, dict) for x in dato):
            tabla(dato, titulo, limite=limite)
        else:
            ficha({"valores": dato}, titulo)
        return
    if not isinstance(dato, dict):
        consola.print(dato)
        return
    simples = {k: v for k, v in dato.items() if not isinstance(v, (dict, list))
               or (isinstance(v, list) and not all(isinstance(x, dict) for x in v))}
    if simples:
        ficha(simples, titulo)
    for k, v in dato.items():
        if k in simples:
            continue
        sub = f"{titulo} › {encabezado(k)}" if titulo else encabezado(k)
        mostrar(v, sub, limite)


def salida_json(dato: Any) -> None:
    consola.print_json(json.dumps(dato, ensure_ascii=False))


# =============================================================================
# Comandos
# =============================================================================
def cmd_login(args: argparse.Namespace) -> None:
    email = args.email or Prompt.ask("Correo")
    contrasena = os.environ.get("TAXTECH_CONTRASENA") or getpass.getpass("Contraseña: ")
    r = llamar(args, "POST", "auth/login", cuerpo={"email": email, "contrasena": contrasena}, con_token=False)
    guardar_sesion({"api": url_api(args), "token": r["token"], "usuario": r["usuario"]})
    u = r["usuario"]
    consola.print(f"[green]✓[/green] Sesión iniciada como [bold]{u['email']}[/bold] ({u['rol']}).")
    consola.print(f"[dim]Token guardado en {ARCHIVO_SESION}[/dim]")
    if u.get("debe_cambiar_contrasena"):
        consola.print("[yellow]Debes cambiar tu contraseña en el panel web antes de consultar datos.[/yellow]")


def cmd_logout(_args: argparse.Namespace) -> None:
    borrar_sesion()
    consola.print("Sesión cerrada.")


def cmd_sesion(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", "auth/sesion")
    return d, lambda: ficha(d | {"api": url_api(args)}, "Sesión")


def cmd_empresas(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", "empresas")
    return d, lambda: tabla(d, "Empresas con acceso")


def cmd_resumen(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", "resumen", {"empresa_id": args.empresa})

    def ver() -> None:
        ficha(d.get("fuentes", {}), "Fuentes cargadas")
        tabla(d.get("empresas", []), "Empresas", ["empresa_id", "rfc", "razon_social", "grupo", "sociedad_sap"])
        ejec = [e | {"por_estatus": ", ".join(f"{k} {v}" for k, v in (e.get("por_estatus") or {}).items())}
                for e in d.get("ejecuciones", [])]
        tabla(ejec, "Últimas ejecuciones", ["ejecucion_id", "regla_codigo", "periodo_desde", "periodo_hasta",
                                            "total_partidas", "total_conciliadas", "importe_diferencias",
                                            "por_estatus", "ejecutado_en"], limite=args.limite)
        tabla(d.get("cargas", []), "Últimas cargas", ["carga_id", "fuente", "archivo", "registros",
                                                      "periodo_desde", "periodo_hasta", "cargado_en"],
              limite=args.limite)
        tabla(d.get("reglas", []), "Reglas")
        if d.get("ocultas"):
            consola.print(f"[dim]Ejecuciones ocultas: {d['ocultas']}[/dim]")
    return d, ver


def cmd_ejecuciones(args: argparse.Namespace) -> Any:
    if args.ocultas:
        d = llamar(args, "GET", "ejecuciones/ocultas")
        return d, lambda: mostrar(d, "Ejecuciones ocultas", args.limite)
    d = llamar(args, "GET", "ejecuciones", {"empresa_id": args.empresa, "regla": args.regla})
    return d, lambda: tabla(d, f"Ejecuciones de la empresa {args.empresa}",
                            ["ejecucion_id", "regla_codigo", "periodo_desde", "periodo_hasta", "total_partidas",
                             "total_conciliadas", "ejecutado_por", "ejecutado_en"], limite=args.limite)


def cmd_resultados(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", f"ejecuciones/{args.ejecucion}/resultados")
    filas = d.get("filas", [])
    if args.estatus:
        filas = [f for f in filas if f.get("estatus") == args.estatus.upper()]
    if args.pendientes:
        filas = [f for f in filas if f.get("estatus") != "CONCILIADO" and not f.get("revisado_por")]

    def ver() -> None:
        e = d.get("ejecucion", {})
        ficha({k: e.get(k) for k in ("ejecucion_id", "empresa_id", "regla_codigo", "periodo_desde",
                                     "periodo_hasta", "total_partidas", "total_conciliadas", "ejecutado_en")},
              "Ejecución")
        conteo = Counter(f.get("estatus") for f in d.get("filas", []))
        tabla([{"estatus": k, "partidas": v} for k, v in conteo.most_common()], "Partidas por estatus")
        columnas = None if args.todas_columnas else [
            "resultado_id", "estatus", "uuid", "serie_folio", "num_documento", "rfc_tercero",
            "importe_a", "importe_b", "diferencia", "revisado_por"]
        tabla(filas, f"Resultados ({len(filas):,})", columnas, limite=args.limite, totales=True)
    return d | {"filas": filas}, ver


PAPELES = {"iva": "papel-iva", "retenciones": "papel-retenciones", "notas": "papel-notas-credito"}


def cmd_papel(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", f"ejecuciones/{args.ejecucion}/{PAPELES[args.tipo]}")
    return d, lambda: mostrar(d, f"Papel de trabajo ({args.tipo})", args.limite)


def cmd_tablero(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", f"empresas/{args.empresa}/tablero-iva", {"meses": args.meses, "hasta": args.hasta})

    def ver() -> None:
        meses = d.get("meses", [])
        t = Table(title=f"Tablero de IVA · empresa {args.empresa}", box=box.SIMPLE_HEAVY,
                  header_style="bold cyan", title_justify="left", title_style="bold")
        cols = ["trasladado", "cobrado", "pagado", "acreditable", "retenido", "saldo"]
        t.add_column("Periodo")
        for c in cols:
            t.add_column(encabezado(c), justify="right")
        t.add_column("Resultado")
        for m in meses:
            saldo = Decimal(m.get("saldo") or 0)
            t.add_row(m["periodo_desde"][:7], *(dinero(m.get(c) or 0) for c in cols[:-1]),
                      dinero(abs(saldo)),
                      Text("a cargo", style="bold red") if saldo > 0 else Text("a favor", style="bold green"))
        consola.print(t if meses else Panel(Text("Sin meses con cierre.", style="dim")))
        if meses and args.detalle:
            u = meses[-1]
            tabla(u.get("riesgo_por_regla", []), f"Riesgo por regla · {u['periodo_desde'][:7]}")
            tabla(u.get("no_acreditable", []), f"IVA no acreditable · {u['periodo_desde'][:7]}")
    return d, ver


def cmd_comparativo(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", f"empresas/{args.empresa}/comparativo", {"meses": args.meses, "hasta": args.hasta})

    def ver() -> None:
        tabla(d.get("periodos", []), "Comparativo de hallazgos: al corte contra hoy",
              ["periodo_desde", "corte", "al_corte", "cerrados", "cambiaron", "nuevos", "abiertos",
               "alta_abiertos", "revisados", "riesgo_al_corte", "riesgo_abierto"])
        if args.detalle:
            tabla(d.get("detalle", []), "Detalle", ["periodo_desde", "regla", "referencia", "contraparte",
                                                    "estatus_corte", "estatus_hoy", "riesgo_hoy", "prioridad",
                                                    "estado"], limite=args.limite)
    return d, ver


def cmd_alertas(args: argparse.Namespace) -> Any:
    if args.historial:
        d = llamar(args, "GET", f"empresas/{args.empresa}/resumenes-alertas", {"limite": args.limite or 12})
        filas = [{"resumen_id": r["resumen_id"], "generado_en": r["generado_en"], "origen": r["origen"],
                  "fecha_corte": r["fecha_corte"], **r.get("conteo", {}),
                  "nuevas": len(r.get("nuevas", [])), "resueltas": len(r.get("resueltas", []))} for r in d]
        return d, lambda: tabla(filas, "Resúmenes de alertas")
    d = llamar(args, "GET", f"empresas/{args.empresa}/alertas", {"hoy": args.hoy})

    def ver() -> None:
        r = d.get("resumen", {})
        partes = [Text(f" {k}: {r.get(k, 0)} ", style=COLOR_SEVERIDAD.get(k, "")) for k in COLOR_SEVERIDAD]
        consola.print(Panel(Text.assemble(*partes, f"  revisadas: {r.get('revisadas', 0)}"),
                            title=f"Alertas al {d.get('hoy')}", title_align="left", expand=False))
        tabla(d.get("alertas", []), None, ["severidad", "categoria", "titulo", "fecha_limite", "dias",
                                           "importe", "regla", "referencia", "revisado_por"], limite=args.limite)
    return d, ver


def cmd_cargas(args: argparse.Namespace) -> Any:
    d = llamar(args, "GET", "cargas")
    return d, lambda: tabla(d, "Cargas de archivos", limite=args.limite)


def cmd_explorador(args: argparse.Namespace) -> Any:
    if not args.tabla:
        d = llamar(args, "GET", "explorador")
        filas = [{"tabla": t["nombre"], "columnas": len(t.get("columnas", [])),
                  "editable": t.get("editable"), "borrable": t.get("borrable")} for t in d]
        return d, lambda: tabla(filas, "Tablas disponibles")
    if args.id:
        d = llamar(args, "GET", f"explorador/{urllib.parse.quote(args.tabla)}/{urllib.parse.quote(args.id)}")
        return d, lambda: mostrar(d, f"{args.tabla} · {args.id}")
    consulta: dict[str, Any] = {"pagina": args.pagina, "limite": args.limite or 50, "orden": args.orden,
                                "dir": args.dir}
    for f in args.filtro or []:
        if "=" not in f:
            raise ErrorApi(f"Filtro no válido: «{f}». Usa columna=valor.")
        k, v = f.split("=", 1)
        consulta[k.strip()] = v
    d = llamar(args, "GET", f"explorador/{urllib.parse.quote(args.tabla)}", consulta)

    def ver() -> None:
        tabla(d.get("filas", []), args.tabla)
        paginas = max(1, -(-d.get("total", 0) // max(1, d.get("limite", 1))))
        consola.print(f"[dim]Página {d.get('pagina')} de {paginas} · {d.get('total', 0):,} filas[/dim]")
    return d, ver


def cmd_get(args: argparse.Namespace) -> Any:
    consulta = dict(p.split("=", 1) for p in args.param or [] if "=" in p)
    d = llamar(args, "GET", args.ruta, consulta)
    return d, lambda: mostrar(d, args.ruta, args.limite)


# =============================================================================
# Inicio: tablero interactivo que se abre al ejecutar el utilitario sin comando
# =============================================================================
LOGO = (
    "▀█▀ ▄▀█ ▀▄▀   ▀█▀ █▀▀ █▀▀ █ █",
    " █  █▀█ █ █    █  ██▄ █▄▄ █▀█",
)
# Degradado de la marca: esmeralda → dorado (paletas brand y gold del panel).
COLOR_INICIAL, COLOR_FINAL = (16, 185, 129), (245, 158, 11)
ORDEN_CIERRE = ("C1", "C2", "C3", "C4", "C6", "C7", "C8", "C10", "C5", "C9")
MESES = ("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
BLOQUES = "▁▂▃▄▅▆▇█"


def degradado(linea: str) -> Text:
    t = Text()
    n = max(1, len(linea) - 1)
    for i, c in enumerate(linea):
        r, g, b = (round(a + (z - a) * i / n) for a, z in zip(COLOR_INICIAL, COLOR_FINAL))
        t.append(c, style=f"bold #{r:02x}{g:02x}{b:02x}")
    return t


def nombre_mes(periodo: str) -> str:
    """'2026-10-01' → 'oct 26'."""
    try:
        return f"{MESES[int(periodo[5:7]) - 1]} {periodo[2:4]}"
    except (ValueError, IndexError):
        return periodo


def decimal(valor: Any) -> Decimal:
    try:
        return Decimal(str(valor))
    except (InvalidOperation, ValueError):
        return Decimal(0)


def chispa(valores: list[Decimal]) -> Text:
    """Línea de tendencia con bloques: rojo si el saldo es a cargo y verde si es a favor."""
    if not valores:
        return Text("sin meses con cierre", style="dim")
    bajo, alto = min(valores), max(valores)
    rango = (alto - bajo) or Decimal(1)
    t = Text()
    for v in valores:
        nivel = int((v - bajo) / rango * (len(BLOQUES) - 1))
        t.append(BLOQUES[nivel] * 2, style="red" if v > 0 else "green")
    return t


def barra_alertas(r: dict[str, int], ancho: int = 26) -> Text:
    total = sum(r.get(k, 0) for k in COLOR_SEVERIDAD)
    t = Text()
    if not total:
        return Text("━" * ancho, style="green") + Text("  sin alertas", style="green")
    usados = 0
    for k, estilo in COLOR_SEVERIDAD.items():
        n = round(r.get(k, 0) / total * ancho)
        if r.get(k, 0) and not n:
            n = 1
        n = min(n, ancho - usados)
        usados += n
        t.append("━" * n, style=estilo.replace("bold ", "").replace("white on ", ""))
    return t


def preparar_inicio(args: argparse.Namespace) -> dict[str, Any]:
    """Lee la sesión, mide la latencia y trae en paralelo los datos de la empresa activa."""
    e: dict[str, Any] = {"api": url_api(args), "sesion": None, "en_linea": False, "latencia": None,
                         "empresas": [], "empresa": None, "datos": {}, "fallas": {}, "aviso": None}
    inicio = time.perf_counter()
    try:
        e["sesion"] = llamar(args, "GET", "auth/sesion") if token_actual() else None
        e["en_linea"] = True
        if not e["sesion"]:  # Sin token: un 401 basta para saber que la API responde.
            llamar(args, "GET", "auth/sesion", con_token=False)
    except ErrorApi as x:
        e["en_linea"] = x.estado is not None
        if x.estado not in (None, 401):
            e["aviso"] = str(x)
    e["latencia"] = (time.perf_counter() - inicio) * 1000
    s = e["sesion"]
    if not s or s.get("debe_cambiar_contrasena"):
        return e

    with consola.status("[cyan]Tomando el pulso fiscal…[/cyan]", spinner="dots12"):
        try:
            e["empresas"] = llamar(args, "GET", "empresas")
        except ErrorApi as x:
            e["fallas"]["empresas"] = str(x)
        if not e["empresas"]:
            return e
        guardada = leer_sesion().get("empresa_id")
        e["empresa"] = next((x for x in e["empresas"] if x["empresa_id"] == guardada), e["empresas"][0])
        eid = e["empresa"]["empresa_id"]
        tareas = {
            "resumen": ("resumen", {"empresa_id": eid}),
            "tablero": (f"empresas/{eid}/tablero-iva", {"meses": 12}),
            "alertas": (f"empresas/{eid}/alertas", None),
        }
        with ThreadPoolExecutor(max_workers=len(tareas)) as ex:
            futuros = {k: ex.submit(llamar, args, "GET", ruta, q) for k, (ruta, q) in tareas.items()}
            for k, f in futuros.items():
                try:
                    e["datos"][k] = f.result()
                except ErrorApi as x:
                    e["fallas"][k] = str(x)
    return e


def sugerencias(e: dict[str, Any]) -> list[tuple[str, str, list[str] | None]]:
    """Siguientes pasos según los datos: (texto, estilo, comando que los abre o None)."""
    d, eid = e["datos"], str(e["empresa"]["empresa_id"])
    pasos: list[tuple[str, str, list[str] | None]] = []
    r = (d.get("alertas") or {}).get("resumen", {})
    if r.get("critica"):
        pasos.append((f"{r['critica']} alerta(s) crítica(s) esperan tu atención", "bold red",
                      ["alertas", "--empresa", eid]))
    meses = sorted((d.get("tablero") or {}).get("meses", []), key=lambda m: m["periodo_desde"])
    ejecuciones = (d.get("resumen") or {}).get("ejecuciones", [])
    if meses:
        u = meses[-1]
        if u.get("reglas_ejecutadas", 10) < 10:
            pasos.append((f"El cierre de {nombre_mes(u['periodo_desde'])} lleva {u['reglas_ejecutadas']} de 10 reglas: "
                          "complétalo en el panel", "yellow", None))
        riesgos = [x for x in u.get("riesgo_por_regla", []) if decimal(x.get("importe_riesgo")) > 0]
        if riesgos:
            mayor = max(riesgos, key=lambda x: decimal(x["importe_riesgo"]))
            ej = next((x for x in ejecuciones if x["regla_codigo"] == mayor["regla"]
                       and x["periodo_desde"] == u["periodo_desde"]), None)
            pasos.append((f"{mayor['regla']} concentra el mayor riesgo de {nombre_mes(u['periodo_desde'])}: "
                          f"{dinero(mayor['importe_riesgo']).plain} en {mayor['hallazgos']} hallazgo(s)", "dark_orange",
                          ["resultados", str(ej["ejecucion_id"]), "--pendientes"] if ej else None))
        c5 = u.get("ejecucion_c5")
        if c5:
            pasos.append((f"Consulta el papel de trabajo de IVA de {nombre_mes(u['periodo_desde'])}", "cyan",
                          ["papel", "iva", str(c5)]))
    elif not e["fallas"].get("tablero"):
        pasos.append(("Aún no hay meses con cierre: carga fuentes y ejecuta el cierre en el panel", "yellow", None))
    cargas = (d.get("resumen") or {}).get("cargas", [])
    if cargas:
        ultima = max(c["cargado_en"] for c in cargas)
        try:
            dias = (datetime.now(timezone.utc) - datetime.fromisoformat(ultima.replace("Z", "+00:00"))).days
            if dias > 30:
                pasos.append((f"La última carga de archivos fue hace {dias} días", "yellow", ["cargas"]))
        except ValueError:
            pass
    if not pasos:
        pasos.append(("Todo en orden: no hay pendientes urgentes", "green", None))
    return pasos[:4]


def teclas_pasos(pasos: list[tuple[str, str, list[str] | None]]) -> dict[str, list[str]]:
    """Letras a, b, c… solo para los pasos que abren un comando."""
    accionables = [c for _, _, c in pasos if c]
    return {chr(ord("a") + i): c for i, c in enumerate(accionables)}


def pintar_encabezado(e: dict[str, Any]) -> None:
    hora = datetime.now().hour
    saludo = "Buenos días" if hora < 12 else "Buenas tardes" if hora < 19 else "Buenas noches"
    s = e["sesion"]
    derecha = Text(justify="right")
    if s:
        derecha.append(f"{saludo}, ", style="dim").append(s["email"].split("@")[0], style="bold")
        derecha.append(f"  {s['rol']} ", style="bold black on #10b981")
    else:
        derecha.append(f"{saludo}. ", style="dim").append("Sin sesión", style="bold yellow")
    derecha.append("\n")
    if e["empresa"]:
        x = e["empresa"]
        derecha.append(f"{x['rfc']} · {x['razon_social']}", style="bold #f59e0b")
        if x.get("grupo"):
            derecha.append(f"  ({x['grupo']})", style="dim")
    derecha.append("\n")
    if e["en_linea"]:
        ms = e["latencia"] or 0
        derecha.append("● ", style="green" if ms < 300 else "yellow").append(f"API en línea · {ms:.0f} ms", style="dim")
    else:
        derecha.append("● ", style="red").append("API fuera de línea", style="dim")
    derecha.append(f"  ·  {datetime.now():%d/%m/%Y %H:%M}", style="dim")

    g = Table.grid(expand=True)
    g.add_column(no_wrap=True)
    logo = Text("\n").join(degradado(l) for l in LOGO)
    logo.append("\nconciliación fiscal · CFDI ↔ SAP ↔ bancos ↔ Data Stage", style="dim italic")
    if consola.width >= 120:
        g.add_column(justify="right")
        g.add_row(logo, derecha)
    else:  # En terminales angostas, los datos de la sesión van debajo del logo.
        derecha.justify = "left"
        g.add_row(logo)
        g.add_row(Text("─" * 20, style="dim"))
        g.add_row(derecha)
    consola.print(Panel(g, border_style="#10b981", box=box.ROUNDED, padding=(0, 2)))


def tarjeta(titulo: str, cuerpo: Any, color: str = "cyan") -> Panel:
    return Panel(cuerpo, title=f"[bold]{titulo}[/bold]", title_align="left", border_style=color,
                 box=box.ROUNDED, padding=(0, 1), width=34, height=8)


def pintar_pulso(e: dict[str, Any]) -> None:
    d = e["datos"]
    tarjetas = []
    meses = sorted((d.get("tablero") or {}).get("meses", []), key=lambda m: m["periodo_desde"])
    if meses:
        u, saldo = meses[-1], decimal(meses[-1]["saldo"])
        cuerpo = Text()
        cuerpo.append(nombre_mes(u["periodo_desde"]).upper() + "\n", style="dim")
        cuerpo.append(dinero(abs(saldo)).plain, style="bold red" if saldo > 0 else "bold green")
        cuerpo.append("  a cargo" if saldo > 0 else "  a favor", style="red" if saldo > 0 else "green")
        if len(meses) > 1:
            delta = saldo - decimal(meses[-2]["saldo"])
            mejora = delta < 0
            cuerpo.append(f"\n{'▼' if delta < 0 else '▲'} {dinero(abs(delta)).plain} vs "
                          f"{nombre_mes(meses[-2]['periodo_desde'])}", style="green" if mejora else "red")
        cuerpo.append(f"\nretenido {dinero(u.get('retenido') or 0).plain}", style="dim")
        tarjetas.append(tarjeta("IVA del mes", cuerpo, "#10b981"))
        tendencia = Text("saldo de los últimos meses\n", style="dim")
        tendencia.append(chispa([decimal(m["saldo"]) for m in meses]))
        tendencia.append(f"\n{nombre_mes(meses[0]['periodo_desde'])} → {nombre_mes(meses[-1]['periodo_desde'])}",
                         style="dim")
        tendencia.append("\n▇ a cargo", style="red").append("  ▇ a favor", style="green")
        tarjetas.append(tarjeta("Tendencia", tendencia, "#10b981"))
    else:
        tarjetas.append(tarjeta("IVA del mes", Text(e["fallas"].get("tablero", "Sin meses con cierre."), style="dim")))

    a = d.get("alertas")
    if a:
        r = a.get("resumen", {})
        cuerpo = Text("al " + str(a.get("hoy")) + "\n", style="dim")
        cuerpo.append(barra_alertas(r)).append("\n")
        for k in COLOR_SEVERIDAD:
            cuerpo.append(f"{k} {r.get(k, 0)}  ", style=COLOR_SEVERIDAD[k].replace("white on ", ""))
        cuerpo.append(f"\nrevisadas {r.get('revisadas', 0)}", style="dim")
        tarjetas.append(tarjeta("Alertas", cuerpo, "red" if r.get("critica") else "yellow" if r.get("alta") else "green"))

    f = (d.get("resumen") or {}).get("fuentes")
    if f:
        cuerpo = Text()
        for etiqueta, clave in (("CFDI", "cfdi"), ("  recibidos", "cfdi_recibidos"), ("Documentos ERP", "erp_documentos"),
                                ("Movimientos", "movimientos"), ("Pedimentos", "pedimentos"), ("Terceros 69-B", "terceros_69b")):
            n = f.get(clave, 0)
            cuerpo.append(f"{etiqueta:<16}", style="dim").append(f"{n:>9,}\n",
                                                                 style="bold red" if clave == "terceros_69b" and n else "bold")
        cuerpo.rstrip()
        tarjetas.append(tarjeta("Fuentes", cuerpo, "#f59e0b"))
    consola.print(Columns(tarjetas, padding=(0, 1)))


def mapa_cierre(e: dict[str, Any]) -> Table | Text:
    """Mapa de calor regla × mes con el porcentaje de partidas conciliadas."""
    ejecuciones = (e["datos"].get("resumen") or {}).get("ejecuciones", [])
    if not ejecuciones:
        return Text("Sin ejecuciones visibles.", style="dim")
    periodos = sorted({x["periodo_desde"] for x in ejecuciones})[-6:]
    ultima: dict[tuple[str, str], dict[str, Any]] = {}
    for x in sorted(ejecuciones, key=lambda x: x["ejecutado_en"]):
        ultima[(x["regla_codigo"], x["periodo_desde"])] = x
    t = Table(box=box.SIMPLE, header_style="dim", pad_edge=False, padding=(0, 1))
    t.add_column("Regla", style="bold")
    for p in periodos:
        t.add_column(nombre_mes(p), justify="center")
    for regla in ORDEN_CIERRE:
        celdas = []
        for p in periodos:
            x = ultima.get((regla, p))
            if not x:
                celdas.append(Text("·", style="dim"))
                continue
            total = x.get("total_partidas") or 0
            tasa = (x.get("total_conciliadas") or 0) / total if total else 1
            estilo = "black on green" if tasa >= 0.95 else "black on yellow" if tasa >= 0.8 else "white on red"
            celdas.append(Text(f" {tasa:>4.0%} ", style=estilo))
        t.add_row(regla, *celdas)
    t.caption = "% conciliado · «·» sin ejecutar"
    t.caption_style = "dim"
    return t


def pintar_inicio(e: dict[str, Any], pasos: list[tuple[str, str, list[str] | None]]) -> None:
    pintar_encabezado(e)
    if e["aviso"]:
        consola.print(f"[yellow]{e['aviso']}[/yellow]")
    s = e["sesion"]
    if not e["en_linea"]:
        consola.print(Panel(f"No hay respuesta de [bold]{e['api']}[/bold].\nInicia el backend en el repo privado "
                            "(en [bold]app/[/bold]: [cyan]npm run dev[/cyan]) y pulsa Enter para reintentar.",
                            border_style="red", title="Sin conexión", title_align="left"))
    elif not s:
        consola.print(Panel("Pulsa [bold cyan]l[/bold cyan] para iniciar sesión y ver el pulso fiscal de tus empresas.",
                            border_style="yellow", title="Bienvenido", title_align="left"))
    elif s.get("debe_cambiar_contrasena"):
        consola.print(Panel("Debes cambiar tu contraseña en el panel web antes de consultar datos.",
                            border_style="yellow"))
    elif not e["empresa"]:
        consola.print(Panel(e["fallas"].get("empresas", "No tienes empresas asignadas. Pide acceso a un ADMIN."),
                            border_style="yellow"))
    else:
        pintar_pulso(e)
        brujula = Text()
        teclas = teclas_pasos(pasos)
        for texto, estilo, comando in pasos:
            tecla = next((k for k, c in teclas.items() if c is comando), None)
            brujula.append(f" {tecla} " if tecla else " · ", style="bold black on cyan" if tecla else "dim")
            brujula.append(f" {texto}\n", style=estilo)
        brujula.rstrip()
        g = Table.grid(expand=True, padding=(0, 1))
        g.add_column(ratio=1)
        g.add_column(ratio=1)
        panel_mapa = Panel(mapa_cierre(e), title="[bold]Mapa de cierre[/bold]", title_align="left",
                           border_style="#10b981", box=box.ROUNDED)
        panel_brujula = Panel(brujula, title="[bold]Brújula · siguiente paso[/bold]", title_align="left",
                              border_style="cyan", box=box.ROUNDED)
        if consola.width >= 110:
            g.add_row(panel_mapa, panel_brujula)
            consola.print(g)
        else:
            consola.print(panel_mapa, panel_brujula)
        for k, msg in e["fallas"].items():
            consola.print(f"[dim]No se pudo leer {k}: {msg}[/dim]")
    pintar_menu(e)


def opciones_menu(e: dict[str, Any]) -> list[tuple[str, str, list[str] | None]]:
    if not e["sesion"]:
        return [("l", "Iniciar sesión", None)]
    if not e["empresa"]:
        return [("l", "Cerrar sesión", None)]
    eid = str(e["empresa"]["empresa_id"])
    return [
        ("1", "Resumen", ["resumen", "--empresa", eid]),
        ("2", "Tablero de IVA", ["tablero", "--empresa", eid, "--detalle"]),
        ("3", "Alertas", ["alertas", "--empresa", eid]),
        ("4", "Comparativo", ["comparativo", "--empresa", eid]),
        ("5", "Ejecuciones", ["ejecuciones", "--empresa", eid]),
        ("6", "Resultados…", ["resultados"]),
        ("7", "Papel de trabajo…", ["papel"]),
        ("8", "Cargas", ["cargas"]),
        ("9", "Explorador", ["explorador"]),
        ("e", "Cambiar empresa", None),
        ("l", "Cerrar sesión", None),
    ]


def pintar_menu(e: dict[str, Any]) -> None:
    t = Text()
    for tecla, etiqueta, _ in opciones_menu(e) + [("q", "Salir", None)]:
        t.append(f" {tecla} ", style="bold black on #f59e0b").append(f" {etiqueta}   ")
    t.append("\n↵ actualizar · escribe un comando (p. ej. ", style="dim")
    t.append("resultados 42 --pendientes", style="cyan").append(") o una palabra y te ayudo a encontrarlo", style="dim")
    consola.print(Panel(t, box=box.HORIZONTALS, border_style="dim"))


def elegir_empresa(e: dict[str, Any]) -> None:
    tabla([{"#": i + 1, **{k: x.get(k) for k in ("rfc", "razon_social", "grupo")}}
           for i, x in enumerate(e["empresas"])], "Tus empresas")
    n = Prompt.ask("Número de empresa", default="1")
    if n.isdigit() and 1 <= int(n) <= len(e["empresas"]):
        s = leer_sesion()
        s["empresa_id"] = e["empresas"][int(n) - 1]["empresa_id"]
        guardar_sesion(s)


def completar_comando(base: list[str]) -> list[str] | None:
    """Pide los datos que faltan a «Resultados…» y «Papel de trabajo…»."""
    if base == ["resultados"]:
        n = Prompt.ask("ejecucion_id (consúltalo en Ejecuciones o en el mapa)")
        return ["resultados", n, "--pendientes"] if n.isdigit() else None
    if base == ["papel"]:
        tipo = Prompt.ask("Tipo", choices=list(PAPELES), default="iva")
        n = Prompt.ask("ejecucion_id")
        return ["papel", tipo, n] if n.isdigit() else None
    return base


def interpretar(texto: str, e: dict[str, Any]) -> list[str] | None:
    """Comando escrito tal cual o búsqueda aproximada en el menú («tablro» → Tablero de IVA)."""
    try:
        partes = shlex.split(texto, posix=os.name != "nt")
    except ValueError:
        return None
    nombres = {"login", "logout", "sesion", "empresas", "resumen", "ejecuciones", "resultados", "papel",
               "tablero", "comparativo", "alertas", "cargas", "explorador", "get"}
    if partes and partes[0] in nombres:
        return partes
    menu = {etiqueta.lower().rstrip("…"): comando for _, etiqueta, comando in opciones_menu(e) if comando}
    candidatos = difflib.get_close_matches(texto.lower(), list(menu) + sorted(nombres), n=1, cutoff=0.5)
    if not candidatos:
        return None
    elegido = candidatos[0]
    if not Prompt.ask(f"¿Quisiste decir [bold cyan]{elegido}[/bold cyan]?", choices=["s", "n"], default="s") == "s":
        return None
    # Un nombre de comando lleva los argumentos del menú (p. ej. la empresa activa) si está ahí.
    por_nombre = {c[0]: c for c in menu.values()}
    return completar_comando(menu.get(elegido) or por_nombre.get(elegido) or [elegido])


def ejecutar_desde_inicio(argv: list[str], args: argparse.Namespace) -> None:
    consola.clear()
    consola.rule(f"[dim]taxtech {' '.join(argv)}[/dim]", style="#10b981")
    try:
        main((["--api", args.api] if args.api else []) + argv)
    except SystemExit:  # argparse sale con -h o con argumentos inválidos.
        pass
    consola.input("\n[dim]↵ volver al inicio[/dim] ")


class Entrada(Prompt):
    prompt_suffix = " "


def cmd_inicio(args: argparse.Namespace) -> None:
    while True:
        consola.clear()
        e = preparar_inicio(args)
        pasos = sugerencias(e) if e["empresa"] else []
        pintar_inicio(e, pasos)
        try:
            opcion = Entrada.ask("[bold #10b981]›[/bold #10b981]", default="", show_default=False).strip()
        except EOFError:
            return None
        teclas = {tecla: comando for tecla, _, comando in opciones_menu(e)}
        if opcion.lower() in ("q", "salir", "exit"):
            consola.print(degradado("Hasta pronto. ✦"))
            return None
        if not opcion:
            continue
        if opcion.lower() == "l":
            if e["sesion"]:
                cmd_logout(args)
            else:
                try:
                    cmd_login(argparse.Namespace(api=args.api, email=None))
                except ErrorApi as x:
                    errores.print(f"[bold red]✗[/bold red] {x}")
                    consola.input("[dim]↵ continuar[/dim] ")
            continue
        if opcion.lower() == "e" and e["empresas"]:
            elegir_empresa(e)
            continue
        atajos = teclas_pasos(pasos)
        if opcion in teclas and teclas[opcion]:
            argv = completar_comando(teclas[opcion])
        elif opcion.lower() in atajos:
            argv = atajos[opcion.lower()]
        else:
            argv = interpretar(opcion, e)
            if argv is None:
                consola.input(f"[yellow]No reconozco «{opcion}».[/yellow] [dim]↵ continuar[/dim] ")
                continue
        if argv:
            ejecutar_desde_inicio(argv, args)


# =============================================================================
# Argumentos
# =============================================================================
def construir_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="taxtech", description="Consulta la API local de TAX Tech (solo lectura).")
    p.add_argument("--api", help=f"URL base de la API (por omisión {API_POR_OMISION} o TAXTECH_API)")
    p.add_argument("--json", action="store_true", help="muestra la respuesta en JSON en lugar de tablas")
    sub = p.add_subparsers(dest="comando", metavar="comando")

    def comando(nombre: str, funcion, ayuda: str, limite: bool = True) -> argparse.ArgumentParser:
        s = sub.add_parser(nombre, help=ayuda, description=ayuda)
        s.set_defaults(funcion=funcion)
        if limite:
            s.add_argument("--limite", type=int, help="máximo de filas a mostrar")
        return s

    comando("inicio", cmd_inicio, "tablero interactivo (también sin comando)", limite=False)
    s = comando("login", cmd_login, "inicia sesión y guarda el token", limite=False)
    s.add_argument("--email", help="correo del usuario")
    comando("logout", cmd_logout, "borra el token guardado", limite=False)
    comando("sesion", cmd_sesion, "muestra el usuario de la sesión", limite=False)
    comando("empresas", cmd_empresas, "empresas a las que tienes acceso", limite=False)

    s = comando("resumen", cmd_resumen, "fuentes, cargas, ejecuciones y reglas")
    s.add_argument("--empresa", type=int, help="solo esta empresa (empresa_id)")

    s = comando("ejecuciones", cmd_ejecuciones, "ejecuciones visibles de una empresa")
    g = s.add_mutually_exclusive_group(required=True)
    g.add_argument("--empresa", type=int, help="empresa_id")
    g.add_argument("--ocultas", action="store_true", help="lista las ejecuciones ocultas")
    s.add_argument("--regla", type=str.upper, help="C1 … C10")

    s = comando("resultados", cmd_resultados, "partidas de una ejecución")
    s.add_argument("ejecucion", type=int, help="ejecucion_id")
    s.add_argument("--estatus", help="filtra por estatus (p. ej. SOLO_CFDI)")
    s.add_argument("--pendientes", action="store_true", help="solo hallazgos sin revisar")
    s.add_argument("--todas-columnas", action="store_true", help="muestra todas las columnas")

    s = comando("papel", cmd_papel, "papel de trabajo de una ejecución")
    s.add_argument("tipo", choices=PAPELES, help="iva (C5), retenciones (C9) o notas (C10)")
    s.add_argument("ejecucion", type=int, help="ejecucion_id")

    for nombre, funcion, ayuda in (("tablero", cmd_tablero, "tablero de IVA mensual"),
                                   ("comparativo", cmd_comparativo, "hallazgos al corte contra hoy")):
        s = comando(nombre, funcion, ayuda)
        s.add_argument("--empresa", type=int, required=True, help="empresa_id")
        s.add_argument("--meses", type=int, default=12, help="número de meses (1 a 36)")
        s.add_argument("--hasta", help="último periodo AAAA-MM")
        s.add_argument("--detalle", action="store_true", help="agrega el detalle")

    s = comando("alertas", cmd_alertas, "alertas vigentes de una empresa")
    s.add_argument("--empresa", type=int, required=True, help="empresa_id")
    s.add_argument("--hoy", help="fecha de referencia AAAA-MM-DD")
    s.add_argument("--historial", action="store_true", help="resúmenes de alertas generados")

    comando("cargas", cmd_cargas, "últimas cargas de archivos")

    s = comando("explorador", cmd_explorador, "tablas del explorador y sus filas")
    s.add_argument("tabla", nargs="?", help="nombre de la tabla (sin él, lista las tablas)")
    s.add_argument("id", nargs="?", help="llave de una fila")
    s.add_argument("--pagina", type=int, default=1)
    s.add_argument("--orden", help="columna para ordenar")
    s.add_argument("--dir", choices=("asc", "desc"), default="asc")
    s.add_argument("--filtro", action="append", metavar="COLUMNA=VALOR", help="filtro de igualdad (repetible)")

    s = comando("get", cmd_get, "GET a cualquier ruta de la API (p. ej. empresas/1/alertas)")
    s.add_argument("ruta")
    s.add_argument("param", nargs="*", metavar="CLAVE=VALOR", help="parámetros de consulta")
    return p


def main(argv: list[str] | None = None) -> int:
    args = construir_parser().parse_args(argv)
    if args.comando is None:
        args.funcion = cmd_inicio
    try:
        resultado = args.funcion(args)
        if resultado is not None:
            dato, ver = resultado
            salida_json(dato) if args.json else ver()
        return 0
    except ErrorApi as e:
        errores.print(f"[bold red]✗[/bold red] {e}")
        return 1
    except KeyboardInterrupt:
        return 130


if __name__ == "__main__":
    sys.exit(main())
