package dev.ashish.wristpet.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ashish.wristpet.pet.Bedtime
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Nudge
import dev.ashish.wristpet.sync.PetCommand
import dev.ashish.wristpet.sync.PetSnapshot

@Composable
fun SettingsScreen(snapshot: PetSnapshot?, reachable: Boolean, send: SendCommand) {
    if (snapshot == null) return EmptyState(NO_SYNC_MESSAGE)
    val pet = snapshot.state
    var name by remember(pet.name) { mutableStateOf(pet.name) }
    val enabled = reachable

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!reachable) {
            Text("Your watch is away. Settings are read-only until it's nearby.", style = MaterialTheme.typography.bodyMedium)
        }
        Section("Name") {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(GameRules.MAX_NAME_LENGTH) },
                singleLine = true,
                enabled = enabled,
                supportingText = { Text("Leave empty to use the pet's own name.") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { send(PetCommand.Rename(name)) {} }, enabled = enabled && name != pet.name) { Text("Save name") }
        }
        Section("Bedtime", "Your pet sleeps on the face during these hours.") {
            Bedtime.entries.forEach { option ->
                Choice(option.label, option == pet.bedtime, enabled) { send(PetCommand.SetBedtime(option)) {} }
            }
        }
        Section("Gentle nudges", "Your pet gets curious on the face — a cue to drink water or stretch. No alerts, ever.") {
            Nudge.entries.forEach { option ->
                Choice(option.label, option == pet.nudge, enabled) { send(PetCommand.SetNudge(option)) {} }
            }
        }
    }
}

@Composable
private fun Section(title: String, description: String? = null, content: @Composable () -> Unit) {
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
            content()
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 8.dp))
    }
}
