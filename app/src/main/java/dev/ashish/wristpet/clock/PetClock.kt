package dev.ashish.wristpet.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.ashish.wristpet.WristPetApp
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Nudge
import dev.ashish.wristpet.pet.PetRepository
import dev.ashish.wristpet.pet.PetState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * The only timers in the app: one inexact alarm for the next time-driven mood change (a mood
 * wearing off, bedtime, wake-up) and an optional inexact repeating nudge. Both are batched by the
 * system, so they cost almost nothing. Moods that end within a minute also get an in-process refresh.
 */
object PetClock {
    const val ACTION_MOOD = "dev.ashish.wristpet.action.MOOD"
    const val ACTION_NUDGE = "dev.ashish.wristpet.action.NUDGE"

    fun scheduleMood(context: Context, state: PetState, now: Long, zone: ZoneId) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = pending(context, ACTION_MOOD)
        val at = GameRules.nextMoodChange(state, now, zone)
        if (at == null) {
            alarms.cancel(intent)
            return
        }
        alarms.set(AlarmManager.RTC, at, intent)
        // Inexact alarms can be minutes late, too late for a few-second reaction (a tap's happy or
        // playful). The app was just woken for that change, so refresh in-process on time; the
        // alarm stays as the fallback if the process is gone by then.
        if (at - now <= SHORT_MOOD_MS) {
            val app = context.applicationContext as WristPetApp
            shortMoodRefresh?.cancel()
            shortMoodRefresh = app.scope.launch {
                delay(at - now + 250)
                PetRepository(app).refresh()
            }
        }
    }

    private const val SHORT_MOOD_MS = 60_000L
    private var shortMoodRefresh: Job? = null

    fun scheduleNudge(context: Context, nudge: Nudge, now: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = pending(context, ACTION_NUDGE)
        if (nudge == Nudge.OFF) {
            alarms.cancel(intent)
        } else {
            val interval = nudge.minutes * 60_000L
            alarms.setInexactRepeating(AlarmManager.RTC, now + interval, interval, intent)
        }
    }

    private fun pending(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, PetClockReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
