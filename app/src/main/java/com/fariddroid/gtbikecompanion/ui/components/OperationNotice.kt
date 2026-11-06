package com.fariddroid.gtbikecompanion.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fariddroid.gtbikecompanion.R
import com.fariddroid.gtbikecompanion.feature.common.OperationUi
import com.fariddroid.gtbikecompanion.ui.format.reasonText
import com.fariddroid.gtbikecompanion.ui.theme.LocalStatusColors

/**
 * Result of the last action, announced once (polite live region). "Uncertain" is never shown as failure:
 * the PC may have applied it, so the user checks the game before trying again (plan §14).
 */
@Composable
fun OperationNotice(operation: OperationUi?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (operation == null) return
    val (text, color) = when (operation) {
        OperationUi.Sending -> stringResource(R.string.op_sending) to MaterialTheme.colorScheme.onSurfaceVariant
        OperationUi.Waiting -> stringResource(R.string.op_waiting) to MaterialTheme.colorScheme.onSurfaceVariant
        OperationUi.Applied -> stringResource(R.string.op_applied) to LocalStatusColors.current.success
        OperationUi.Uncertain -> stringResource(R.string.op_uncertain) to MaterialTheme.colorScheme.secondary
        is OperationUi.Refused -> stringResource(R.string.op_refused, stringResource(reasonText(operation.code))) to MaterialTheme.colorScheme.error
    }
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }.testTag("operationNotice"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color, modifier = Modifier.weight(1f))
        if (operation != OperationUi.Sending && operation != OperationUi.Waiting) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.op_dismiss)) }
        }
    }
}
