package dev.ashish.wristpet

import android.app.Activity
import android.os.Bundle
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.PetRepository
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Launched by the pet complication's tap action: pets the pet and closes at once, so a tap on the
 * face never opens the app. It's an invisible activity (Theme.NoDisplay) rather than a broadcast
 * because the watch face only fires activity tap actions.
 */
class PetTapActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as WristPetApp).scope.launch {
            PetRepository(this@PetTapActivity).update { GameRules.onPet(it, System.currentTimeMillis(), ZoneId.systemDefault()) }
        }
        finish()
    }
}
