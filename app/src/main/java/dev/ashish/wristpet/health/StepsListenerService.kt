package dev.ashish.wristpet.health

import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import dev.ashish.wristpet.WristPetApp
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.PetRepository
import kotlinx.coroutines.launch
import java.time.ZoneId

class StepsListenerService : PassiveListenerService() {
    override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
        val steps = dataPoints.getData(DataType.STEPS_DAILY).maxOfOrNull { it.value } ?: return
        val repository = PetRepository(this)
        (application as WristPetApp).scope.launch {
            repository.update { GameRules.onSteps(it, steps, System.currentTimeMillis(), ZoneId.systemDefault()) }
        }
    }
}
