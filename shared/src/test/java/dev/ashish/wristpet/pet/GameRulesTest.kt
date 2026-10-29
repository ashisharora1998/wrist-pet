package dev.ashish.wristpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class GameRulesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(hour: Int, minute: Int = 0, day: Int = 29) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val awake = PetState(bedtime = Bedtime.OFF)

    @Test
    fun stagesFollowXpThresholds() {
        assertEquals(1, GameRules.stageFor(0))
        assertEquals(1, GameRules.stageFor(99))
        assertEquals(2, GameRules.stageFor(100))
        assertEquals(3, GameRules.stageFor(400))
        assertEquals(40 to 300, GameRules.stageProgress(140))
        assertEquals(0 to 0, GameRules.stageProgress(999))
    }

    @Test
    fun stepsCreditOnlyNewXpAndCapPerDay() {
        var s = GameRules.onSteps(awake, 450, at(9), zone)
        assertEquals(4, s.xp)
        s = GameRules.onSteps(s, 999, at(10), zone)
        assertEquals(9, s.xp)
        s = GameRules.onSteps(s, 999, at(11), zone) // same total again: nothing new
        assertEquals(9, s.xp)
        s = GameRules.onSteps(s, 1_000_000, at(12), zone)
        assertEquals(GameRules.MAX_STEP_XP_PER_DAY, s.xp)
    }

    @Test
    fun newDayResetsDailyCountersButKeepsXp() {
        var s = GameRules.onSteps(awake, 5_000, at(20), zone)
        val xp = s.xp
        s = GameRules.onSteps(s, 300, at(8, day = 30), zone)
        assertEquals(xp + 3, s.xp)
        assertEquals(300, s.stepsToday)
    }

    @Test
    fun stepMilestoneMakesPetHappy() {
        val s = GameRules.onSteps(awake, 2_100, at(9), zone)
        assertEquals(Mood.HAPPY, GameRules.moodAt(s, at(9, 10), zone))
        assertEquals(Mood.IDLE, GameRules.moodAt(s, at(9, 40), zone))
    }

    @Test
    fun pettingXpIsCappedPerDay() {
        var s = awake
        repeat(15) { s = GameRules.onPet(s, at(9, it * 2), zone) } // spaced out: no grumpiness
        assertEquals(GameRules.MAX_XP_PETS_PER_DAY * GameRules.PET_XP, s.xp)
    }

    @Test
    fun pettingAlternatesHappyAndPlayingForAFewSeconds() {
        var s = GameRules.onPet(awake, at(9), zone)
        assertEquals(Mood.HAPPY, GameRules.moodAt(s, at(9) + 5_000, zone))
        assertEquals(Mood.IDLE, GameRules.moodAt(s, at(9) + 20_000, zone))
        s = GameRules.onPet(s, at(9, 2), zone)
        assertEquals(Mood.PLAYING, GameRules.moodAt(s, at(9, 2) + 5_000, zone))
        assertEquals(at(9, 2) + GameRules.PET_MOOD_SECONDS * 1_000, GameRules.nextMoodChange(s, at(9, 2) + 5_000, zone))
        s = GameRules.onPet(s, at(9, 4), zone)
        assertEquals(Mood.HAPPY, GameRules.moodAt(s, at(9, 4) + 5_000, zone))
    }

    @Test
    fun fiveQuickTapsMakeThePetGrumpyWithoutLosingXp() {
        var s = awake
        repeat(GameRules.ANGRY_TAPS - 1) { s = GameRules.onPet(s, at(9) + it * 5_000L, zone) }
        assertTrue(GameRules.moodAt(s, at(9) + 30_000, zone) != Mood.ANGRY)
        val xp = s.xp
        s = GameRules.onPet(s, at(9) + 25_000, zone)
        assertEquals(Mood.ANGRY, GameRules.moodAt(s, at(9, 1), zone))
        assertTrue(s.xp >= xp)
        assertEquals(Mood.IDLE, GameRules.moodAt(s, at(9, 6), zone))
    }

    @Test
    fun hittingTheStepGoalPowersUp() {
        var s = GameRules.onSteps(awake, 9_000, at(17), zone)
        s = GameRules.onSteps(s, 10_050, at(18), zone)
        assertEquals(Mood.ATTACK, GameRules.moodAt(s, at(18, 10), zone))
        s = GameRules.onSteps(s, 12_100, at(19), zone) // past the goal: back to milestone cheers
        assertEquals(Mood.HAPPY, GameRules.moodAt(s, at(19, 1), zone))
    }

    @Test
    fun snacksAreTwoRandomTimesInTheirWindows() {
        val s = GameRules.onTick(awake, at(7), zone, kotlin.random.Random(42))
        assertEquals(2, s.snackTimes.size)
        val hours = s.snackTimes.map { LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone).hour }
        assertTrue(hours[0] in GameRules.SNACK_WINDOWS[0])
        assertTrue(hours[1] in GameRules.SNACK_WINDOWS[1])
        val lunch = s.snackTimes[0]
        assertEquals(Mood.EATING, GameRules.moodAt(s, lunch + 60_000, zone))
        assertEquals(Mood.IDLE, GameRules.moodAt(s, lunch + 20 * 60_000, zone))
        assertEquals(lunch, GameRules.nextMoodChange(s, at(8), zone))
        // Same day again: snacks stay put.
        assertEquals(s.snackTimes, GameRules.onTick(s, at(9), zone, kotlin.random.Random(7)).snackTimes)
    }

    @Test
    fun visitCountsOncePerDay() {
        var s = GameRules.onVisit(awake, at(9), zone)
        s = GameRules.onVisit(s, at(10), zone)
        assertEquals(GameRules.VISIT_XP, s.xp)
        s = GameRules.onVisit(s, at(9, day = 30), zone)
        assertEquals(2 * GameRules.VISIT_XP, s.xp)
    }

    @Test
    fun evolvingPlaysFirstThenCelebratesForAnHour() {
        val s = GameRules.onSteps(awake.copy(xp = 98), 300, at(9), zone)
        assertEquals(2, s.stage)
        assertEquals(Mood.EVOLVING, GameRules.moodAt(s, at(9, 1), zone))
        assertEquals(Mood.HAPPY, GameRules.moodAt(s, at(9, 50), zone))
        assertEquals(at(9, 2), GameRules.nextMoodChange(s, at(9, 1), zone))
    }

    @Test
    fun sleepWindowCrossesMidnightAndBeatsOtherMoods() {
        val s = GameRules.onPet(PetState(bedtime = Bedtime.H22), at(22, 30), zone)
        assertEquals(Mood.SLEEP, GameRules.moodAt(s, at(22, 31), zone))
        assertTrue(GameRules.isAsleep(Bedtime.H22, at(3), zone))
        assertFalse(GameRules.isAsleep(Bedtime.H22, at(7), zone))
        assertTrue(GameRules.isAsleep(Bedtime.H00, at(0, 30), zone))
        assertFalse(GameRules.isAsleep(Bedtime.OFF, at(3), zone))
    }

    @Test
    fun nudgeMakesPetCuriousOnlyWhileAwake() {
        val s = GameRules.onNudge(PetState(bedtime = Bedtime.H22), at(15), zone)
        assertEquals(Mood.CURIOUS, GameRules.moodAt(s, at(15, 10), zone))
        val asleep = GameRules.onNudge(PetState(bedtime = Bedtime.H22), at(23), zone)
        assertEquals(PetState(bedtime = Bedtime.H22), asleep)
    }

    @Test
    fun nextMoodChangeIsEarliestOfMoodEndAndBedtimeEdges() {
        val s = GameRules.onSteps(PetState(bedtime = Bedtime.H22), 2_100, at(21, 50), zone) // happy for 30 min
        assertEquals(at(22), GameRules.nextMoodChange(s, at(21, 50), zone))
        assertEquals(at(7, day = 30), GameRules.nextMoodChange(PetState(bedtime = Bedtime.H22), at(23), zone))
        assertNull(GameRules.nextMoodChange(awake, at(12), zone))
    }

    @Test
    fun switchingSpeciesKeepsProgressAndRenameIsTrimmed() {
        val s = GameRules.chooseSpecies(awake.copy(xp = 250), "puff")
        assertEquals(250, s.xp)
        assertEquals("Puff", s.name)
        assertEquals("Mochi", GameRules.rename(s, "  Mochi ").name)
        assertEquals("Puff", GameRules.rename(s, "   ").name)
    }

    @Test
    fun finishedDaysMoveIntoHistoryAndKeepThirty() {
        var s = GameRules.onSteps(awake, 1_234, at(20), zone)
        s = GameRules.onPet(s, at(21), zone)
        s = GameRules.onVisit(s, at(8, day = 30), zone)
        assertEquals(listOf(DayRecord("2026-10-29", 1_234, 12 + GameRules.PET_XP, 1)), s.history)
        assertEquals(GameRules.VISIT_XP, s.xpToday)

        repeat(40) { s = GameRules.onVisit(s, at(8, day = 1) + it * 86_400_000L, zone) }
        assertEquals(GameRules.HISTORY_DAYS, s.history.size)
    }

    @Test
    fun historyEncodingRoundTripsAndSkipsGarbage() {
        val records = listOf(DayRecord("2026-10-28", 5_000, 50, 3), DayRecord("2026-10-29", 0, 5, 0))
        assertEquals(records, DayRecord.decode(DayRecord.encode(records)))
        assertEquals(emptyList<DayRecord>(), DayRecord.decode(null))
        assertEquals(records.take(1), DayRecord.decode("2026-10-28,5000,50,3;broken;x,y,z,w"))
    }
}
