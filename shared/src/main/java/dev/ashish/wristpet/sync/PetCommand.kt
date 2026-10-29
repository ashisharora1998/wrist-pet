package dev.ashish.wristpet.sync

import dev.ashish.wristpet.pet.Bedtime
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Nudge
import dev.ashish.wristpet.pet.PetState
import java.time.ZoneId

/** Changes the phone asks the watch to make. The watch applies them; it owns the game. */
sealed interface PetCommand {
    data class Choose(val speciesId: String) : PetCommand
    data class Rename(val name: String) : PetCommand
    data class SetBedtime(val bedtime: Bedtime) : PetCommand
    data class SetNudge(val nudge: Nudge) : PetCommand
    data object Pet : PetCommand

    fun applyTo(state: PetState, now: Long, zone: ZoneId): PetState = when (this) {
        is Choose -> GameRules.chooseSpecies(state, speciesId)
        is Rename -> GameRules.rename(state, name)
        is SetBedtime -> state.copy(bedtime = bedtime)
        is SetNudge -> state.copy(nudge = nudge)
        Pet -> GameRules.onPet(state, now, zone)
    }

    companion object {
        private const val SEPARATOR = '\u0000'

        /** Message payload: `type<NUL>argument`, UTF-8. */
        fun encode(command: PetCommand): ByteArray {
            val (type, arg) = when (command) {
                is Choose -> "choose" to command.speciesId
                is Rename -> "rename" to command.name
                is SetBedtime -> "bedtime" to command.bedtime.name
                is SetNudge -> "nudge" to command.nudge.name
                Pet -> "pet" to ""
            }
            return "$type$SEPARATOR$arg".toByteArray(Charsets.UTF_8)
        }

        /** Null for anything malformed or unknown, so a newer phone can't crash an older watch. */
        fun decode(bytes: ByteArray): PetCommand? {
            val text = bytes.toString(Charsets.UTF_8)
            val type = text.substringBefore(SEPARATOR)
            val arg = text.substringAfter(SEPARATOR, "")
            return when (type) {
                "choose" -> arg.takeIf { it.isNotBlank() }?.let(::Choose)
                "rename" -> Rename(arg)
                "bedtime" -> Bedtime.entries.firstOrNull { it.name == arg }?.let(::SetBedtime)
                "nudge" -> Nudge.entries.firstOrNull { it.name == arg }?.let(::SetNudge)
                "pet" -> Pet
                else -> null
            }
        }
    }
}
