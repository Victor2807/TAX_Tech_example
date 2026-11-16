package com.taxtech.mobile.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [com.taxtech.mobile.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen(FAKE_DATA) }
  }

  @Test
  fun saludos_existen() {
    FAKE_DATA.forEach { composeTestRule.onNodeWithText("¡Hola, $it!").assertExists() }
  }
}

private val FAKE_DATA = listOf("Sample1", "Sample2", "Sample3")
