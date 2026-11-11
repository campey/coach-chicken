package com.coachchicken

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Turn the daily clucks on or off. */
@Composable
fun ReminderDialog(initialEnabled: Boolean, onDismiss: () -> Unit, onTest: () -> Unit, onSave: (enabled: Boolean) -> Unit) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily cluck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me", style = MaterialTheme.typography.titleMedium)
                    Switch(enabled, { enabled = it })
                }
                Text("6:00 a morning cluck", style = MaterialTheme.typography.bodyLarge)
                Text("13:00 a lunchtime nudge", style = MaterialTheme.typography.bodyLarge)
                Text("21:00 a streak saver, only if you have a streak to lose", style = MaterialTheme.typography.bodyLarge)
                Text("Once today's plank is done, the rest of the day stays quiet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onTest) { Text("Send a test cluck in a minute") }
            }
        },
        confirmButton = { TextButton({ onSave(enabled) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
