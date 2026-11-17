package com.taxtech.mobile.ui.accesos

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.Accesos
import com.taxtech.mobile.data.Empresa
import com.taxtech.mobile.data.REGIMENES_FISCALES
import com.taxtech.mobile.data.Rol
import com.taxtech.mobile.data.Usuario
import com.taxtech.mobile.data.UsuarioAcceso
import com.taxtech.mobile.data.textoVisible
import com.taxtech.mobile.ui.comun.AvisoError
import com.taxtech.mobile.ui.comun.BotonSecundario
import com.taxtech.mobile.ui.comun.Cargando
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.DialogoConfirmar
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.Pastilla
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.TituloTarjeta
import com.taxtech.mobile.ui.comun.Vacio
import com.taxtech.mobile.ui.comun.fechaHora
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.theme.LocalColoresTax
import com.taxtech.mobile.ui.theme.Marca
import com.taxtech.mobile.ui.theme.Mono
import kotlinx.serialization.json.JsonObject

private enum class Pestana(val titulo: String) { USUARIOS("Usuarios"), EMPRESAS("Empresas"), GRUPOS("Grupos"), BITACORA("Bitácora") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccesosScreen(panel: PanelViewModel) {
  val c = LocalContenedor.current
  val vm: AccesosViewModel = viewModel(key = "accesos") { AccesosViewModel(c) }
  val p by panel.estado.collectAsStateWithLifecycle()
  val e by vm.estado.collectAsStateWithLifecycle()
  val yo = panel.usuario
  var pestana by rememberSaveable { mutableStateOf(Pestana.USUARIOS) }
  LaunchedEffect(yo.rol) { if (yo.rol.esAdmin) vm.cargar() }
  LaunchedEffect(pestana) { if (pestana == Pestana.BITACORA && e.bitacora.isEmpty()) vm.cargarBitacora(reiniciar = true) }

  var nuevoUsuario by remember { mutableStateOf(false) }
  var nuevaEmpresa by remember { mutableStateOf(false) }
  var nuevoGrupo by remember { mutableStateOf(false) }
  var empresasDe by remember { mutableStateOf<UsuarioAcceso?>(null) }
  var restablecerA by remember { mutableStateOf<UsuarioAcceso?>(null) }
  var grupoDe by remember { mutableStateOf<Empresa?>(null) }
  var desactivar by remember { mutableStateOf<UsuarioAcceso?>(null) }

  PullToRefreshBox(
    isRefreshing = e.cargando && e.datos != null,
    onRefresh = { if (yo.rol.esAdmin) { vm.cargar(forzar = true); if (pestana == Pestana.BITACORA) vm.cargarBitacora(reiniciar = true) } },
    modifier = Modifier.fillMaxSize(),
  ) {
    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      item { MiAcceso(yo, p.empresas) }
      if (!yo.rol.esAdmin) {
        item {
          Tarjeta {
            Vacio("Administración de accesos", "Solo un Admin da de alta usuarios, empresas y grupos, y consulta la bitácora. Si necesitas otra empresa o perfil, pídeselo a un administrador.", Iconos.Escudo)
          }
        }
        return@LazyColumn
      }
      item {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          Pestana.entries.forEachIndexed { i, t ->
            SegmentedButton(selected = pestana == t, onClick = { pestana = t }, shape = SegmentedButtonDefaults.itemShape(i, Pestana.entries.size)) {
              Text(t.titulo, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
          }
        }
      }
      if (e.error != null) item { AvisoError(e.error!!) { vm.cargar(forzar = true) } }
      val d = e.datos
      if (d == null && e.cargando) item { Cargando() }
      if (d != null) {
        when (pestana) {
          Pestana.USUARIOS -> {
            item {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Ceja("${d.usuarios.size} usuarios", modifier = Modifier.weight(1f).padding(start = 4.dp))
                BotonSecundario("Nuevo usuario", { nuevoUsuario = true }, icono = Iconos.Mas)
              }
            }
            items(d.usuarios, key = { it.id }) { u ->
              TarjetaUsuario(
                u, d, esYo = u.id == yo.id || u.email == yo.email, trabajando = e.trabajando,
                alRol = { r -> vm.cambiarRol(u.id, r) },
                alActivo = { a -> if (a) vm.cambiarActivo(u.id, true) else desactivar = u },
                alEmpresas = { empresasDe = u },
                alRestablecer = { restablecerA = u },
              )
            }
          }
          Pestana.EMPRESAS -> {
            item {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Ceja("${d.empresas.size} empresas", modifier = Modifier.weight(1f).padding(start = 4.dp))
                BotonSecundario("Nueva empresa", { nuevaEmpresa = true }, icono = Iconos.Mas)
              }
            }
            items(d.empresas, key = { it.id }) { em ->
              Tarjeta(onClick = { grupoDe = em }, relleno = PaddingValues(14.dp)) {
                Text(em.razonSocial, style = MaterialTheme.typography.titleSmall)
                Text(em.rfc, style = Mono.merge(MaterialTheme.typography.bodySmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Pastilla(em.grupo ?: "Sin grupo", if (em.grupo != null) Marca.Oro500 else MaterialTheme.colorScheme.onSurfaceVariant)
                  em.sociedadSap?.let { Pastilla("SAP $it", MaterialTheme.colorScheme.tertiary, mono = true) }
                  val usuarios = d.usuarios.count { em.id in it.empresas }
                  Pastilla("$usuarios usuario${if (usuarios == 1) "" else "s"}", MaterialTheme.colorScheme.primary)
                }
              }
            }
          }
          Pestana.GRUPOS -> {
            item {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Ceja("${d.grupos.size} grupos empresariales", modifier = Modifier.weight(1f).padding(start = 4.dp))
                BotonSecundario("Nuevo grupo", { nuevoGrupo = true }, icono = Iconos.Mas)
              }
            }
            item {
              Text(
                "Un Lector solo puede tener empresas de un mismo grupo; un Editor puede tener de varios. Una empresa sin grupo cuenta como grupo propio.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
              )
            }
            if (d.grupos.isEmpty()) item { Tarjeta { Vacio("Sin grupos", "Crea un grupo para reunir empresas del mismo corporativo.", Iconos.Edificio) } }
            items(d.grupos, key = { it.id }) { g ->
              Tarjeta(relleno = PaddingValues(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Iconos.Edificio, null, tint = Marca.Oro500, modifier = Modifier.size(20.dp))
                  Spacer(Modifier.width(10.dp))
                  Text(g.nombre, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                  Pastilla("${g.empresas} empresa${if (g.empresas == 1L) "" else "s"}", Marca.Oro500)
                }
                val nombres = d.empresas.filter { it.grupoId == g.id }.joinToString { it.razonSocial }
                if (nombres.isNotEmpty()) Text(nombres, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
              }
            }
          }
          Pestana.BITACORA -> {
            item { Ceja("Cambios registrados: ${e.totalBitacora}", modifier = Modifier.padding(start = 4.dp)) }
            items(e.bitacora, key = { it["bitacora_id"].textoVisible() ?: it.hashCode().toString() }) { b -> EntradaBitacora(b) }
            if (e.cargandoBitacora) item { Cargando() }
            if (!e.cargandoBitacora && e.bitacora.size < e.totalBitacora) {
              item { BotonSecundario("Cargar más", { vm.cargarBitacora() }, icono = Iconos.Abajo, modifier = Modifier.fillMaxWidth()) }
            }
          }
        }
      }
    }
  }

  val d = e.datos
  if (nuevoUsuario && d != null) DialogoUsuario(d, e.trabajando, { nuevoUsuario = false }) { email, rol, pass, emp -> vm.crearUsuario(email, rol, pass, emp) { nuevoUsuario = false } }
  empresasDe?.let { u -> if (d != null) DialogoEmpresas(u, d, { empresasDe = null }) { ids -> vm.asignarEmpresas(u.id, ids) { empresasDe = null } } }
  restablecerA?.let { u -> DialogoRestablecer(u, { restablecerA = null }) { pass -> vm.restablecer(u.id, pass) { restablecerA = null } } }
  grupoDe?.let { em -> if (d != null) DialogoGrupo(em, d, { grupoDe = null }) { g -> vm.cambiarGrupo(em.id, g) { grupoDe = null } } }
  if (nuevaEmpresa && d != null) DialogoEmpresa(d, { nuevaEmpresa = false }) { rfc, razon, reg, soc, g -> vm.crearEmpresa(rfc, razon, reg, soc, g) { nuevaEmpresa = false } }
  if (nuevoGrupo) DialogoTexto("Nuevo grupo empresarial", "Nombre del grupo", { nuevoGrupo = false }) { n -> vm.crearGrupo(n) { nuevoGrupo = false } }
  desactivar?.let { u ->
    DialogoConfirmar("Desactivar usuario", "${u.email} ya no podrá entrar. Puedes reactivarlo cuando quieras.", "Desactivar", { vm.cambiarActivo(u.id, false) }, { desactivar = null }, peligro = true)
  }
}

/** Perfil propio y lo que permite cada rol, con el suyo resaltado. */
@Composable
private fun MiAcceso(u: Usuario, empresas: List<Empresa>) {
  val colores = LocalColoresTax.current
  val permisos = listOf(
    "Consultar resumen, tablero, comparativo y explorador" to Rol.LECTOR,
    "Ejecutar conciliaciones y el cierre del mes" to Rol.EDITOR,
    "Revisar partidas y registrar cortes" to Rol.EDITOR,
    "Subir archivos y editar registros" to Rol.EDITOR,
    "Borrar registros del explorador" to Rol.ADMIN,
    "Administrar usuarios, empresas, grupos y bitácora" to Rol.ADMIN,
  )
  Tarjeta(acento = if (u.rol.esAdmin) Marca.Oro500 else MaterialTheme.colorScheme.primary) {
    TituloTarjeta("Tu acceso", u.email, Iconos.Escudo) { Pastilla(u.rol.nombre, if (u.rol.esAdmin) Marca.Oro500 else MaterialTheme.colorScheme.primary, punto = true) }
    Spacer(Modifier.height(10.dp))
    Row {
      Spacer(Modifier.weight(1f))
      Rol.entries.forEach { r ->
        Text(r.nombre.take(1), style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (r == u.rol) FontWeight.Bold else FontWeight.Normal),
          color = if (r == u.rol) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(30.dp))
      }
    }
    permisos.forEach { (texto, minimo) ->
      Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val propio = u.rol.ordinal >= minimo.ordinal
        Text(texto, style = MaterialTheme.typography.bodySmall, color = if (propio) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Rol.entries.forEach { r ->
          Box(Modifier.width(30.dp)) {
            if (r.ordinal >= minimo.ordinal) Icon(Iconos.Palomita, null, tint = if (r == u.rol) colores.exito else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
          }
        }
      }
    }
    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(
      if (u.rol.esAdmin) "Como Admin ves todas las empresas (${empresas.size})." else "Empresas asignadas: " + empresas.joinToString { it.razonSocial }.ifEmpty { "ninguna" },
      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun TarjetaUsuario(
  u: UsuarioAcceso,
  d: Accesos,
  esYo: Boolean,
  trabajando: Boolean,
  alRol: (Rol) -> Unit,
  alActivo: (Boolean) -> Unit,
  alEmpresas: () -> Unit,
  alRestablecer: () -> Unit,
) {
  val colores = LocalColoresTax.current
  Tarjeta(Modifier.animateContentSize(), acento = if (!u.activo) colores.peligro else null, relleno = PaddingValues(14.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(u.email + if (esYo) " (tú)" else "", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
          if (!u.activo) Pastilla("Inactivo", colores.peligro)
          if (u.debeCambiarContrasena) Pastilla("Debe cambiar contraseña", colores.aviso)
        }
      }
      Switch(checked = u.activo, onCheckedChange = alActivo, enabled = !esYo && !trabajando)
    }
    Spacer(Modifier.height(10.dp))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
      Rol.entries.forEachIndexed { i, r ->
        SegmentedButton(selected = u.rol == r, onClick = { if (u.rol != r) alRol(r) }, enabled = !esYo && !trabajando, shape = SegmentedButtonDefaults.itemShape(i, Rol.entries.size)) {
          Text(r.nombre, style = MaterialTheme.typography.labelMedium)
        }
      }
    }
    Spacer(Modifier.height(8.dp))
    Text(
      if (u.rol.esAdmin) "Ve todas las empresas" else d.empresas.filter { it.id in u.empresas }.joinToString { it.razonSocial }.ifEmpty { "Sin empresas asignadas" },
      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row {
      if (!u.rol.esAdmin) TextButton(onClick = alEmpresas, enabled = !trabajando) { Text("Empresas") }
      if (!esYo) TextButton(onClick = alRestablecer, enabled = !trabajando) { Text("Restablecer contraseña") }
    }
  }
}

@Composable
private fun EntradaBitacora(b: JsonObject) {
  var abierta by remember { mutableStateOf(false) }
  val accion = b["accion"].textoVisible().orEmpty()
  val color = when (accion) { "INSERT" -> Marca.Esmeralda500; "UPDATE" -> Marca.Cielo400; "DELETE" -> Marca.Rosa400; else -> Marca.Oro500 }
  Tarjeta(Modifier.animateContentSize(), onClick = { abierta = !abierta }, relleno = PaddingValues(12.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Pastilla(accion, color, mono = true)
      Spacer(Modifier.width(8.dp))
      Text(b["tabla"].textoVisible() ?: "—", style = Mono.merge(MaterialTheme.typography.labelLarge), modifier = Modifier.weight(1f))
      Text(fechaHora(b["ocurrido_en"].textoVisible()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(b["email"].textoVisible().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    b["llave"].textoVisible()?.let { Text("Llave $it", style = Mono.merge(MaterialTheme.typography.labelSmall), maxLines = if (abierta) 6 else 1, overflow = TextOverflow.Ellipsis) }
    if (abierta) {
      listOf("antes", "despues").forEach { k ->
        b[k].textoVisible()?.let {
          Text(if (k == "antes") "Antes" else "Después", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
          Text(it, style = Mono.merge(MaterialTheme.typography.labelSmall), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun SelectorEmpresas(d: Accesos, elegidas: MutableList<Long>) {
  Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
    d.empresas.forEach { em ->
      Row(Modifier.fillMaxWidth().clickable { if (em.id in elegidas) elegidas.remove(em.id) else elegidas.add(em.id) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = em.id in elegidas, onCheckedChange = { if (it) elegidas.add(em.id) else elegidas.remove(em.id) })
        Column {
          Text(em.razonSocial, style = MaterialTheme.typography.bodyMedium)
          Text(listOfNotNull(em.rfc, em.grupo).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
private fun CampoContrasena(valor: String, cambiar: (String) -> Unit) {
  OutlinedTextField(
    valor, cambiar, label = { Text("Contraseña inicial") }, singleLine = true,
    textStyle = MaterialTheme.typography.bodyMedium.merge(Mono),
    supportingText = { Text("Mínimo 12 caracteres. Compártela por un medio seguro: deberá cambiarla al entrar.") },
    trailingIcon = { TextButton(onClick = { cambiar(generarContrasena()) }) { Text("Generar") } },
    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun DialogoUsuario(d: Accesos, trabajando: Boolean, cerrar: () -> Unit, crear: (String, Rol, String, List<Long>) -> String?) {
  var email by remember { mutableStateOf("") }
  var rol by remember { mutableStateOf(Rol.LECTOR) }
  var pass by remember { mutableStateOf(generarContrasena()) }
  val elegidas = remember { mutableStateListOf<Long>() }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = cerrar,
    title = { Text("Nuevo usuario") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(email, { email = it }, label = { Text("Correo") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
          Rol.entries.forEachIndexed { i, r -> SegmentedButton(selected = rol == r, onClick = { rol = r }, shape = SegmentedButtonDefaults.itemShape(i, Rol.entries.size)) { Text(r.nombre) } }
        }
        CampoContrasena(pass) { pass = it }
        if (rol.esAdmin) Text("Un Admin ve todas las empresas: no se le asignan.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else { Text("Empresas", style = MaterialTheme.typography.labelLarge); SelectorEmpresas(d, elegidas) }
        if (error != null) Text(error!!, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = { TextButton(onClick = { error = crear(email, rol, pass, elegidas.toList()) }, enabled = !trabajando) { Text("Crear usuario", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
  )
}

@Composable
private fun DialogoEmpresas(u: UsuarioAcceso, d: Accesos, cerrar: () -> Unit, guardar: (List<Long>) -> Unit) {
  val elegidas = remember { mutableStateListOf<Long>().apply { addAll(u.empresas) } }
  AlertDialog(
    onDismissRequest = cerrar,
    title = { Text("Empresas de ${u.email}") },
    text = {
      Column {
        Text(
          if (u.rol == Rol.LECTOR) "Un Lector solo puede tener empresas de un mismo grupo." else "Un Editor puede tener empresas de varios grupos.",
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SelectorEmpresas(d, elegidas)
      }
    },
    confirmButton = { TextButton(onClick = { guardar(elegidas.toList()) }) { Text("Guardar", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
  )
}

@Composable
private fun DialogoRestablecer(u: UsuarioAcceso, cerrar: () -> Unit, guardar: (String) -> String?) {
  var pass by remember { mutableStateOf(generarContrasena()) }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = cerrar,
    title = { Text("Restablecer contraseña") },
    text = {
      Column {
        Text("${u.email} deberá cambiarla al entrar.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        CampoContrasena(pass) { pass = it }
        if (error != null) Text(error!!, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = { TextButton(onClick = { error = guardar(pass) }) { Text("Restablecer", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorGrupo(d: Accesos, actual: Long?, elegir: (Long?) -> Unit) {
  FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    (listOf<Pair<Long?, String>>(null to "Sin grupo") + d.grupos.map { it.id to it.nombre }).forEach { (id, nombre) ->
      Text(
        nombre, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(CircleShape).background(if (id == actual) Marca.Oro500.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
          .clickable { elegir(id) }.padding(horizontal = 12.dp, vertical = 7.dp),
      )
    }
  }
}

@Composable
private fun DialogoGrupo(em: Empresa, d: Accesos, cerrar: () -> Unit, guardar: (Long?) -> Unit) {
  var grupo by remember { mutableStateOf(em.grupoId) }
  AlertDialog(
    onDismissRequest = cerrar,
    title = { Text("Grupo de ${em.razonSocial}") },
    text = {
      Column {
        Text("Si un Lector tiene empresas de dos grupos, la API rechaza el cambio.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        SelectorGrupo(d, grupo) { grupo = it }
      }
    },
    confirmButton = { TextButton(onClick = { guardar(grupo) }, enabled = grupo != em.grupoId) { Text("Guardar", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
  )
}

@Composable
private fun DialogoEmpresa(d: Accesos, cerrar: () -> Unit, crear: (String, String, String?, String?, Long?) -> String?) {
  var rfc by remember { mutableStateOf("") }
  var razon by remember { mutableStateOf("") }
  var regimen by remember { mutableStateOf<String?>(null) }
  var sociedad by remember { mutableStateOf("") }
  var grupo by remember { mutableStateOf<Long?>(null) }
  var menuRegimen by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = cerrar,
    title = { Text("Nueva empresa") },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(rfc, { rfc = it.uppercase().take(13) }, label = { Text("RFC") }, singleLine = true, textStyle = MaterialTheme.typography.bodyMedium.merge(Mono), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(razon, { razon = it.take(300) }, label = { Text("Razón social") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Box {
          OutlinedTextField(
            regimen?.let { "$it · ${REGIMENES_FISCALES[it]}" } ?: "", {}, readOnly = true, label = { Text("Régimen fiscal (opcional)") },
            trailingIcon = { Icon(Iconos.Abajo, null, Modifier.clickable { menuRegimen = true }) },
            shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { menuRegimen = true },
          )
          DropdownMenu(expanded = menuRegimen, onDismissRequest = { menuRegimen = false }) {
            DropdownMenuItem(text = { Text("Sin régimen") }, onClick = { regimen = null; menuRegimen = false })
            REGIMENES_FISCALES.forEach { (k, v) -> DropdownMenuItem(text = { Text("$k · $v", style = MaterialTheme.typography.bodySmall) }, onClick = { regimen = k; menuRegimen = false }) }
          }
        }
        OutlinedTextField(sociedad, { sociedad = it.uppercase().take(4) }, label = { Text("Sociedad SAP (BUKRS)") }, supportingText = { Text("Hasta 4 letras o números; única dentro del grupo") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        Text("Grupo", style = MaterialTheme.typography.labelLarge)
        SelectorGrupo(d, grupo) { grupo = it }
        if (error != null) Text(error!!, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = { TextButton(onClick = { error = crear(rfc, razon, regimen, sociedad, grupo) }) { Text("Crear empresa", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
  )
}

@Composable
private fun DialogoTexto(titulo: String, etiqueta: String, cerrar: () -> Unit, guardar: (String) -> String?) {
  var texto by remember { mutableStateOf("") }
  var error by remember { mutableStateOf<String?>(null) }
  AlertDialog(
    onDismissRequest = cerrar,
    title = { Text(titulo) },
    text = {
      Column {
        OutlinedTextField(texto, { texto = it.take(120) }, label = { Text(etiqueta) }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        if (error != null) Text(error!!, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall)
      }
    },
    confirmButton = { TextButton(onClick = { error = guardar(texto) }) { Text("Crear", fontWeight = FontWeight.SemiBold) } },
    dismissButton = { TextButton(onClick = cerrar) { Text("Cancelar") } },
  )
}
