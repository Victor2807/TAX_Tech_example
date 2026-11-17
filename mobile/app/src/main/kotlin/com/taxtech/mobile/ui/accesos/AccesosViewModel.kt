package com.taxtech.mobile.ui.accesos

import com.taxtech.mobile.Contenedor
import com.taxtech.mobile.data.Accesos
import com.taxtech.mobile.data.ConfigApi
import com.taxtech.mobile.data.FORMATO_RFC
import com.taxtech.mobile.data.Rol
import com.taxtech.mobile.ui.comun.VmBase
import java.security.SecureRandom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonObject

/** Contraseña inicial aleatoria de 16 caracteres, sin caracteres que se confundan (0/O, 1/l/I). */
fun generarContrasena(largo: Int = 16): String {
  val alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789-_.!?"
  val r = SecureRandom()
  return (1..largo).map { alfabeto[r.nextInt(alfabeto.length)] }.joinToString("")
}

private val CORREO = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

class AccesosViewModel(c: Contenedor) : VmBase(c) {
  data class Estado(
    val datos: Accesos? = null,
    val cargando: Boolean = false,
    val error: String? = null,
    val trabajando: Boolean = false,
    val bitacora: List<JsonObject> = emptyList(),
    val paginaBitacora: Int = 0,
    val totalBitacora: Long = 0,
    val cargandoBitacora: Boolean = false,
  )

  val estado = MutableStateFlow(Estado())

  fun cargar(forzar: Boolean = false) {
    if (!forzar && estado.value.datos != null) return
    estado.update { it.copy(cargando = true, error = null) }
    lanzar(alFallar = { m -> estado.update { it.copy(error = m) } }, alTerminar = { estado.update { it.copy(cargando = false) } }) {
      estado.update { it.copy(datos = repo.accesos()) }
    }
  }

  /** Ejecuta un cambio y vuelve a leer los accesos. Devuelve el error de validación local, si lo hay. */
  private fun cambio(mensaje: String, alTerminar: () -> Unit = {}, bloque: suspend () -> Unit) {
    estado.update { it.copy(trabajando = true) }
    lanzar(alTerminar = { estado.update { it.copy(trabajando = false) } }) {
      bloque()
      avisar(mensaje)
      estado.update { it.copy(datos = repo.accesos()) }
      alTerminar()
    }
  }

  fun crearUsuario(email: String, rol: Rol, contrasena: String, empresas: List<Long>, alTerminar: () -> Unit): String? {
    if (!CORREO.matches(email.trim())) return "Correo no válido."
    if (contrasena.length < ConfigApi.MINIMO_CONTRASENA) return "La contraseña debe tener al menos ${ConfigApi.MINIMO_CONTRASENA} caracteres."
    cambio("Usuario ${email.trim().lowercase()} creado", alTerminar) { repo.crearUsuario(email, rol, contrasena, if (rol.esAdmin) emptyList() else empresas) }
    return null
  }

  fun cambiarRol(id: Long, rol: Rol) = cambio("Perfil actualizado") { repo.cambiarUsuario(id, rol = rol) }

  fun cambiarActivo(id: Long, activo: Boolean) = cambio(if (activo) "Usuario activado" else "Usuario desactivado") { repo.cambiarUsuario(id, activo = activo) }

  fun restablecer(id: Long, contrasena: String, alTerminar: () -> Unit): String? {
    if (contrasena.length < ConfigApi.MINIMO_CONTRASENA) return "La contraseña debe tener al menos ${ConfigApi.MINIMO_CONTRASENA} caracteres."
    cambio("Contraseña restablecida: deberá cambiarla al entrar", alTerminar) { repo.restablecerContrasena(id, contrasena) }
    return null
  }

  fun asignarEmpresas(id: Long, empresas: List<Long>, alTerminar: () -> Unit) = cambio("Empresas asignadas", alTerminar) { repo.empresasDeUsuario(id, empresas) }

  fun crearGrupo(nombre: String, alTerminar: () -> Unit): String? {
    if (nombre.trim().length < 2) return "Escribe el nombre del grupo."
    cambio("Grupo creado", alTerminar) { repo.crearGrupo(nombre) }
    return null
  }

  fun crearEmpresa(rfc: String, razon: String, regimen: String?, sociedad: String?, grupoId: Long?, alTerminar: () -> Unit): String? {
    val r = rfc.trim().uppercase()
    if (!FORMATO_RFC.matches(r)) return "RFC no válido: 12 caracteres para persona moral o 13 para persona física."
    if (r == "XAXX010101000" || r == "XEXX010101000") return "Ese es un RFC genérico del SAT: usa el RFC de la empresa."
    if (razon.trim().length < 2) return "Escribe la razón social."
    if (!sociedad.isNullOrBlank() && !Regex("^[A-Za-z\\d]{1,4}$").matches(sociedad.trim())) return "La sociedad SAP (BUKRS) tiene hasta 4 letras o números."
    cambio("Empresa $r creada", alTerminar) { repo.crearEmpresa(r, razon, regimen, sociedad, grupoId) }
    return null
  }

  fun cambiarGrupo(empresaId: Long, grupoId: Long?, alTerminar: () -> Unit) = cambio("Grupo de la empresa actualizado", alTerminar) { repo.cambiarGrupoEmpresa(empresaId, grupoId) }

  fun cargarBitacora(reiniciar: Boolean = false) {
    val e = estado.value
    if (e.cargandoBitacora) return
    val pagina = if (reiniciar) 1 else e.paginaBitacora + 1
    estado.update { it.copy(cargandoBitacora = true) }
    lanzar(alTerminar = { estado.update { it.copy(cargandoBitacora = false) } }) {
      val p = repo.bitacora(pagina)
      estado.update { it.copy(bitacora = if (reiniciar) p.filas else it.bitacora + p.filas, paginaBitacora = pagina, totalBitacora = p.total) }
    }
  }
}
