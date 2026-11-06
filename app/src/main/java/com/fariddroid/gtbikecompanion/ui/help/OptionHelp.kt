package com.fariddroid.gtbikecompanion.ui.help

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fariddroid.gtbikecompanion.R

/**
 * The mandatory «?» button (plan §10). Independent of the option's control: always enabled, never changes a
 * value or sends a command, works offline. 48 dp touch target via IconButton.
 */
@Composable
fun OptionHelpButton(
    topic: HelpTopic,
    optionLabel: String,
    modifier: Modifier = Modifier,
    currentReason: String? = null,
) {
    var open by rememberSaveable(topic) { mutableStateOf(false) }
    val description = stringResource(R.string.option_help_description, optionLabel)
    IconButton(
        onClick = { open = true },
        modifier = modifier
            .testTag("help:${topic.id}")
            .semantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "?",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
    if (open) HelpSheet(topic = topic, currentReason = currentReason, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpSheet(topic: HelpTopic, currentReason: String?, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("helpSheet")) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(topic.title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            Text(text = stringResource(topic.body), style = MaterialTheme.typography.bodyLarge)
            if (currentReason != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.help_current_reason, currentReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).testTag("helpClose")) {
                Text(stringResource(R.string.help_close))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
