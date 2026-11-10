package com.coachchicken

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Turn the daily reminder on or off and pick the time of day. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDialog(
    initialEnabled: Boolean, initialHour: Int, initialMinute: Int,
    onDismiss: () -> Unit, onSave: (enabled: Boolean, hour: Int, minute: Int) -> Unit,
) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    val time = rememberTimePickerState(initialHour, initialMinute, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Daily cluck") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("A nudge from the chicken at this time each day, unless today's plank is already done.", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Remind me", style = MaterialTheme.typography.titleMedium)
                    Switch(enabled, { enabled = it })
                }
                TimeInput(time)
            }
        },
        confirmButton = { TextButton({ onSave(enabled, time.hour, time.minute) }) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
