package dev.ashish.wristpet.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import dev.ashish.wristpet.mobile.data.PhoneRepository
import dev.ashish.wristpet.mobile.ui.PhoneApp
import dev.ashish.wristpet.sync.PetCommand
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository by lazy { PhoneRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhoneApp(
                snapshots = repository.snapshots,
                watchReachable = repository.watchReachable,
                send = { command: PetCommand, onResult: (Boolean) -> Unit ->
                    lifecycleScope.launch { onResult(repository.send(command)) }
                },
            )
        }
    }
}
