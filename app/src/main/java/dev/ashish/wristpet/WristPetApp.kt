package dev.ashish.wristpet

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class WristPetApp : Application() {
    /** For short state writes started by services and receivers that outlive their callback. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
