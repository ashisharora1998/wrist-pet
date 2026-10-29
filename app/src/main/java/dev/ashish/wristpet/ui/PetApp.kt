package dev.ashish.wristpet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import dev.ashish.wristpet.pet.Bedtime
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Mood
import dev.ashish.wristpet.pet.Nudge
import dev.ashish.wristpet.pet.PetSprites
import dev.ashish.wristpet.pet.PetState
import dev.ashish.wristpet.pet.Species
import dev.ashish.wristpet.pet.Sprite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId

private val Mint = Color(0xFF7FE0B0)
private val Cream = Color(0xFFF2EBD9)
private val Lavender = Color(0xFFB9A6F2)
private val Ink = Color(0xFF1A1C1E)
private val Track = Color(0xFF2A2D30)

private val PetColors = Colors(
    primary = Mint,
    onPrimary = Ink,
    secondary = Lavender,
    onSecondary = Ink,
    surface = Ink,
    onSurface = Cream,
    onSurfaceVariant = Lavender,
)

@Composable
fun PetApp(
    states: Flow<PetState>,
    stepsPermitted: () -> Boolean,
    onPet: () -> Unit,
    onChooseSpecies: (String) -> Unit,
    onRename: () -> Unit,
    onBedtime: (Bedtime) -> Unit,
    onNudge: (Nudge) -> Unit,
    onEnableSteps: () -> Unit,
) {
    val state by states.collectAsStateWithLifecycle(initialValue = null)
    val nav = rememberSwipeDismissableNavController()
    MaterialTheme(colors = PetColors) {
        val pet = state ?: return@MaterialTheme
        SwipeDismissableNavHost(navController = nav, startDestination = "home") {
            composable("home") {
                HomeScreen(pet, stepsPermitted(), onPet, { nav.navigate("pets") }, onRename, { nav.navigate("settings") }, onEnableSteps)
            }
            composable("pets") {
                PickerScreen(pet) { id -> onChooseSpecies(id); nav.popBackStack() }
            }
            composable("settings") {
                SettingsScreen(pet, onBedtime, onNudge)
            }
        }
    }
}

@Composable
private fun PetList(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun HomeScreen(
    pet: PetState,
    stepsPermitted: Boolean,
    onPet: () -> Unit,
    onChoosePet: () -> Unit,
    onRename: () -> Unit,
    onSettings: () -> Unit,
    onEnableSteps: () -> Unit,
) = PetList {
    val mood = GameRules.moodAt(pet, System.currentTimeMillis(), ZoneId.systemDefault())
    val (earned, needed) = GameRules.stageProgress(pet.xp)
    item {
        Box(
            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPet),
        ) {
            AnimatedPet(pet, mood, 96.dp)
        }
    }
    item {
        Text(pet.name, style = MaterialTheme.typography.title2, color = Cream, fontWeight = FontWeight.Bold)
    }
    item {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (needed == 0) "Stage ${pet.stage} · fully grown" else "Stage ${pet.stage} · $earned/$needed XP",
                style = MaterialTheme.typography.caption1,
                color = Lavender,
            )
            if (needed > 0) {
                Box(Modifier.padding(top = 4.dp).width(120.dp).height(6.dp).background(Track, CircleShape)) {
                    Box(Modifier.fillMaxWidth(earned.toFloat() / needed).height(6.dp).background(Mint, CircleShape))
                }
            }
        }
    }
    item {
        Text(
            "${pet.stepsToday} steps today · tap to pet",
            style = MaterialTheme.typography.caption2,
            color = Cream,
            textAlign = TextAlign.Center,
        )
    }
    if (!stepsPermitted) {
        item { PetChip("Earn XP from steps", "Allow activity access", onEnableSteps) }
    }
    item { PetChip("Choose pet", Species.displayName(pet.speciesId), onChoosePet) }
    item { PetChip("Rename", pet.name, onRename) }
    item { PetChip("Settings", "Bedtime & nudges", onSettings) }
}

@Composable
private fun PickerScreen(pet: PetState, onChoose: (String) -> Unit) {
    val context = LocalContext.current
    PetList {
        item { ListHeader { Text("Choose your pet") } }
        items(Species.all(context)) { id ->
            Chip(
                onClick = { onChoose(id) },
                label = { Text(Species.displayName(id)) },
                secondaryLabel = if (id == pet.speciesId) ({ Text("Your pet") }) else null,
                icon = { PixelImage(PetSprites.resolve(context, id, pet.stage, PetSprites.COMPLICATION), 32.dp) },
                colors = if (id == pet.speciesId) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "Switching keeps your XP and stage.",
                style = MaterialTheme.typography.caption3,
                color = Lavender,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SettingsScreen(pet: PetState, onBedtime: (Bedtime) -> Unit, onNudge: (Nudge) -> Unit) = PetList {
    item { ListHeader { Text("Settings") } }
    item { PetChip("Bedtime", pet.bedtime.label) { onBedtime(pet.bedtime.next()) } }
    item { PetChip("Gentle nudges", pet.nudge.label) { onNudge(pet.nudge.next()) } }
    item {
        Text(
            "Nudges make your pet curious on the face — a reminder to drink water or stretch. No alerts.",
            style = MaterialTheme.typography.caption3,
            color = Lavender,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PetChip(label: String, secondary: String, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label) },
        secondaryLabel = { Text(secondary) },
        colors = ChipDefaults.secondaryChipColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Plays the mood's frames at the face's 4 fps; falls back to the static sprite. */
@Composable
private fun AnimatedPet(pet: PetState, mood: Mood, size: Dp) {
    val context = LocalContext.current
    val frames = remember(pet.speciesId, pet.stage, mood) {
        PetSprites.frames(context, pet.speciesId, pet.stage, mood)
            .ifEmpty { listOf(PetSprites.resolve(context, pet.speciesId, pet.stage, PetSprites.COMPLICATION)) }
    }
    var frame by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        while (frames.size > 1) {
            delay(250)
            frame = (frame + 1) % frames.size
        }
    }
    PixelImage(frames[frame], size)
}

@Composable
private fun PixelImage(sprite: Sprite, size: Dp) {
    val context = LocalContext.current
    val bitmap = remember(sprite) { sprite.bitmap(context).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        filterQuality = FilterQuality.None, // nearest-neighbour: keep pixel art crisp
        modifier = Modifier.size(size),
    )
}

private fun <E : Enum<E>> E.next(): E {
    val values = declaringJavaClass.enumConstants!!
    return values[(ordinal + 1) % values.size]
}
