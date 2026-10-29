package dev.ashish.wristpet

import android.app.RemoteInput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.wear.input.RemoteInputIntentHelper
import dev.ashish.wristpet.health.StepTracking
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.PetRepository
import dev.ashish.wristpet.pet.PetState
import dev.ashish.wristpet.ui.PetApp
import kotlinx.coroutines.launch
import java.time.ZoneId

/** The pet screen. All game state changes go through [PetRepository.update] and [GameRules]. */
class PetActivity : ComponentActivity() {

    private val repository by lazy { PetRepository(this) }

    private val stepsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) lifecycleScope.launch { StepTracking.registerIfPermitted(this@PetActivity) }
    }

    private val renameInput = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val name = result.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(KEY_NAME) } ?: return@registerForActivityResult
        change { GameRules.rename(it, name.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        change { GameRules.onVisit(it, now(), zone()) }
        lifecycleScope.launch { StepTracking.registerIfPermitted(this@PetActivity) }

        setContent {
            PetApp(
                states = repository.state,
                stepsPermitted = { StepTracking.isPermitted(this) },
                onPet = { change { GameRules.onPet(it, now(), zone()) } },
                onChooseSpecies = { id -> change { GameRules.chooseSpecies(it, id) } },
                onRename = ::askForName,
                onBedtime = { bedtime -> change { it.copy(bedtime = bedtime) } },
                onNudge = { nudge -> change { it.copy(nudge = nudge) } },
                onEnableSteps = { stepsPermission.launch(StepTracking.PERMISSION) },
            )
        }
    }

    private fun askForName() {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(KEY_NAME).setLabel(getString(R.string.rename_label)).build()))
        renameInput.launch(intent)
    }

    private fun change(transform: (PetState) -> PetState) {
        lifecycleScope.launch { repository.update(transform) }
    }

    private fun now() = System.currentTimeMillis()
    private fun zone() = ZoneId.systemDefault()

    private companion object {
        const val KEY_NAME = "pet_name"
    }
}
