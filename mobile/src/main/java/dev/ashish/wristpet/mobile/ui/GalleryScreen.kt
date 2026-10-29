package dev.ashish.wristpet.mobile.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Mood
import dev.ashish.wristpet.pet.Species
import dev.ashish.wristpet.sync.PetCommand
import dev.ashish.wristpet.sync.PetSnapshot

/** Every pet at every stage, animated in the chosen mood, so you see what your pet grows into. */
@Composable
fun GalleryScreen(snapshot: PetSnapshot?, reachable: Boolean, send: SendCommand) {
    val context = LocalContext.current
    var mood by remember { mutableStateOf(Mood.IDLE) }
    var choosing by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val current = snapshot?.state?.speciesId
    // Listing the library scans its folders, so do it once rather than on every recomposition.
    val all = remember { Species.all(context) }
    val shown = remember(all, query) {
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { Species.displayName(it).contains(q, ignoreCase = true) }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search ${all.size} pets") },
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mood.entries.forEach { m ->
                    FilterChip(selected = m == mood, onClick = { mood = m }, label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text("No pets match \"${query.trim()}\".", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 24.dp))
            }
        }
        items(shown, key = { it }) { id ->
            val isCurrent = id == current
            Card(
                colors = if (isCurrent) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)) else CardDefaults.cardColors(),
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Species.displayName(id), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (isCurrent) {
                            OutlinedButton(onClick = {}, enabled = false) { Text("Your pet") }
                        } else {
                            Button(
                                onClick = {
                                    choosing = id
                                    send(PetCommand.Choose(id)) { choosing = null }
                                },
                                enabled = reachable && snapshot != null && choosing == null,
                            ) { Text("Choose") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        (1..GameRules.MAX_STAGE).forEach { stage ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AnimatedPet(id, stage, mood, 88.dp)
                                Text("Stage $stage", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Switching keeps your XP and stage. Stages unlock at ${GameRules.STAGE_XP.joinToString(" and ")} XP.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
