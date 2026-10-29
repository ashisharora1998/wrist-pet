package dev.ashish.wristpet.pet

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dev.ashish.wristpet.clock.PetClock
import dev.ashish.wristpet.complication.PetComplicationService
import dev.ashish.wristpet.sync.PetSnapshot
import dev.ashish.wristpet.sync.PetSync
import dev.ashish.wristpet.tile.PetTileService
import java.time.ZoneId

/**
 * Pushes a state change to everything that shows the pet (face, tile, phone) and re-arms the
 * time-based alarms.
 */
object PetSignals {
    fun publish(context: Context, before: PetState?, after: PetState) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val faceChanged = before == null ||
            before.speciesId != after.speciesId ||
            before.stage != after.stage ||
            GameRules.moodAt(before, now, zone) != GameRules.moodAt(after, now, zone)
        if (faceChanged) {
            ComplicationDataSourceUpdateRequester
                .create(context, ComponentName(context, PetComplicationService::class.java))
                .requestUpdateAll()
        }
        if (before != after || faceChanged) {
            TileService.getUpdater(context).requestUpdate(PetTileService::class.java)
            publishToPhone(context, after, now, urgent = faceChanged || !onlyCountersChanged(before, after))
        }
        PetClock.scheduleMood(context, after, now, zone)
        if (before == null || before.nudge != after.nudge) PetClock.scheduleNudge(context, after.nudge, now)
    }

    /**
     * The phone reads this DataItem; the system persists it and syncs when connected. Step batches
     * aren't urgent, so the Data Layer can bundle them; anything the user did syncs right away.
     */
    private fun publishToPhone(context: Context, state: PetState, now: Long, urgent: Boolean) {
        val request = PutDataMapRequest.create(PetSync.PATH_STATE).apply {
            dataMap.putAll(PetSync.toDataMap(PetSnapshot(state, now)))
        }.asPutDataRequest()
        if (urgent) request.setUrgent()
        Wearable.getDataClient(context).putDataItem(request)
    }

    private fun onlyCountersChanged(before: PetState?, after: PetState): Boolean =
        before != null && before.copy(stepsToday = after.stepsToday, stepXpToday = after.stepXpToday, xp = after.xp, xpToday = after.xpToday) == after
}
