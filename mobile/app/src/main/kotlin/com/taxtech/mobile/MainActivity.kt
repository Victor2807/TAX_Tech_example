package com.taxtech.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.taxtech.mobile.data.EstadoSesion
import com.taxtech.mobile.ui.comun.FondoAurora
import com.taxtech.mobile.ui.comun.Monograma
import com.taxtech.mobile.ui.login.CambiarContrasenaScreen
import com.taxtech.mobile.ui.login.LoginScreen
import com.taxtech.mobile.ui.panel.PanelScreen
import com.taxtech.mobile.ui.panel.PanelViewModel
import com.taxtech.mobile.ui.theme.TAXTechTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val contenedor = (application as TaxTechAplicacion).contenedor
    setContent {
      TAXTechTheme {
        CompositionLocalProvider(LocalContenedor provides contenedor) { AppTaxTech() }
      }
    }
  }
}

/**
 * Restaura la sesión al arrancar y guarda los ViewModels del panel de la sesión activa:
 * sobreviven a la rotación y se descartan al salir, como `salir()` del panel web.
 */
class AppViewModel(private val c: Contenedor) : ViewModel() {
  private var sesionPanel = -1L
  private var almacen: ViewModelStore? = null

  init {
    viewModelScope.launch { c.sesion.restaurar() }
  }

  fun almacenPanel(sesion: Long): ViewModelStore {
    if (sesion != sesionPanel || almacen == null) {
      almacen?.clear()
      almacen = ViewModelStore()
      sesionPanel = sesion
    }
    return almacen!!
  }

  fun liberarPanel() {
    almacen?.clear()
    almacen = null
    sesionPanel = -1
  }

  override fun onCleared() {
    almacen?.clear()
  }
}

@Composable
fun AppTaxTech() {
  val c = LocalContenedor.current
  val app: AppViewModel = viewModel { AppViewModel(c) }
  val estado by c.sesion.estado.collectAsStateWithLifecycle()
  AnimatedContent(
    targetState = estado,
    contentKey = { e -> if (e is EstadoSesion.Activa) "panel-${e.id}" else e::class.simpleName },
    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
    label = "sesion",
  ) { e ->
    when (e) {
      EstadoSesion.Comprobando -> Portada()
      is EstadoSesion.SinSesion -> {
        LaunchedEffect(Unit) { app.liberarPanel() }
        LoginScreen(e.mensaje)
      }
      is EstadoSesion.DebeCambiarContrasena -> CambiarContrasenaScreen(e.usuario)
      is EstadoSesion.Activa -> {
        val almacen = app.almacenPanel(e.id)
        val dueno = remember(almacen) { object : ViewModelStoreOwner { override val viewModelStore = almacen } }
        CompositionLocalProvider(LocalViewModelStoreOwner provides dueno) {
          val panel: PanelViewModel = viewModel { PanelViewModel(c, e.usuario) }
          PanelScreen(e.usuario, panel)
        }
      }
    }
  }
}

@Composable
private fun Portada() {
  FondoAurora {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
      Monograma(tamano = 72.dp)
      Spacer(Modifier.height(20.dp))
      Text("TAX Tech", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
      Spacer(Modifier.height(24.dp))
      CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
  }
}
