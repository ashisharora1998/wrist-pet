package dev.ashish.wristpet.mobile.data

import androidx.glance.appwidget.updateAll
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.WearableListenerService
import dev.ashish.wristpet.mobile.widget.PetWidget
import kotlinx.coroutines.runBlocking

/** The watch published new state: redraw the home-screen widget. The app UI listens on its own. */
class PhoneSyncService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        // Listener callbacks run on a background thread; the widget update is quick.
        runBlocking { PetWidget().updateAll(this@PhoneSyncService) }
    }
}
