package dev.ashish.wristpet.sync

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.ashish.wristpet.pet.PetRepository
import kotlinx.coroutines.runBlocking
import java.time.ZoneId

/** Applies [PetCommand]s sent by the phone. Unknown commands are ignored. */
class WatchCommandService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PetSync.PATH_COMMAND) return
        val command = PetCommand.decode(event.data) ?: return
        // Listener callbacks run on a background thread and the write takes milliseconds.
        runBlocking {
            PetRepository(this@WatchCommandService).update {
                command.applyTo(it, System.currentTimeMillis(), ZoneId.systemDefault())
            }
        }
    }
}
