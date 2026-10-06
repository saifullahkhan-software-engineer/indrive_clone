package com.example.indriveclone.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.indriveclone.data.model.UserRole

/** First screen: pick the role. Changeable later from the overflow menu. */
@Composable
fun RoleSelectionScreen(
    onRoleSelected: (UserRole) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "inDrive",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Ride-hailing demo — pick how you want to use the app. Everything is local; there is no backend.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            RoleCard(
                title = "I'm a Rider",
                subtitle = "Pick two points on the map, see the road distance and set your fare.",
                icon = Icons.Filled.Place,
                onClick = { onRoleSelected(UserRole.RIDER) },
            )
            Spacer(Modifier.height(16.dp))
            RoleCard(
                title = "I'm a Driver",
                subtitle = "Browse open requests and accept one or send a counter-offer.",
                icon = Icons.Filled.Person,
                onClick = { onRoleSelected(UserRole.DRIVER) },
            )

            Spacer(Modifier.height(24.dp))
            TextButton(onClick = onOpenSettings, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Admin settings")
            }
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
