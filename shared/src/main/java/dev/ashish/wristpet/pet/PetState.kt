package dev.ashish.wristpet.pet

/**
 * Moods the face can animate. [code] is the mood index in the complication value
 * (`speciesIndex * 100 + stage * 10 + code`, so at most 10 moods); it must match the `moods`
 * order in watchface/build.gradle.kts. Each mood plays the `pet_<mood>_NN.png` frames.
 */
enum class Mood(val code: Int) {
    IDLE(0),
    HAPPY(1),
    CURIOUS(2),
    SLEEP(3),
    EVOLVING(4),
    PLAYING(5),
    EATING(6),
    ATTACK(7),
    ANGRY(8),
    SAD(9),
}

/** Night window in local hours; the pet sleeps from [startHour] until [endHour]. */
enum class Bedtime(val startHour: Int?, val endHour: Int?, val label: String) {
    OFF(null, null, "Off"),
    H22(22, 7, "22:00–07:00"),
    H23(23, 7, "23:00–07:00"),
    H00(0, 8, "00:00–08:00"),
}

/** How often the pet gets curious as a gentle hydration / stand reminder. Never a notification. */
enum class Nudge(val minutes: Int, val label: String) {
    OFF(0, "Off"),
    M60(60, "Every hour"),
    M90(90, "Every 90 min"),
    M120(120, "Every 2 hours"),
}

data class PetState(
    val speciesId: String = DEFAULT_SPECIES,
    /** Null means "use the species' display name". */
    val customName: String? = null,
    val xp: Int = 0,
    /** Mood set by an event (petting, steps, nudge) until [eventMoodUntil]; see [GameRules.moodAt]. */
    val eventMood: Mood = Mood.IDLE,
    val eventMoodUntil: Long = 0,
    /** Local date (ISO) the daily counters below belong to. */
    val day: String = "",
    val stepsToday: Long = 0,
    val stepXpToday: Int = 0,
    val petsToday: Int = 0,
    val xpToday: Int = 0,
    val visitedToday: Boolean = false,
    val bedtime: Bedtime = Bedtime.H22,
    val nudge: Nudge = Nudge.OFF,
    /** When the pet last reached a new stage; it shows [Mood.EVOLVING] briefly after. */
    val evolvedAt: Long = 0,
    /** Start of the current burst of taps, and how many taps it has had (for [Mood.ANGRY]). */
    val petBurstStart: Long = 0,
    val petBurstCount: Int = 0,
    /** Today's random snack times (epoch ms); the pet shows [Mood.EATING] at each. */
    val snackTimes: List<Long> = emptyList(),
    /** Finished days, oldest first, at most [GameRules.HISTORY_DAYS]. Today lives in the counters above. */
    val history: List<DayRecord> = emptyList(),
) {
    val stage: Int
        get() = GameRules.stageFor(xp)

    val name: String
        get() = customName ?: Species.displayName(speciesId)

    companion object {
        const val DEFAULT_SPECIES = "sprout"
    }
}

/** One finished day, kept for the phone's history charts. */
data class DayRecord(val date: String, val steps: Long, val xp: Int, val pets: Int) {
    companion object {
        /** Compact text form for storage: `date,steps,xp,pets;...`. */
        fun encode(records: List<DayRecord>): String =
            records.joinToString(";") { "${it.date},${it.steps},${it.xp},${it.pets}" }

        fun decode(text: String?): List<DayRecord> =
            text.orEmpty().split(";").mapNotNull { entry ->
                val parts = entry.split(",")
                if (parts.size != 4) return@mapNotNull null
                val steps = parts[1].toLongOrNull() ?: return@mapNotNull null
                val xp = parts[2].toIntOrNull() ?: return@mapNotNull null
                val pets = parts[3].toIntOrNull() ?: return@mapNotNull null
                DayRecord(parts[0], steps, xp, pets)
            }
    }
}
