package dev.ashish.wristpet.mobile.data

import android.content.Context
import android.net.Uri
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import dev.ashish.wristpet.sync.PetCommand
import dev.ashish.wristpet.sync.PetSnapshot
import dev.ashish.wristpet.sync.PetSync
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The phone's view of the pet. It never owns game state: it reads the snapshot the watch
 * publishes (kept locally by the Data Layer, so it works offline) and sends commands back.
 */
class PhoneRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dataClient = Wearable.getDataClient(appContext)
    private val capabilityClient = Wearable.getCapabilityClient(appContext)
    private val messageClient = Wearable.getMessageClient(appContext)

    /** Last snapshot from the watch, then every new one; null until the watch has published once. */
    val snapshots: Flow<PetSnapshot?> = callbackFlow {
        val listener = DataClient.OnDataChangedListener { events ->
            events.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == PetSync.PATH_STATE }
                .lastOrNull()
                ?.let { trySend(PetSync.fromDataMap(DataMapItem.fromDataItem(it.dataItem).dataMap)) }
        }
        dataClient.addListener(listener).await()
        send(latest())
        awaitClose { dataClient.removeListener(listener) }
    }

    /** True while a watch with WristPet installed is reachable right now. */
    val watchReachable: Flow<Boolean> = callbackFlow {
        val listener = CapabilityClient.OnCapabilityChangedListener { info: CapabilityInfo -> trySend(info.nodes.isNotEmpty()) }
        capabilityClient.addListener(listener, PetSync.CAPABILITY_WATCH).await()
        send(reachableWatchIds().isNotEmpty())
        awaitClose { capabilityClient.removeListener(listener) }
    }

    suspend fun latest(): PetSnapshot? {
        val items = dataClient.getDataItems(STATE_URI).await()
        try {
            return items.maxByOrNull { DataMapItem.fromDataItem(it).dataMap.getLong("updated_at", 0) }
                ?.let { PetSync.fromDataMap(DataMapItem.fromDataItem(it).dataMap) }
        } finally {
            items.release()
        }
    }

    /** Sends a command to every reachable watch; false if none was reachable or delivery failed. */
    suspend fun send(command: PetCommand): Boolean {
        val payload = PetCommand.encode(command)
        val sent = reachableWatchIds().map { id ->
            runCatching { messageClient.sendMessage(id, PetSync.PATH_COMMAND, payload).await() }.isSuccess
        }
        return sent.any { it }
    }

    private suspend fun reachableWatchIds(): List<String> =
        runCatching {
            capabilityClient.getCapability(PetSync.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE).await().nodes.map { it.id }
        }.getOrDefault(emptyList())

    private companion object {
        val STATE_URI: Uri = Uri.parse("wear://*${PetSync.PATH_STATE}")
    }
}
