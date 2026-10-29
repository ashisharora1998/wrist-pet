package dev.ashish.wristpet.sync

import com.google.android.gms.wearable.DataMap
import dev.ashish.wristpet.pet.Bedtime
import dev.ashish.wristpet.pet.DayRecord
import dev.ashish.wristpet.pet.Mood
import dev.ashish.wristpet.pet.Nudge
import dev.ashish.wristpet.pet.PetState

/**
 * The watch <-> phone contract over the Wear Data Layer. The watch publishes [PATH_STATE] as a
 * DataItem (persisted and queued by the system, so the phone always has the last snapshot); the
 * phone sends [PetCommand]s as messages to [PATH_COMMAND] on nodes with [CAPABILITY_WATCH].
 */
object PetSync {
    const val PATH_STATE = "/pet/state"
    const val PATH_COMMAND = "/pet/command"
    const val CAPABILITY_WATCH = "wristpet_watch"

    fun toDataMap(snapshot: PetSnapshot): DataMap = DataMap().apply {
        val s = snapshot.state
        putString("species", s.speciesId)
        s.customName?.let { putString("custom_name", it) }
        putInt("xp", s.xp)
        putString("event_mood", s.eventMood.name)
        putLong("event_mood_until", s.eventMoodUntil)
        putLong("evolved_at", s.evolvedAt)
        putLongArray("snack_times", s.snackTimes.toLongArray())
        putString("day", s.day)
        putLong("steps_today", s.stepsToday)
        putInt("xp_today", s.xpToday)
        putInt("pets_today", s.petsToday)
        putString("bedtime", s.bedtime.name)
        putString("nudge", s.nudge.name)
        putString("history", DayRecord.encode(s.history))
        putLong("updated_at", snapshot.updatedAt)
    }

    fun fromDataMap(map: DataMap): PetSnapshot {
        val d = PetState()
        val state = PetState(
            speciesId = map.getString("species", d.speciesId),
            customName = map.getString("custom_name"),
            xp = map.getInt("xp", d.xp),
            eventMood = Mood.entries.firstOrNull { it.name == map.getString("event_mood") } ?: d.eventMood,
            eventMoodUntil = map.getLong("event_mood_until", d.eventMoodUntil),
            evolvedAt = map.getLong("evolved_at", d.evolvedAt),
            snackTimes = map.getLongArray("snack_times")?.toList() ?: d.snackTimes,
            day = map.getString("day", d.day),
            stepsToday = map.getLong("steps_today", d.stepsToday),
            xpToday = map.getInt("xp_today", d.xpToday),
            petsToday = map.getInt("pets_today", d.petsToday),
            bedtime = Bedtime.entries.firstOrNull { it.name == map.getString("bedtime") } ?: d.bedtime,
            nudge = Nudge.entries.firstOrNull { it.name == map.getString("nudge") } ?: d.nudge,
            history = DayRecord.decode(map.getString("history")),
        )
        return PetSnapshot(state, map.getLong("updated_at", 0))
    }
}

/**
 * What the phone shows: the watch's state when it last published. The phone derives the current
 * mood with [dev.ashish.wristpet.pet.GameRules.moodAt], so expiring moods stay correct between syncs.
 */
data class PetSnapshot(val state: PetState, val updatedAt: Long)
