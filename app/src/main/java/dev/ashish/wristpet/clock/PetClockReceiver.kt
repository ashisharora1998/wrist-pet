package dev.ashish.wristpet.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.ashish.wristpet.WristPetApp
import dev.ashish.wristpet.health.StepTracking
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.PetRepository
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Handles the pet's alarms, and re-arms everything after a reboot or app update. */
class PetClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val repository = PetRepository(context)
        (context.applicationContext as WristPetApp).scope.launch {
            try {
                when (intent.action) {
                    PetClock.ACTION_NUDGE ->
                        repository.update { GameRules.onNudge(it, System.currentTimeMillis(), ZoneId.systemDefault()) }
                    Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                        StepTracking.registerIfPermitted(context)
                        repository.refresh()
                    }
                    // Mood alarm: time alone changed the mood, and may have started a new day.
                    else -> {
                        repository.update { GameRules.onTick(it, System.currentTimeMillis(), ZoneId.systemDefault()) }
                        repository.refresh()
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
