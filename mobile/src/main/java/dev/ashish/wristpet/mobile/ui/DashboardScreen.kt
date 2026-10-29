package dev.ashish.wristpet.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Species
import dev.ashish.wristpet.sync.PetCommand
import dev.ashish.wristpet.sync.PetSnapshot
import java.time.ZoneId

@Composable
fun DashboardScreen(snapshot: PetSnapshot?, reachable: Boolean, send: SendCommand) {
    if (snapshot == null) return EmptyState(NO_SYNC_MESSAGE)
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    val pet = snapshot.state
    val mood = GameRules.moodAt(pet, now, zone)
    val (earned, needed) = GameRules.stageProgress(pet.xp)
    // Counters belong to the watch's current day; if it hasn't rolled over yet, today is still empty.
    val isToday = pet.day == GameRules.today(now, zone)
    var sending by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionChip(reachable, snapshot.updatedAt, now)
        AnimatedPet(pet.speciesId, pet.stage, mood, 192.dp)
        Text(pet.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "${Species.displayName(pet.speciesId)} · Stage ${pet.stage} · ${mood.name.lowercase()}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (needed > 0) {
            LinearProgressIndicator(progress = { earned.toFloat() / needed }, modifier = Modifier.fillMaxWidth(0.8f))
            Text("$earned / $needed XP to stage ${pet.stage + 1}", style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Fully grown · ${pet.xp} XP", style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("Steps", if (isToday) "%,d".format(pet.stepsToday) else "0", Modifier.weight(1f))
            Stat("XP today", if (isToday) "${pet.xpToday}" else "0", Modifier.weight(1f))
            Stat("Pets", if (isToday) "${pet.petsToday}" else "0", Modifier.weight(1f))
        }
        Button(
            onClick = {
                sending = true
                send(PetCommand.Pet) { sending = false }
            },
            enabled = reachable && !sending,
            modifier = Modifier.fillMaxWidth(0.8f),
        ) {
            Text("Pet ${pet.name}")
        }
        if (!reachable) {
            Text("Bring your watch nearby to pet or change settings.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
    }
}
