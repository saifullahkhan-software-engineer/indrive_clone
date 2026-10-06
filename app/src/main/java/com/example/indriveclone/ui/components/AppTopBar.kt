package com.example.indriveclone.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.indriveclone.data.model.UserRole

/**
 * Shared top bar. The overflow menu holds the demo-level actions (settings, role switch, reset), which
 * is how the role stays changeable after the first screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    role: UserRole?,
    onBack: (() -> Unit)? = null,
    onOpenSettings: () -> Unit,
    onSwitchRole: () -> Unit,
    onResetDemoData: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        actions = {
            actions()
            IconButton(onClick = { menuExpanded = true }) {
                Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Admin settings") },
                    onClick = {
                        menuExpanded = false
                        onOpenSettings()
                    },
                )
                DropdownMenuItem(
                    text = { Text(if (role == UserRole.DRIVER) "Switch to rider" else "Switch to driver") },
                    onClick = {
                        menuExpanded = false
                        onSwitchRole()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Reset demo data") },
                    onClick = {
                        menuExpanded = false
                        onResetDemoData()
                    },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}
