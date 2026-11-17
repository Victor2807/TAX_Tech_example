"""
Ícono de TAX Tech «Cuatro fuentes»: CFDI, SAP, banco y Data Stage convergen en un nodo validado.

Define la geometría una sola vez y genera:
  - app/src/main/res/drawable/ic_launcher_background.xml   fondo del ícono adaptable (vector)
  - app/src/main/res/drawable/ic_launcher_foreground.xml   motivo del ícono adaptable (vector)
  - app/src/main/res/drawable/ic_launcher_monochrome.xml   capa para íconos temáticos (Android 13+)
  - arte/icono.svg                                         maestro de 1024 x 1024 (con brillos)
  - app/src/main/res/mipmap-*/ic_launcher(_round).png      PNG para Android 7.x (sin íconos adaptables)
  - app/src/main/ic_launcher-playstore.png                 512 x 512 para la tienda

Los PNG se dibujan con Microsoft Edge en modo headless (no requiere bibliotecas de Python).
Uso, desde la carpeta mobile/:   python arte/generar_icono.py
"""
import math
import os
import subprocess
import tempfile

MOVIL = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(MOVIL, "app", "src", "main", "res")

# ----------------------------------------------------------------------------- geometría
# Diseño en un lienzo de 1024 (lo visible del ícono). En el ícono adaptable, lo visible son
# los 72 dp centrales de un lienzo de 108 dp; la zona segura es un círculo de 66 dp.
C = 512
AMPLIA = 1.17          # el motivo se agranda para que se lea a tamaño real en el cajón de apps
AMPLIA_MONO = 0.95     # la capa monocroma va más chica, como piden los íconos temáticos
D = 236                # distancia del centro a cada mosaico
MITAD, RADIO = 78, 40  # mosaicos de 156 x 156 con esquinas de 40
NODO = 112
TRAZO_GLIFO = 12

MOSAICOS = [  # (dx, dy, color, glifo)
    (0, -D, "#0EA5E9", "cfdi"),
    (D, 0, "#8B5CF6", "erp"),
    (0, D, "#14B8A6", "banco"),
    (-D, 0, "#F59E0B", "datastage"),
]

# Glifos relativos al centro del mosaico: (cerrado, puntos)
GLIFOS = {
    "cfdi": [(True, [(-30, -42), (14, -42), (34, -22), (34, 42), (-30, 42)]), (False, [(-14, -6), (18, -6)]), (False, [(-14, 16), (18, 16)])],
    "erp": [(False, [(-36, 38), (-36, 0)]), (False, [(-12, 38), (-12, -30)]), (False, [(12, 38), (12, -12)]), (False, [(36, 38), (36, -40)])],
    "banco": [(True, [(-42, -14), (0, -42), (42, -14)])] + [(False, [(x, -4), (x, 30)]) for x in (-32, -11, 11, 32)] + [(False, [(-44, 40), (44, 40)])],
    "datastage": [(True, [(-40, 6), (40, 6), (28, 38), (-28, 38)]), (False, [(-22, 6), (-22, -26), (22, -26), (22, 6)]), (False, [(0, -26), (0, -44)])],
}
PALOMITA = [(-50, 2), (-12, 40), (54, -30)]  # relativa al centro del nodo
PUNTOS = (132, 148)                          # puntos entre cada mosaico y el nodo


class Escala:
    """Transforma coordenadas del diseño (1024) a otro lienzo."""

    def __init__(self, centro, factor):
        self.c, self.f = centro, factor

    def p(self, x, y):
        return self.c + (x - C) * self.f, self.c + (y - C) * self.f

    def n(self, v):
        return v * self.f


def fmt(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def ruta_poligono(e, cx, cy, cerrado, puntos):
    pts = [e.p(cx + x, cy + y) for x, y in puntos]
    d = "M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in pts)
    return d + (" Z" if cerrado else "")


def ruta_circulo(e, cx, cy, r):
    x, y = e.p(cx, cy)
    rr = e.n(r)
    return f"M{fmt(x - rr)},{fmt(y)} a{fmt(rr)},{fmt(rr)} 0 1,0 {fmt(2 * rr)},0 a{fmt(rr)},{fmt(rr)} 0 1,0 {fmt(-2 * rr)},0 Z"


def ruta_redondeado(e, cx, cy, mitad, radio, solo_arriba=False):
    x0, y0 = e.p(cx - mitad, cy - mitad)
    x1, y1 = e.p(cx + mitad, cy + mitad)
    r = e.n(radio)
    if solo_arriba:
        ym = (y0 + y1) / 2
        return (f"M{fmt(x0)},{fmt(ym)} V{fmt(y0 + r)} A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x0 + r)},{fmt(y0)} "
                f"H{fmt(x1 - r)} A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x1)},{fmt(y0 + r)} V{fmt(ym)} Z")
    return (f"M{fmt(x0 + r)},{fmt(y0)} H{fmt(x1 - r)} A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x1)},{fmt(y0 + r)} "
            f"V{fmt(y1 - r)} A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x1 - r)},{fmt(y1)} H{fmt(x0 + r)} "
            f"A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x0)},{fmt(y1 - r)} V{fmt(y0 + r)} A{fmt(r)},{fmt(r)} 0 0,1 {fmt(x0 + r)},{fmt(y0)} Z")


# ----------------------------------------------------------------------------- vectores de Android
CABECERA = ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generado por arte/generar_icono.py: edita el script, no este archivo. -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    xmlns:aapt="http://schemas.android.com/aapt"\n'
            '    android:width="108dp" android:height="108dp"\n'
            '    android:viewportWidth="108" android:viewportHeight="108">\n')


def relleno_radial(d, cx, cy, r, paradas):
    items = "\n".join(f'                <item android:offset="{o}" android:color="{c}" />' for o, c in paradas)
    return (f'    <path android:pathData="{d}">\n'
            f'        <aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="radial" android:centerX="{fmt(cx)}" android:centerY="{fmt(cy)}" android:gradientRadius="{fmt(r)}">\n'
            f'{items}\n'
            f'            </gradient>\n'
            f'        </aapt:attr>\n'
            f'    </path>\n')


def relleno(d, color):
    return f'    <path android:fillColor="{color}" android:pathData="{d}" />\n'


def trazo(d, color, ancho):
    return (f'    <path android:pathData="{d}" android:strokeColor="{color}" android:strokeWidth="{fmt(ancho)}"\n'
            f'        android:strokeLineCap="round" android:strokeLineJoin="round" />\n')


def fondo_xml():
    v = 72 / 1024  # el diseño de 1024 ocupa los 72 dp visibles, desde el dp 18
    a = lambda x: 18 + x * v
    todo = "M0,0 H108 V108 H0 Z"
    s = CABECERA
    s += relleno_radial(todo, 54, a(430), 62, [("0", "#FF0B3B2E"), ("0.55", "#FF06121F"), ("1", "#FF020617")])
    s += relleno_radial(todo, a(250), a(230), 30, [("0", "#5910B981"), ("1", "#0010B981")])
    s += relleno_radial(todo, a(820), a(840), 28, [("0", "#38F59E0B"), ("1", "#00F59E0B")])
    s += relleno_radial(todo, a(860), a(200), 20, [("0", "#2438BDF8"), ("1", "#0038BDF8")])
    return s + "</vector>\n"


def frente_xml():
    e = Escala(54, 72 / 1024 * AMPLIA)
    s = CABECERA
    for r in PUNTOS:
        for dx, dy in ((r, 0), (-r, 0), (0, r), (0, -r)):
            s += relleno(ruta_circulo(e, C + dx, C + dy, 6), "#B394A3B8")
    for dx, dy, color, glifo in MOSAICOS:
        cx, cy = C + dx, C + dy
        s += relleno(ruta_redondeado(e, cx, cy, MITAD, RADIO), "#FF" + color[1:])
        s += relleno(ruta_redondeado(e, cx, cy, MITAD, RADIO, solo_arriba=True), "#1FFFFFFF")
        for cerrado, puntos in GLIFOS[glifo]:
            s += trazo(ruta_poligono(e, cx, cy, cerrado, puntos), "#FFFFFFFF", e.n(TRAZO_GLIFO))
    gx, gy = e.p(C, C)
    s += relleno_radial(ruta_circulo(e, C, C, 160), gx, gy, e.n(160), [("0", "#9910B981"), ("0.7", "#3310B981"), ("1", "#0010B981")])
    s += relleno_radial(ruta_circulo(e, C, C, NODO), gx - e.n(0.2 * NODO), gy - e.n(0.3 * NODO), e.n(1.4 * NODO), [("0", "#FF6EE7B7"), ("1", "#FF047857")])
    s += trazo(ruta_poligono(e, C, C, False, PALOMITA), "#FFFFFFFF", e.n(30))
    return s + "</vector>\n"


def monocromo_xml():
    # Una sola tinta: el sistema la colorea con el tema del fondo de pantalla
    e = Escala(54, 72 / 1024 * AMPLIA_MONO)
    s = CABECERA
    for dx, dy, _, _ in MOSAICOS:
        s += relleno(ruta_redondeado(e, C + dx, C + dy, MITAD, RADIO), "#FFFFFFFF")
    for r in PUNTOS:
        for dx, dy in ((r, 0), (-r, 0), (0, r), (0, -r)):
            s += relleno(ruta_circulo(e, C + dx, C + dy, 6), "#FFFFFFFF")
    s += trazo(ruta_circulo(e, C, C, NODO - 14), "#FFFFFFFF", e.n(26))
    s += trazo(ruta_poligono(e, C, C, False, PALOMITA), "#FFFFFFFF", e.n(30))
    return s + "</vector>\n"


# ----------------------------------------------------------------------------- SVG maestro
def svg_maestro():
    e = Escala(C, AMPLIA)
    partes = ["""<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">
<defs>
  <radialGradient id="noche" cx="50%" cy="42%" r="75%"><stop offset="0" stop-color="#0B3B2E"/><stop offset=".55" stop-color="#06121F"/><stop offset="1" stop-color="#020617"/></radialGradient>
  <radialGradient id="nodo" cx="40%" cy="35%" r="70%"><stop offset="0" stop-color="#6EE7B7"/><stop offset="1" stop-color="#047857"/></radialGradient>
  <filter id="difuso" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="70"/></filter>
  <filter id="brillo" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="20"/></filter>
</defs>
<rect width="1024" height="1024" fill="url(#noche)"/>
<circle cx="250" cy="230" r="230" fill="#10B981" opacity=".35" filter="url(#difuso)"/>
<circle cx="820" cy="840" r="220" fill="#F59E0B" opacity=".22" filter="url(#difuso)"/>
<circle cx="860" cy="200" r="160" fill="#38BDF8" opacity=".14" filter="url(#difuso)"/>"""]
    for r in PUNTOS:
        for dx, dy in ((r, 0), (-r, 0), (0, r), (0, -r)):
            partes.append(f'<path d="{ruta_circulo(e, C + dx, C + dy, 6)}" fill="#94A3B8" fill-opacity=".7"/>')
    for dx, dy, color, glifo in MOSAICOS:
        cx, cy = C + dx, C + dy
        partes.append(f'<path d="{ruta_redondeado(e, cx, cy, MITAD, RADIO)}" fill="{color}"/>')
        partes.append(f'<path d="{ruta_redondeado(e, cx, cy, MITAD, RADIO, True)}" fill="#FFFFFF" fill-opacity=".12"/>')
        for cerrado, puntos in GLIFOS[glifo]:
            partes.append(f'<path d="{ruta_poligono(e, cx, cy, cerrado, puntos)}" fill="none" stroke="#FFFFFF" stroke-width="{fmt(e.n(TRAZO_GLIFO))}" stroke-linecap="round" stroke-linejoin="round"/>')
    partes.append(f'<path d="{ruta_circulo(e, C, C, NODO + 6)}" fill="#10B981" opacity=".7" filter="url(#brillo)"/>')
    partes.append(f'<path d="{ruta_circulo(e, C, C, NODO)}" fill="url(#nodo)"/>')
    partes.append(f'<path d="{ruta_poligono(e, C, C, False, PALOMITA)}" fill="none" stroke="#FFFFFF" stroke-width="{fmt(e.n(30))}" stroke-linecap="round" stroke-linejoin="round"/>')
    partes.append("</svg>")
    return "\n".join(partes)


# ----------------------------------------------------------------------------- PNG con Edge
EDGE = [r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe", r"C:\Program Files\Microsoft\Edge\Application\msedge.exe"]
DENSIDADES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def dibujar_png(edge, svg, destino, px, forma):
    radio = {"cuadro": "0", "redondeado": "22%", "circulo": "50%"}[forma]
    html = (f'<!doctype html><html><head><meta charset="utf-8"><style>html,body{{margin:0;background:transparent;overflow:hidden}}'
            f'div{{width:1024px;height:1024px;border-radius:{radio};overflow:hidden}}</style></head><body><div>{svg}</div></body></html>')
    with tempfile.TemporaryDirectory() as tmp:
        pagina = os.path.join(tmp, "icono.html")
        open(pagina, "w", encoding="utf-8").write(html)
        subprocess.run([edge, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--default-background-color=00000000",
                        f"--force-device-scale-factor={px / 1024}", "--window-size=1024,1024",
                        f"--screenshot={destino}", "file:///" + pagina.replace("\\", "/")],
                       check=True, capture_output=True, timeout=120)


def main():
    escribir = lambda ruta, txt: open(ruta, "w", encoding="utf-8", newline="\n").write(txt)
    escribir(os.path.join(RES, "drawable", "ic_launcher_background.xml"), fondo_xml())
    escribir(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), frente_xml())
    escribir(os.path.join(RES, "drawable", "ic_launcher_monochrome.xml"), monocromo_xml())
    svg = svg_maestro()
    escribir(os.path.join(MOVIL, "arte", "icono.svg"), svg)
    print("Vectores y SVG maestro generados.")

    edge = next((r for r in EDGE if os.path.exists(r)), None)
    if not edge:
        print("No se encontró Microsoft Edge: los PNG no se regeneraron.")
        return
    for carpeta, px in DENSIDADES.items():
        dir_ = os.path.join(RES, f"mipmap-{carpeta}")
        for viejo in ("ic_launcher.webp", "ic_launcher_round.webp"):
            if os.path.exists(os.path.join(dir_, viejo)):
                os.remove(os.path.join(dir_, viejo))
        dibujar_png(edge, svg, os.path.join(dir_, "ic_launcher.png"), px, "redondeado")
        dibujar_png(edge, svg, os.path.join(dir_, "ic_launcher_round.png"), px, "circulo")
    dibujar_png(edge, svg, os.path.join(MOVIL, "app", "src", "main", "ic_launcher-playstore.png"), 512, "cuadro")
    print("PNG generados (mipmap-* y playstore).")


if __name__ == "__main__":
    main()
