package com.taxtech.mobile.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface DataRepository {
  val data: Flow<List<String>>
}

class DefaultDataRepository : DataRepository {
  // Nombres a los que la pantalla principal saluda.
  override val data: Flow<List<String>> = flow { emit(listOf("mundo")) }
}
