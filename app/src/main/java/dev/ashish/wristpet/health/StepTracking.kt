package dev.ashish.wristpet.health

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig

/**
 * Daily steps come from Health Services' passive monitoring: the system batches step data and
 * wakes [StepsListenerService] on its own schedule, so the app never polls sensors.
 */
object StepTracking {
    const val PERMISSION = Manifest.permission.ACTIVITY_RECOGNITION

    fun isPermitted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun registerIfPermitted(context: Context) {
        if (!isPermitted(context)) return
        val config = PassiveListenerConfig.builder().setDataTypes(setOf(DataType.STEPS_DAILY)).build()
        HealthServices.getClient(context).passiveMonitoringClient
            .setPassiveListenerServiceAsync(StepsListenerService::class.java, config)
            .await()
    }
}
