package com.taxtech.mobile.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.taxtech.mobile.R
import com.taxtech.mobile.data.DefaultDataRepository
import com.taxtech.mobile.ui.theme.TAXTechTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DefaultDataRepository()) },
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  when (val state = uiState) {
    MainScreenUiState.Loading -> {
      Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator()
      }
    }
    is MainScreenUiState.Success -> {
      MainScreen(data = state.data, modifier = modifier)
    }
    is MainScreenUiState.Error -> {
      Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Text(stringResource(R.string.error_carga, state.throwable.message.orEmpty()))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MainScreen(data: List<String>, modifier: Modifier = Modifier) {
  Scaffold(
    modifier = modifier,
    topBar = {
      CenterAlignedTopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        colors = TopAppBarDefaults.topAppBarColors(
          containerColor = MaterialTheme.colorScheme.primaryContainer,
          titleContentColor = MaterialTheme.colorScheme.primary
        )
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      data.forEach { Saludo(it) }
      Text(
        text = stringResource(R.string.bienvenida),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
fun Saludo(nombre: String, modifier: Modifier = Modifier) {
  Text(
    text = stringResource(R.string.saludo, nombre),
    modifier = modifier,
    style = MaterialTheme.typography.headlineLarge,
    color = MaterialTheme.colorScheme.primary,
    textAlign = TextAlign.Center
  )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun MainScreenPreview() {
  TAXTechTheme { MainScreen(listOf("mundo")) }
}
