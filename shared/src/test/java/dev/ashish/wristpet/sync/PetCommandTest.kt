package dev.ashish.wristpet.sync

import dev.ashish.wristpet.pet.Bedtime
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Nudge
import dev.ashish.wristpet.pet.PetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class PetCommandTest {
    @Test
    fun everyCommandRoundTrips() {
        listOf(
            PetCommand.Choose("puff"),
            PetCommand.Rename("Mochi the 2nd"),
            PetCommand.SetBedtime(Bedtime.H23),
            PetCommand.SetNudge(Nudge.M90),
            PetCommand.Pet,
        ).forEach { assertEquals(it, PetCommand.decode(PetCommand.encode(it))) }
    }

    @Test
    fun unknownOrMalformedCommandsAreIgnored() {
        assertNull(PetCommand.decode("feed\u0000cake".toByteArray()))
        assertNull(PetCommand.decode("bedtime\u0000NOON".toByteArray()))
        assertNull(PetCommand.decode("choose\u0000".toByteArray()))
        assertNull(PetCommand.decode(ByteArray(0)))
    }

    @Test
    fun petFromPhoneUsesTheSameDailyCap() {
        val zone = ZoneId.of("UTC")
        var s = PetState(bedtime = Bedtime.OFF)
        repeat(20) { s = PetCommand.Pet.applyTo(s, 1_000L, zone) }
        assertEquals(GameRules.MAX_XP_PETS_PER_DAY * GameRules.PET_XP, s.xp)
    }
}
