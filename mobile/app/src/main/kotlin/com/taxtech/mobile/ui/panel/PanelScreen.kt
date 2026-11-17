package com.taxtech.mobile.ui.panel

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.Usuario
import com.taxtech.mobile.ui.accesos.AccesosScreen
import com.taxtech.mobile.ui.cargas.CargasScreen
import com.taxtech.mobile.ui.comparativo.ComparativoScreen
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.DialogoConfirmar
import com.taxtech.mobile.ui.comun.FondoAurora
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.Pastilla
import com.taxtech.mobile.ui.comun.SelectorMes
import com.taxtech.mobile.ui.comun.nombreDeCorreo
import com.taxtech.mobile.ui.conciliar.ConciliarScreen
import com.taxtech.mobile.ui.explorador.ExploradorScreen
import com.taxtech.mobile.ui.login.ContrasenaViewModel
import com.taxtech.mobile.ui.login.FormularioContrasena
import com.taxtech.mobile.ui.resumen.ResumenScreen
import com.taxtech.mobile.ui.tablero.TableroScreen
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Marca
import kotlinx.serialization.Serializable

@Serializable data object DestResumen : NavKey
@Serializable data object DestTablero : NavKey
@Serializable data object DestConciliar : NavKey
@Serializable data object DestComparativo : NavKey
@Serializable data object DestExplorador : NavKey
@Serializable data object DestCargas : NavKey
@Serializable data object DestAccesos : NavKey

/** Las pestañas del panel. `usaMes`: muestran el selector de periodo del encabezado. */
enum class Seccion(val clave: NavKey, val titulo: String, val ceja: String, val icono: ImageVector, val usaMes: Boolean, val enDock: Boolean) {
  RESUMEN(DestResumen, "Resumen", "Panorama", Iconos.Inicio, false, true),
  TABLERO(DestTablero, "Tablero de IVA", "Flujo de efectivo", Iconos.Grafica, true, true),
  CONCILIAR(DestConciliar, "Conciliar", "Reglas C1 a C10", Iconos.Cruce, true, true),
  COMPARATIVO(DestComparativo, "Comparativo", "Corte contra hoy", Iconos.Balanza, true, true),
  EXPLORADOR(DestExplorador, "Explorador", "Tablas y registros", Iconos.Tabla, false, false),
  CARGAS(DestCargas, "Cargas", "Fuentes de datos", Iconos.Subir, false, false),
  ACCESOS(DestAccesos, "Accesos", "Usuarios y empresas", Iconos.Escudo, false, false);

  companion object {
    fun de(clave: NavKey?): Seccion = entries.firstOrNull { it.clave == clave } ?: RESUMEN
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(usuario: Usuario, panel: PanelViewModel) {
  val c = LocalContenedor.current
  val pila = rememberNavBackStack(DestResumen)
  val seccion = Seccion.de(pila.lastOrNull())
  val avisos = remember { SnackbarHostState() }
  val estado by panel.estado.collectAsStateWithLifecycle()
  val mes by panel.mes.collectAsStateWithLifecycle()
  var verMas by rememberSaveable { mutableStateOf(false) }
  var verContrasena by rememberSaveable { mutableStateOf(false) }
  var confirmarSalida by remember { mutableStateOf(false) }
  val haptico = LocalHapticFeedback.current

  LaunchedEffect(Unit) { c.avisos.mensajes.collect { avisos.showSnackbar(it) } }

  val ir: (Seccion) -> Unit = { s ->
    if (pila.lastOrNull() != s.clave) {
      haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
      while (pila.size > 1) pila.removeAt(pila.lastIndex)
      if (s != Seccion.RESUMEN) pila.add(s.clave)
    }
  }

  FondoAurora {
    Column(Modifier.fillMaxSize()) {
      Encabezado(seccion, usuario, panel, estado, mes, alAbrirMas = { verMas = true })
      NavDisplay(
        backStack = pila,
        modifier = Modifier.weight(1f),
        onBack = { if (pila.size > 1) pila.removeAt(pila.lastIndex) },
        transitionSpec = { (fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 30 }) togetherWith fadeOut(tween(140)) },
        popTransitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
        entryProvider = entryProvider {
          entry<DestResumen> { ResumenScreen(panel, ir) }
          entry<DestTablero> { TableroScreen(panel) }
          entry<DestConciliar> { ConciliarScreen(panel) }
          entry<DestComparativo> { ComparativoScreen(panel) }
          entry<DestExplorador> { ExploradorScreen(panel) }
          entry<DestCargas> { CargasScreen(panel) }
          entry<DestAccesos> { AccesosScreen(panel) }
        },
      )
    }
    SnackbarHost(avisos, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 88.dp)) { d ->
      Snackbar(d, shape = RoundedCornerShape(16.dp), containerColor = MaterialTheme.colorScheme.inverseSurface)
    }
    Dock(
      seleccion = if (seccion.enDock) seccion else null,
      alElegir = ir,
      alMas = { verMas = true },
      modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
    )
  }

  if (verMas) {
    ModalBottomSheet(onDismissRequest = { verMas = false }, containerColor = MaterialTheme.colorScheme.surface) {
      HojaMas(
        usuario = usuario,
        servidor = c.sesion.servidor,
        actual = seccion,
        alElegir = { verMas = false; ir(it) },
        alCambiarContrasena = { verMas = false; verContrasena = true },
        alSalir = { verMas = false; confirmarSalida = true },
      )
    }
  }

  if (verContrasena) {
    val vm: ContrasenaViewModel = viewModel(key = "contrasena") { ContrasenaViewModel(c) }
    val e by vm.estado.collectAsStateWithLifecycle()
    LaunchedEffect(e.listo) { if (e.listo) { verContrasena = false; vm.reiniciar() } }
    AlertDialog(
      onDismissRequest = { verContrasena = false; vm.reiniciar() },
      title = { Text("Cambiar contraseña") },
      text = { FormularioContrasena(e.cargando, e.error, vm::enviar) },
      confirmButton = {},
      dismissButton = { TextButton(onClick = { verContrasena = false; vm.reiniciar() }) { Text("Cancelar") } },
    )
  }

  if (confirmarSalida) {
    DialogoConfirmar(
      titulo = "Cerrar sesión",
      texto = "Se borrará la sesión de este dispositivo. Para volver necesitarás tu correo y contraseña.",
      confirmar = "Cerrar sesión",
      alConfirmar = { c.sesion.salir() },
      alCerrar = { confirmarSalida = false },
      peligro = true,
    )
  }
}

@Composable
private fun Encabezado(seccion: Seccion, usuario: Usuario, panel: PanelViewModel, estado: PanelViewModel.Estado, mes: String, alAbrirMas: () -> Unit) {
  var menuEmpresas by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Ceja(seccion.ceja)
        Text(seccion.titulo, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
      }
      // Avatar con la inicial: abre la hoja con el perfil, las demás pestañas y la salida
      Box(
        Modifier.size(42.dp).clip(CircleShape)
          .background(Brush.linearGradient(listOf(Marca.Esmeralda500, Marca.Oro500)))
          .clickable(onClickLabel = "Abrir menú de la cuenta", onClick = alAbrirMas),
        contentAlignment = Alignment.Center,
      ) {
        Text(nombreDeCorreo(usuario.email).take(1), color = Color.White, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      val empresa = estado.empresa
      Box(Modifier.weight(1f, fill = false)) {
        Row(
          Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(enabled = estado.empresas.size > 1) { menuEmpresas = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Icon(Iconos.Edificio, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(
            empresa?.razonSocial ?: if (estado.cargando) "Cargando empresas…" else "Sin empresas asignadas",
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 170.dp),
          )
          if (estado.empresas.size > 1) Icon(Iconos.Abajo, "Cambiar de empresa", modifier = Modifier.padding(start = 4.dp).size(16.dp))
        }
        DropdownMenu(expanded = menuEmpresas, onDismissRequest = { menuEmpresas = false }) {
          estado.empresas.forEach { e ->
            DropdownMenuItem(
              text = {
                Column {
                  Text(e.razonSocial, style = MaterialTheme.typography.bodyMedium)
                  Text(listOfNotNull(e.rfc, e.grupo).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              },
              leadingIcon = if (e.id == estado.empresaId) ({ Icon(Iconos.Palomita, null, tint = MaterialTheme.colorScheme.primary) }) else null,
              onClick = { menuEmpresas = false; panel.elegirEmpresa(e.id) },
            )
          }
        }
      }
      if (seccion.usaMes) SelectorMes(mes, panel::elegirMes, maximo = panel.mesActual())
    }
  }
}

/** Dock flotante de vidrio con un indicador que se desliza al elegir pestaña. */
@Composable
private fun Dock(seleccion: Seccion?, alElegir: (Seccion) -> Unit, alMas: () -> Unit, modifier: Modifier = Modifier) {
  val c = LocalColoresTax.current
  val items = Seccion.entries.filter { it.enDock }
  val indice = seleccion?.let { items.indexOf(it) } ?: items.size
  BoxWithConstraints(
    modifier.widthIn(max = 520.dp).fillMaxWidth().height(66.dp).clip(RoundedCornerShape(26.dp))
      .background(if (c.oscuro) Color(0xFF0B1222) else Color.White)
      .border(1.dp, c.bordeVidrio, RoundedCornerShape(26.dp)),
  ) {
    val ancho = maxWidth / (items.size + 1)
    val x by animateDpAsState(ancho * indice, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "dock")
    Box(
      Modifier.offset { IntOffset(x.roundToPx(), 0) }.width(ancho).fillMaxHeight().padding(6.dp).clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)))),
    )
    Row(Modifier.fillMaxSize()) {
      items.forEachIndexed { i, s -> ItemDock(s.icono, s.titulo.substringBefore(" de "), i == indice, Modifier.width(ancho)) { alElegir(s) } }
      ItemDock(Iconos.Mosaico, "Más", indice == items.size, Modifier.width(ancho), alMas)
    }
  }
}

@Composable
private fun ItemDock(icono: ImageVector, texto: String, activo: Boolean, modifier: Modifier, onClick: () -> Unit) {
  val color = if (activo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
  Column(
    modifier.fillMaxHeight().clip(RoundedCornerShape(20.dp))
      .semantics { selected = activo }
      .clickable(role = Role.Tab, onClick = onClick),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(icono, null, tint = color, modifier = Modifier.size(22.dp))
    Spacer(Modifier.height(3.dp))
    Text(texto, style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (activo) FontWeight.SemiBold else FontWeight.Normal), color = color, maxLines = 1)
  }
}

@Composable
private fun HojaMas(
  usuario: Usuario,
  servidor: String,
  actual: Seccion,
  alElegir: (Seccion) -> Unit,
  alCambiarContrasena: () -> Unit,
  alSalir: () -> Unit,
) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Marca.Esmeralda500, Marca.Oro500))),
        contentAlignment = Alignment.Center,
      ) { Text(nombreDeCorreo(usuario.email).take(1), color = Color.White, style = MaterialTheme.typography.titleLarge) }
      Spacer(Modifier.width(14.dp))
      Column(Modifier.weight(1f)) {
        Text(usuario.email, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Pastilla(usuario.rol.nombre, if (usuario.rol.esAdmin) Marca.Oro500 else MaterialTheme.colorScheme.primary, punto = true)
          Pastilla(servidor.removePrefix("http://").removePrefix("https://"), MaterialTheme.colorScheme.onSurfaceVariant, mono = true)
        }
      }
    }
    Spacer(Modifier.height(20.dp))
    Ceja("Más herramientas", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      listOf(
        Triple(Seccion.EXPLORADOR, "Consulta y corrige registros", Marca.Cielo400),
        Triple(Seccion.CARGAS, "Sube CFDI, SAP, bancos y Data Stage", Marca.Esmeralda400),
        Triple(Seccion.ACCESOS, if (usuario.rol.esAdmin) "Usuarios, empresas y grupos" else "Tu perfil y permisos", Marca.Oro400),
      ).forEach { (s, texto, color) ->
        Column(
          Modifier.weight(1f).clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = if (s == actual) 0.22f else 0.10f))
            .clickable { alElegir(s) }
            .padding(14.dp),
        ) {
          Icon(s.icono, null, tint = color, modifier = Modifier.size(24.dp))
          Spacer(Modifier.height(10.dp))
          Text(s.titulo, style = MaterialTheme.typography.titleSmall)
          Text(texto, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        }
      }
    }
    Spacer(Modifier.height(18.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    FilaMenu(Iconos.Llave, "Cambiar contraseña", onClick = alCambiarContrasena)
    FilaMenu(Iconos.Salir, "Cerrar sesión", color = LocalColoresTax.current.peligro, onClick = alSalir)
  }
}

@Composable
private fun FilaMenu(icono: ImageVector, texto: String, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
  Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icono, null, tint = color, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(14.dp))
    Text(texto, style = MaterialTheme.typography.bodyLarge, color = color)
  }
}
