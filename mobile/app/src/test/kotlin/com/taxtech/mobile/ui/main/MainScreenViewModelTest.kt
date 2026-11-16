package com.taxtech.mobile.ui.main

import com.taxtech.mobile.data.DataRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {
  // viewModelScope usa Dispatchers.Main, que no existe en las pruebas locales.
  @Before
  fun configurarMain() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
  }

  @After
  fun restaurarMain() {
    Dispatchers.resetMain()
  }

  @Test
  fun uiState_inicialmenteCargando() = runTest {
    val viewModel = MainScreenViewModel(RepositorioFalso())
    assertEquals(MainScreenUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun uiState_muestraLosDatosDelRepositorio() = runTest {
    val viewModel = MainScreenViewModel(RepositorioFalso())
    val estado = viewModel.uiState.first { it !is MainScreenUiState.Loading }
    assertEquals(MainScreenUiState.Success(listOf("Sample")), estado)
  }

  @Test
  fun uiState_reportaElErrorDelRepositorio() = runTest {
    val falla = IllegalStateException("sin conexión")
    val viewModel = MainScreenViewModel(RepositorioFalso(error = falla))
    val estado = viewModel.uiState.first { it !is MainScreenUiState.Loading }
    assertEquals(MainScreenUiState.Error(falla), estado)
  }
}

private class RepositorioFalso(private val error: Throwable? = null) : DataRepository {
  override val data: Flow<List<String>> = flow {
    error?.let { throw it }
    emit(listOf("Sample"))
  }
}
