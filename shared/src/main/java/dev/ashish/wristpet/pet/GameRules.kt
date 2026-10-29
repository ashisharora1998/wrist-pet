package dev.ashish.wristpet.pet

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

/**
 * The whole game, as pure functions of state and time. Product rules: progress only ever goes up;
 * no death, illness, lost levels, streaks or guilt. Missing a day costs nothing.
 */
object GameRules {
    /** XP needed to reach each stage after the first. */
    val STAGE_XP = listOf(100, 400)
    const val MAX_STAGE = 3

    const val STEPS_PER_XP = 100
    const val MAX_STEP_XP_PER_DAY = 150
    const val STEP_MILESTONE = 2_000
    const val PET_XP = 2
    const val MAX_XP_PETS_PER_DAY = 10
    const val VISIT_XP = 5

    const val HAPPY_MINUTES = 30L
    /** A tap's happy or playful reaction is brief, then the pet settles back to idle. */
    const val PET_MOOD_SECONDS = 15L
    const val EVOLVE_HAPPY_MINUTES = 60L
    const val EVOLVING_MINUTES = 2L
    const val CURIOUS_MINUTES = 15L

    /** Hitting the daily step goal is a "power-up". */
    const val DAILY_STEP_GOAL = 10_000L
    const val ATTACK_MINUTES = 15L

    /** This many taps within [ANGRY_WINDOW_MS] makes the pet grumpy for a bit (no XP is lost). */
    const val ANGRY_TAPS = 5
    const val ANGRY_WINDOW_MS = 60_000L
    const val ANGRY_MINUTES = 5L

    /** Two random snacks a day, one in each window (local hours), each [EATING_MINUTES] long. */
    val SNACK_WINDOWS = listOf(11..13, 17..19)
    const val EATING_MINUTES = 15L

    const val HISTORY_DAYS = 30

    fun stageFor(xp: Int): Int = 1 + STAGE_XP.count { xp >= it }

    /** XP progress within the current stage, as (earned, needed); needed is 0 at the final stage. */
    fun stageProgress(xp: Int): Pair<Int, Int> {
        val stage = stageFor(xp)
        if (stage >= MAX_STAGE) return 0 to 0
        val floor = if (stage == 1) 0 else STAGE_XP[stage - 2]
        return (xp - floor) to (STAGE_XP[stage - 1] - floor)
    }

    /** Daily step total from Health Services. Credits XP, cheers on milestones, powers up at the goal. */
    fun onSteps(state: PetState, stepsToday: Long, now: Long, zone: ZoneId, random: Random = Random): PetState {
        val s = rollover(state, now, zone, random)
        if (stepsToday <= s.stepsToday) return s
        val earned = (stepsToday / STEPS_PER_XP).toInt().coerceAtMost(MAX_STEP_XP_PER_DAY)
        var next = addXp(s.copy(stepsToday = stepsToday, stepXpToday = earned), earned - s.stepXpToday, now)
        next = when {
            s.stepsToday < DAILY_STEP_GOAL && stepsToday >= DAILY_STEP_GOAL -> withMood(next, Mood.ATTACK, ATTACK_MINUTES, now)
            stepsToday / STEP_MILESTONE > s.stepsToday / STEP_MILESTONE -> withMood(next, Mood.HAPPY, HAPPY_MINUTES, now)
            else -> next
        }
        return next
    }

    /**
     * A tap on the pet. It's happy or playing (alternating) for [PET_MOOD_SECONDS]; [ANGRY_TAPS] taps within a minute
     * make it grumpy instead. XP is capped per day so tapping isn't a grind, and is never taken away.
     */
    fun onPet(state: PetState, now: Long, zone: ZoneId, random: Random = Random): PetState {
        val s = rollover(state, now, zone, random)
        val xp = if (s.petsToday < MAX_XP_PETS_PER_DAY) PET_XP else 0
        val inBurst = now - s.petBurstStart < ANGRY_WINDOW_MS
        val burst = if (inBurst) s.copy(petBurstCount = s.petBurstCount + 1) else s.copy(petBurstStart = now, petBurstCount = 1)
        val next = addXp(burst.copy(petsToday = s.petsToday + 1), xp, now)
        return when {
            next.petBurstCount >= ANGRY_TAPS -> withMood(next, Mood.ANGRY, ANGRY_MINUTES, now)
            next.petsToday % 2 == 0 -> withMoodFor(next, Mood.PLAYING, PET_MOOD_SECONDS * 1_000, now)
            else -> withMoodFor(next, Mood.HAPPY, PET_MOOD_SECONDS * 1_000, now)
        }
    }

    /** First time the pet screen is opened each day. */
    fun onVisit(state: PetState, now: Long, zone: ZoneId, random: Random = Random): PetState {
        val s = rollover(state, now, zone, random)
        return if (s.visitedToday) s else addXp(s.copy(visitedToday = true), VISIT_XP, now)
    }

    /** A gentle reminder: the pet gets curious for a while. Ignored while it sleeps. */
    fun onNudge(state: PetState, now: Long, zone: ZoneId): PetState =
        if (isAsleep(state.bedtime, now, zone)) state else withMood(state, Mood.CURIOUS, CURIOUS_MINUTES, now)

    /** Time passing on its own (the mood alarm): starts a new day, with new snack times, when due. */
    fun onTick(state: PetState, now: Long, zone: ZoneId, random: Random = Random): PetState = rollover(state, now, zone, random)

    /** Switching pets keeps all progress; a default name follows the new species. */
    fun chooseSpecies(state: PetState, speciesId: String): PetState = state.copy(speciesId = speciesId)

    fun rename(state: PetState, name: String): PetState =
        state.copy(customName = name.trim().take(MAX_NAME_LENGTH).ifEmpty { null })

    /** Priority: sleep, then a fresh evolution, then the latest event mood, then a snack, then idle. */
    fun moodAt(state: PetState, now: Long, zone: ZoneId): Mood = when {
        isAsleep(state.bedtime, now, zone) -> Mood.SLEEP
        state.evolvedAt > 0 && now - state.evolvedAt in 0 until EVOLVING_MINUTES * 60_000 -> Mood.EVOLVING
        now < state.eventMoodUntil -> state.eventMood
        state.snackTimes.any { now - it in 0 until EATING_MINUTES * 60_000 } -> Mood.EATING
        else -> Mood.IDLE
    }

    /** The next moment [moodAt] can change on its own, so one alarm can be set for it; null if never. */
    fun nextMoodChange(state: PetState, now: Long, zone: ZoneId): Long? {
        val candidates = mutableListOf<Long>()
        if (state.eventMoodUntil > now) candidates += state.eventMoodUntil
        val evolvedEnd = state.evolvedAt + EVOLVING_MINUTES * 60_000
        if (state.evolvedAt > 0 && evolvedEnd > now) candidates += evolvedEnd
        for (snack in state.snackTimes) {
            if (snack > now) candidates += snack
            if (snack + EATING_MINUTES * 60_000 > now) candidates += snack + EATING_MINUTES * 60_000
        }
        val start = state.bedtime.startHour
        val end = state.bedtime.endHour
        if (start != null && end != null) {
            candidates += nextHour(start, now, zone)
            candidates += nextHour(end, now, zone)
        }
        return candidates.minOrNull()
    }

    fun isAsleep(bedtime: Bedtime, now: Long, zone: ZoneId): Boolean {
        val start = bedtime.startHour ?: return false
        val end = bedtime.endHour ?: return false
        val hour = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).hour
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    fun today(now: Long, zone: ZoneId): String = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).toString()

    const val MAX_NAME_LENGTH = 12

    /**
     * Starts a new day: the finished day moves into [PetState.history], the daily counters reset,
     * and two random snack times are picked for the new day.
     */
    private fun rollover(state: PetState, now: Long, zone: ZoneId, random: Random): PetState {
        val today = today(now, zone)
        if (state.day == today) return state
        val history = if (state.day.isEmpty()) {
            state.history
        } else {
            (state.history + DayRecord(state.day, state.stepsToday, state.xpToday, state.petsToday)).takeLast(HISTORY_DAYS)
        }
        return state.copy(
            day = today,
            stepsToday = 0,
            stepXpToday = 0,
            petsToday = 0,
            xpToday = 0,
            visitedToday = false,
            history = history,
            snackTimes = snackTimes(today, zone, random),
        )
    }

    private fun snackTimes(day: String, zone: ZoneId, random: Random): List<Long> =
        SNACK_WINDOWS.map { hours ->
            LocalDate.parse(day).atTime(hours.first, 0).plusMinutes(random.nextLong((hours.last - hours.first + 1) * 60L))
                .atZone(zone).toInstant().toEpochMilli()
        }

    private fun addXp(state: PetState, amount: Int, now: Long): PetState {
        if (amount <= 0) return state
        val next = state.copy(xp = state.xp + amount, xpToday = state.xpToday + amount)
        // Evolving plays first (see moodAt), then a long happy celebration.
        return if (next.stage > state.stage) withMood(next.copy(evolvedAt = now), Mood.HAPPY, EVOLVE_HAPPY_MINUTES, now) else next
    }

    private fun withMood(state: PetState, mood: Mood, minutes: Long, now: Long): PetState = withMoodFor(state, mood, minutes * 60_000, now)

    private fun withMoodFor(state: PetState, mood: Mood, durationMs: Long, now: Long): PetState {
        val until = now + durationMs
        // Never cut a longer celebration short.
        return if (state.eventMood == mood && state.eventMoodUntil > until) state else state.copy(eventMood = mood, eventMoodUntil = until)
    }

    private fun nextHour(hour: Int, now: Long, zone: ZoneId): Long {
        val current = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        var at = current.toLocalDate().atTime(hour, 0)
        if (!at.isAfter(current)) at = at.plusDays(1)
        return at.atZone(zone).toInstant().toEpochMilli()
    }
}
