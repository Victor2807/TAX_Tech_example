package com.taxtech.mobile.ui.login

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.LocalContenedor
import com.taxtech.mobile.data.ConfigApi
import com.taxtech.mobile.data.Usuario
import com.taxtech.mobile.data.normalizarServidor
import com.taxtech.mobile.ui.comun.BotonPrincipal
import com.taxtech.mobile.ui.comun.Ceja
import com.taxtech.mobile.ui.comun.FondoAurora
import com.taxtech.mobile.ui.comun.Iconos
import com.taxtech.mobile.ui.comun.Monograma
import com.taxtech.mobile.ui.comun.Tarjeta
import com.taxtech.mobile.ui.comun.VmBase
import com.taxtech.mobile.ui.theme.LocalColoresTax
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class LoginViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(val servidor: String, val email: String = "", val contrasena: String = "", val cargando: Boolean = false, val error: String? = null)

  val estado = MutableStateFlow(Estado(c.sesion.servidor))

  fun cambiar(f: (Estado) -> Estado) = estado.update { f(it).copy(error = null) }

  fun entrar() {
    val e = estado.value
    val servidor = normalizarServidor(e.servidor)
    when {
      servidor == null -> estado.update { it.copy(error = "Escribe la dirección del servidor, por ejemplo ${ConfigApi.SERVIDOR_POR_OMISION}.") }
      e.email.isBlank() || e.contrasena.isEmpty() -> estado.update { it.copy(error = "Escribe tu correo y tu contraseña.") }
      else -> {
        estado.update { it.copy(cargando = true, error = null, servidor = servidor) }
        lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargando = false, contrasena = "") } }) {
          c.sesion.iniciar(servidor, e.email, e.contrasena)
        }
      }
    }
  }
}

@Composable
fun LoginScreen(mensaje: String?) {
  val c = LocalContenedor.current
  val vm: LoginViewModel = viewModel { LoginViewModel(c) }
  val e by vm.estado.collectAsStateWithLifecycle()
  var verContrasena by remember { mutableStateOf(false) }
  var editarServidor by rememberSaveable { mutableStateOf(false) }
  val colores = LocalColoresTax.current

  FondoAurora {
    Column(
      Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Spacer(Modifier.height(24.dp))
      Monograma(tamano = 64.dp)
      Spacer(Modifier.height(20.dp))
      Ceja("Conciliación fiscal · México")
      Spacer(Modifier.height(6.dp))
      Text("TAX Tech", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
      Spacer(Modifier.height(6.dp))
      Text(
        "CFDI ↔ ERP ↔ bancos ↔ pedimentos, en tu bolsillo.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(28.dp))
      Tarjeta(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        Text("Inicia sesión", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text("Con la cuenta que te dio de alta un administrador.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
          value = e.email,
          onValueChange = { v -> vm.cambiar { it.copy(email = v) } },
          label = { Text("Correo") },
          leadingIcon = { Icon(Iconos.Correo, null, modifier = Modifier.size(20.dp)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
          value = e.contrasena,
          onValueChange = { v -> vm.cambiar { it.copy(contrasena = v) } },
          label = { Text("Contraseña") },
          leadingIcon = { Icon(Iconos.Candado, null, modifier = Modifier.size(20.dp)) },
          trailingIcon = {
            Icon(
              if (verContrasena) Iconos.Ocultar else Iconos.Ojo,
              if (verContrasena) "Ocultar contraseña" else "Mostrar contraseña",
              modifier = Modifier.size(20.dp).clickable { verContrasena = !verContrasena },
            )
          },
          visualTransformation = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation(),
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { vm.entrar() }),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth(),
        )
        val error = e.error ?: mensaje
        if (error != null) {
          Spacer(Modifier.height(10.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Iconos.Alerta, null, tint = colores.peligro, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(error, color = colores.peligro, style = MaterialTheme.typography.bodySmall)
          }
        }
        Spacer(Modifier.height(16.dp))
        BotonPrincipal(if (e.cargando) "Entrando…" else "Entrar", onClick = vm::entrar, cargando = e.cargando, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
          Icon(Iconos.Servidor, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
          Spacer(Modifier.width(6.dp))
          Text(e.servidor, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
          TextButton(onClick = { editarServidor = !editarServidor }) { Text(if (editarServidor) "Listo" else "Cambiar") }
        }
        AnimatedVisibility(editarServidor) {
          Column {
            OutlinedTextField(
              value = e.servidor,
              onValueChange = { v -> vm.cambiar { it.copy(servidor = v) } },
              label = { Text("Servidor de la API") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Text(
              "Emulador: ${ConfigApi.SERVIDOR_POR_OMISION} (la API local en 127.0.0.1:3001 de tu equipo). " +
                "Celular por USB: ejecuta adb reverse tcp:3001 tcp:3001 y usa http://127.0.0.1:3001.",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(Modifier.height(16.dp))
      Text(
        "Solo entran las cuentas dadas de alta con perfil Lector, Editor o Admin.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

// ---------------------------------------------------------------- contraseña

class ContrasenaViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(val cargando: Boolean = false, val error: String? = null, val listo: Boolean = false)

  val estado = MutableStateFlow(Estado())

  fun enviar(actual: String, nueva: String, confirmacion: String) {
    val error = validarContrasenaNueva(actual, nueva, confirmacion)
    if (error != null) { estado.update { it.copy(error = error) }; return }
    estado.update { it.copy(cargando = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargando = false) } }) {
      c.sesion.cambiarContrasena(actual, nueva)
      estado.update { it.copy(listo = true) }
      avisar("Contraseña actualizada")
    }
  }

  fun reiniciar() { estado.value = Estado() }

  fun salir() = c.sesion.salir()
}

/** Mismas reglas que la API: 12 a 128 caracteres y distinta de la actual. */
fun validarContrasenaNueva(actual: String, nueva: String, confirmacion: String): String? = when {
  actual.isEmpty() -> "Escribe tu contraseña actual."
  nueva.length < ConfigApi.MINIMO_CONTRASENA -> "La contraseña debe tener al menos ${ConfigApi.MINIMO_CONTRASENA} caracteres."
  nueva.length > 128 -> "La contraseña admite hasta 128 caracteres."
  nueva == actual -> "La contraseña nueva debe ser distinta de la actual."
  nueva != confirmacion -> "La confirmación no coincide."
  else -> null
}

@Composable
fun FormularioContrasena(cargando: Boolean, error: String?, alEnviar: (String, String, String) -> Unit) {
  var actual by remember { mutableStateOf("") }
  var nueva by remember { mutableStateOf("") }
  var confirmacion by remember { mutableStateOf("") }
  val campo = @Composable { etiqueta: String, valor: String, cambiar: (String) -> Unit ->
    OutlinedTextField(
      value = valor, onValueChange = cambiar, label = { Text(etiqueta) }, singleLine = true,
      visualTransformation = PasswordVisualTransformation(),
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
      shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
    )
  }
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    campo("Contraseña actual", actual) { actual = it }
    campo("Contraseña nueva", nueva) { nueva = it }
    campo("Confirma la contraseña nueva", confirmacion) { confirmacion = it }
    Text(
      "Mínimo ${ConfigApi.MINIMO_CONTRASENA} caracteres. Usa una frase que recuerdes.",
      style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (error != null) Text(error, color = LocalColoresTax.current.peligro, style = MaterialTheme.typography.bodySmall)
    BotonPrincipal("Guardar contraseña", onClick = { alEnviar(actual, nueva, confirmacion) }, cargando = cargando, icono = Iconos.Llave, modifier = Modifier.fillMaxWidth())
  }
}

/** Pantalla obligatoria tras el alta o un restablecimiento de contraseña. */
@Composable
fun CambiarContrasenaScreen(usuario: Usuario) {
  val c = LocalContenedor.current
  val vm: ContrasenaViewModel = viewModel { ContrasenaViewModel(c) }
  val e by vm.estado.collectAsStateWithLifecycle()
  FondoAurora {
    Column(
      Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Monograma(tamano = 56.dp)
      Spacer(Modifier.height(18.dp))
      Ceja("Primer ingreso")
      Spacer(Modifier.height(6.dp))
      Text("Crea tu contraseña", style = MaterialTheme.typography.headlineMedium)
      Spacer(Modifier.height(6.dp))
      Text(
        "${usuario.email}: un administrador te dio una contraseña inicial. Cámbiala para continuar.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(20.dp))
      Tarjeta(Modifier.widthIn(max = 440.dp).fillMaxWidth()) {
        FormularioContrasena(e.cargando, e.error, vm::enviar)
      }
      Spacer(Modifier.height(8.dp))
      TextButton(onClick = vm::salir) { Text("Salir", fontWeight = FontWeight.Medium) }
    }
  }
}
