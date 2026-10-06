package com.example.indriveclone.ui

import androidx.compose.runtime.Composable
import com.example.indriveclone.ui.navigation.AppNavHost

/**
 * Root composable: theme is applied in [com.example.indriveclone.MainActivity], the navigation graph
 * lives here so screens stay independent of each other.
 */
@Composable
fun AppRoot() {
    AppNavHost()
}
