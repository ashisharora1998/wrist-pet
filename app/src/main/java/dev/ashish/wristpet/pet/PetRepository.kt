package dev.ashish.wristpet.pet

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.petDataStore: DataStore<Preferences> by preferencesDataStore(name = "pet")

/** Single owner of pet state. Every change is published to the face, tile and alarms; nothing polls. */
class PetRepository(context: Context) {
    private val appContext = context.applicationContext
    private val store = appContext.petDataStore

    val state: Flow<PetState> = store.data.map(::stateOf)

    suspend fun current(): PetState = state.first()

    suspend fun update(transform: (PetState) -> PetState): PetState {
        lateinit var before: PetState
        lateinit var after: PetState
        store.edit { prefs ->
            before = stateOf(prefs)
            after = transform(before)
            write(prefs, after)
        }
        PetSignals.publish(appContext, before, after)
        return after
    }

    /** Re-publish without a state change, e.g. when time alone changed the mood. */
    suspend fun refresh() {
        val state = current()
        PetSignals.publish(appContext, null, state)
    }

    private fun stateOf(prefs: Preferences): PetState {
        val d = PetState()
        return PetState(
            speciesId = prefs[SPECIES] ?: d.speciesId,
            customName = prefs[CUSTOM_NAME],
            xp = prefs[XP] ?: d.xp,
            eventMood = enumOr(prefs[EVENT_MOOD], d.eventMood),
            eventMoodUntil = prefs[EVENT_MOOD_UNTIL] ?: d.eventMoodUntil,
            day = prefs[DAY] ?: d.day,
            stepsToday = prefs[STEPS_TODAY] ?: d.stepsToday,
            stepXpToday = prefs[STEP_XP_TODAY] ?: d.stepXpToday,
            petsToday = prefs[PETS_TODAY] ?: d.petsToday,
            xpToday = prefs[XP_TODAY] ?: d.xpToday,
            visitedToday = prefs[VISITED_TODAY] ?: d.visitedToday,
            bedtime = enumOr(prefs[BEDTIME], d.bedtime),
            nudge = enumOr(prefs[NUDGE], d.nudge),
            evolvedAt = prefs[EVOLVED_AT] ?: d.evolvedAt,
            petBurstStart = prefs[PET_BURST_START] ?: d.petBurstStart,
            petBurstCount = prefs[PET_BURST_COUNT] ?: d.petBurstCount,
            snackTimes = prefs[SNACK_TIMES].orEmpty().split(",").mapNotNull { it.toLongOrNull() },
            history = DayRecord.decode(prefs[HISTORY]),
        )
    }

    private fun write(prefs: androidx.datastore.preferences.core.MutablePreferences, s: PetState) {
        prefs[SPECIES] = s.speciesId
        val customName = s.customName
        if (customName == null) prefs.remove(CUSTOM_NAME) else prefs[CUSTOM_NAME] = customName
        prefs[XP] = s.xp
        prefs[EVENT_MOOD] = s.eventMood.name
        prefs[EVENT_MOOD_UNTIL] = s.eventMoodUntil
        prefs[DAY] = s.day
        prefs[STEPS_TODAY] = s.stepsToday
        prefs[STEP_XP_TODAY] = s.stepXpToday
        prefs[PETS_TODAY] = s.petsToday
        prefs[XP_TODAY] = s.xpToday
        prefs[VISITED_TODAY] = s.visitedToday
        prefs[BEDTIME] = s.bedtime.name
        prefs[NUDGE] = s.nudge.name
        prefs[EVOLVED_AT] = s.evolvedAt
        prefs[PET_BURST_START] = s.petBurstStart
        prefs[PET_BURST_COUNT] = s.petBurstCount
        prefs[SNACK_TIMES] = s.snackTimes.joinToString(",")
        prefs[HISTORY] = DayRecord.encode(s.history)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        val SPECIES = stringPreferencesKey("species")
        val CUSTOM_NAME = stringPreferencesKey("custom_name")
        val XP = intPreferencesKey("xp")
        val EVENT_MOOD = stringPreferencesKey("event_mood")
        val EVENT_MOOD_UNTIL = longPreferencesKey("event_mood_until")
        val DAY = stringPreferencesKey("day")
        val STEPS_TODAY = longPreferencesKey("steps_today")
        val STEP_XP_TODAY = intPreferencesKey("step_xp_today")
        val PETS_TODAY = intPreferencesKey("pets_today")
        val XP_TODAY = intPreferencesKey("xp_today")
        val VISITED_TODAY = booleanPreferencesKey("visited_today")
        val BEDTIME = stringPreferencesKey("bedtime")
        val NUDGE = stringPreferencesKey("nudge")
        val HISTORY = stringPreferencesKey("history")
        val EVOLVED_AT = longPreferencesKey("evolved_at")
        val PET_BURST_START = longPreferencesKey("pet_burst_start")
        val PET_BURST_COUNT = intPreferencesKey("pet_burst_count")
        val SNACK_TIMES = stringPreferencesKey("snack_times")
    }
}
