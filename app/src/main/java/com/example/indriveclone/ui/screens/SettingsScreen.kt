package com.example.indriveclone.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.indriveclone.data.model.AdminSettings
import com.example.indriveclone.data.model.RouteSource
import com.example.indriveclone.data.model.UserRole
import com.example.indriveclone.domain.fare.FareCalculator
import com.example.indriveclone.ui.components.AppTopBar
import com.example.indriveclone.ui.theme.LabelTextStyle
import com.example.indriveclone.ui.util.formatMoney
import com.example.indriveclone.ui.util.routeSourceDescription
import com.example.indriveclone.viewmodel.SettingsViewModel

/**
 * Admin settings — no login in the demo. Fare rules and the optional ORS key are persisted with
 * DataStore, so they survive a restart; the ORS key from local.properties is used only while the key
 * field here is empty (blank == not provided).
 */
@Composable
fun SettingsScreen(
    role: UserRole?,
    onBack: () -> Unit,
    onSwitchRole: () -> Unit,
    onResetDemoData: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Text state is (re)initialised from persisted values; editing only writes back on Save.
    var baseFare by remember(settings) { mutableStateOf(settings.baseFare.toEditable()) }
    var perKmRate by remember(settings) { mutableStateOf(settings.perKmRate.toEditable()) }
    var minimumFare by remember(settings) { mutableStateOf(settings.minimumFare.toEditable()) }
    var maxMultiplier by remember(settings) { mutableStateOf(settings.maxFareMultiplier.toEditable()) }
    var fareStep by remember(settings) { mutableStateOf(settings.fareStep) }
    var orsKey by remember(settings) { mutableStateOf(settings.orsApiKey) }
    var revealKey by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Admin settings",
                role = role,
                onBack = onBack,
                onOpenSettings = onBack,
                onSwitchRole = onSwitchRole,
                onResetDemoData = onResetDemoData,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Fare rules") {
                NumberField(
                    label = "Base fare",
                    value = baseFare,
                    onValueChange = { baseFare = it; error = null },
                )
                NumberField(
                    label = "Per-km rate",
                    value = perKmRate,
                    onValueChange = { perKmRate = it; error = null },
                )
                NumberField(
                    label = "Minimum fare",
                    value = minimumFare,
                    onValueChange = { minimumFare = it; error = null },
                )
                NumberField(
                    label = "Max fare multiplier",
                    value = maxMultiplier,
                    onValueChange = { maxMultiplier = it; error = null },
                    supporting = "The slider's ceiling is the suggested fare × this value (1x or more).",
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "Fare step", style = LabelTextStyle)
                    Spacer(Modifier.width(12.dp))
                    FareStepChip(
                        step = FareCalculator.DEFAULT_STEP,
                        selected = fareStep == FareCalculator.DEFAULT_STEP,
                        onSelect = { fareStep = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    FareStepChip(
                        step = FareCalculator.COARSE_STEP,
                        selected = fareStep == FareCalculator.COARSE_STEP,
                        onSelect = { fareStep = it },
                    )
                }

                // Live preview straight from the pure fare function the app uses at runtime.
                val previewSettings = AdminSettings(
                    baseFare = parseAmount(baseFare) ?: settings.baseFare,
                    perKmRate = parseAmount(perKmRate) ?: settings.perKmRate,
                    minimumFare = parseAmount(minimumFare) ?: settings.minimumFare,
                    maxFareMultiplier = parseAmount(maxMultiplier) ?: settings.maxFareMultiplier,
                    fareStep = fareStep,
                )
                val preview = FareCalculator.bounds(
                    distanceMeters = 5_000.0,
                    settings = previewSettings,
                    step = previewSettings.fareStep,
                )
                Text(
                    text = "Preview — a 5 km trip would suggest ${formatMoney(preview.suggested)}, " +
                        "and the adjuster would allow ${formatMoney(preview.minimum)}…" +
                        "${formatMoney(preview.maximum)} in steps of ${formatMoney(preview.step)}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val parsedBase = parseAmount(baseFare)
                            val parsedPerKm = parseAmount(perKmRate)
                            val parsedMinimum = parseAmount(minimumFare)
                            val parsedMultiplier = parseAmount(maxMultiplier)
                            when {
                                parsedBase == null || parsedPerKm == null || parsedMinimum == null || parsedMultiplier == null ->
                                    error = "Enter a valid non-negative number in every field."
                                parsedMinimum <= 0.0 -> error = "The minimum fare must be greater than 0."
                                parsedMultiplier < 1.0 -> error = "The multiplier must be at least 1x."
                                else -> {
                                    error = null
                                    viewModel.updateBaseFare(parsedBase)
                                    viewModel.updatePerKmRate(parsedPerKm)
                                    viewModel.updateMinimumFare(parsedMinimum)
                                    viewModel.updateMaxFareMultiplier(parsedMultiplier)
                                    viewModel.updateFareStep(fareStep)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save", fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { viewModel.resetToDefaults(); error = null }) {
                        Text("Restore defaults")
                    }
                }
            }

            SectionCard(title = "Routing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "The app always works without a key. Leave the field empty to use the public OSRM demo server; with a key the chain becomes OpenRouteService → OSRM → offline estimate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = orsKey,
                    onValueChange = { orsKey = it },
                    label = { Text("OpenRouteService API key") },
                    singleLine = true,
                    visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { revealKey = !revealKey }) {
                            Text(if (revealKey) "Hide" else "Show")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { viewModel.updateOrsKey(orsKey.trim()) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Save key") }
                    TextButton(
                        onClick = {
                            orsKey = ""
                            viewModel.clearOrsKey()
                        },
                    ) {
                        Icon(Icons.Filled.Clear, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Clear")
                    }
                }

                HorizontalDivider()

                Text(
                    text = buildString {
                        val keyFromSettings = settings.orsApiKey.isNotBlank()
                        append("Active chain: ")
                        append(
                            when {
                                keyFromSettings -> "OpenRouteService (key from this screen) → OSRM → offline estimate"
                                viewModel.buildConfigKeyPresent -> "OpenRouteService (key from local.properties) → OSRM → offline estimate"
                                else -> "OSRM (public demo server, no key) → offline estimate"
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append("ORS = ")
                        append(routeSourceDescription(RouteSource.ORS))
                        append(" · OSRM = ")
                        append(routeSourceDescription(RouteSource.OSRM))
                        append(" · fallback = ")
                        append(routeSourceDescription(RouteSource.HAVERSINE))
                    },
                    style = LabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Reminder: the public OSRM server is for demos only, and the ORS free tier is daily-rate-limited — that is why route requests are debounced and cached.",
                    style = LabelTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "Keys live in DataStore on this device only. Never commit an API key: local.properties is git-ignored; local.properties.example shows the format.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FareStepChip(step: Double, selected: Boolean, onSelect: (Double) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onSelect(step) },
        label = { Text(formatMoney(step)) },
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    supporting: String? = null,
) {
    val supportingContent: (@Composable () -> Unit)? = if (supporting == null) {
        null
    } else {
        { Text(supporting) }
    }
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        singleLine = true,
        supportingText = supportingContent,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** "100" for 100.0, "2.5" for 2.5 — avoids trailing ".0" in the text fields. */
private fun Double.toEditable(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private fun parseAmount(text: String): Double? =
    text.replace(',', '.').trim().toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }
