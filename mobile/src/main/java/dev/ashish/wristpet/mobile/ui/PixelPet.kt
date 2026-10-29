package dev.ashish.wristpet.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import dev.ashish.wristpet.pet.Mood
import dev.ashish.wristpet.pet.PetSprites
import dev.ashish.wristpet.pet.Sprite
import kotlinx.coroutines.delay

/** Plays a mood's frames at the face's 4 fps; falls back to the static sprite. */
@Composable
fun AnimatedPet(species: String, stage: Int, mood: Mood, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frames = remember(species, stage, mood) {
        PetSprites.frames(context, species, stage, mood)
            .ifEmpty { listOf(PetSprites.resolve(context, species, stage, PetSprites.COMPLICATION)) }
    }
    var frame by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames) {
        while (frames.size > 1) {
            delay(250)
            frame = (frame + 1) % frames.size
        }
    }
    PixelImage(frames[frame], size, modifier)
}

@Composable
fun PixelImage(sprite: Sprite, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(sprite) { sprite.bitmap(context).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        filterQuality = FilterQuality.None, // nearest-neighbour: keep pixel art crisp
        modifier = modifier.size(size),
    )
}
