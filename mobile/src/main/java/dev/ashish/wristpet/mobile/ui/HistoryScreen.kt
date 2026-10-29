package dev.ashish.wristpet.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ashish.wristpet.pet.DayRecord
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.PetState
import dev.ashish.wristpet.sync.PetSnapshot
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(snapshot: PetSnapshot?) {
    if (snapshot == null) return EmptyState(NO_SYNC_MESSAGE)
    val now = rememberNow()
    val days = lastDays(snapshot.state, LocalDate.parse(GameRules.today(now, ZoneId.systemDefault())))
    val active = days.count { it.steps > 0 || it.pets > 0 || it.xp > 0 }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Last ${GameRules.HISTORY_DAYS} days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Active on $active days. There are no streaks to keep — every day just adds up.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Chart("Steps", "%,d total".format(days.sumOf { it.steps }), days.map { it.steps.toFloat() }, MaterialTheme.colorScheme.primary, days)
        Chart("XP earned", "${days.sumOf { it.xp }} total", days.map { it.xp.toFloat() }, MaterialTheme.colorScheme.secondary, days)
        Chart("Pets", "${days.sumOf { it.pets }} total", days.map { it.pets.toFloat() }, MaterialTheme.colorScheme.tertiary, days)
    }
}

/** The last [GameRules.HISTORY_DAYS] days ending today, with quiet days filled in as zeros. */
private fun lastDays(state: PetState, today: LocalDate): List<DayRecord> {
    val byDate = state.history.associateBy { it.date }.toMutableMap()
    byDate[state.day] = DayRecord(state.day, state.stepsToday, state.xpToday, state.petsToday)
    return (GameRules.HISTORY_DAYS - 1 downTo 0).map { back ->
        val date = today.minusDays(back.toLong()).toString()
        byDate[date] ?: DayRecord(date, 0, 0, 0)
    }
}

@Composable
private fun Chart(title: String, summary: String, values: List<Float>, color: Color, days: List<DayRecord>) {
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1f
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
            Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                val slot = size.width / values.size
                val barWidth = slot * 0.7f
                values.forEachIndexed { i, v ->
                    val x = i * slot + (slot - barWidth) / 2
                    drawRoundRect(track, Offset(x, 0f), Size(barWidth, size.height), CornerRadius(barWidth / 2))
                    val h = size.height * (v / max)
                    if (h > 0) {
                        // Today is drawn fully opaque; earlier days slightly muted.
                        val alpha = if (i == values.lastIndex) 1f else 0.75f
                        drawRoundRect(color.copy(alpha = alpha), Offset(x, size.height - h), Size(barWidth, h), CornerRadius(barWidth / 2))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(shortDate(days.first().date), style = MaterialTheme.typography.labelSmall)
                Text("Today", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun shortDate(iso: String): String = LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("d MMM"))
