package com.example.indriveclone.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.indriveclone.domain.fare.FareBounds
import com.example.indriveclone.domain.fare.FareCalculator
import com.example.indriveclone.ui.theme.LabelTextStyle
import com.example.indriveclone.ui.util.formatMoney

/**
 * Shared fare picker: a slider plus +/- buttons, exactly bounded by [FareBounds].
 *
 * The rider's offer and the driver's counter-offer use this same composable fed by the same
 * [FareCalculator] window, so "these bounds are the rules" is true on both sides of the marketplace.
 */
@Composable
fun FareAdjuster(
    fare: Double,
    bounds: FareBounds,
    onFareChange: (Double) -> Unit,
    onStep: (Int) -> Unit,
    modifier: Modifier = Modifier,
    amountLabel: String = "Your offer",
    suggestedLabel: String? = "Suggested",
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(text = amountLabel, style = LabelTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatMoney(fare),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (suggestedLabel != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = suggestedLabel,
                        style = LabelTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = formatMoney(bounds.suggested), style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (bounds.maximum > bounds.minimum) {
            Slider(
                value = fare.toFloat(),
                onValueChange = { raw -> onFareChange(FareCalculator.snapToStep(raw.toDouble(), bounds)) },
                valueRange = bounds.minimum.toFloat()..bounds.maximum.toFloat(),
                steps = bounds.sliderSteps,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Min ${formatMoney(bounds.minimum)}",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Step ${formatMoney(bounds.step)}",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Max ${formatMoney(bounds.maximum)}",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = { onStep(-1) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = "Decrease fare by one step",
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "−${formatMoney(bounds.step)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(56.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "at the top: ${formatMoney(bounds.maximum)}",
                style = LabelTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "+${formatMoney(bounds.step)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(56.dp),
            )
            FilledTonalIconButton(onClick = { onStep(1) }, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase fare by one step",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
