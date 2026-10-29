package dev.ashish.wristpet.pet

import android.content.Context
import dev.ashish.wristpet.shared.R

/**
 * Pets bundled into the apps and the face: the built-in ones in `assets/sprites/` plus the library
 * pets listed in `face-pets.txt`. The build lists them, in species-index order, in `R.array.pet_species`.
 */
object Species {
    fun all(context: Context): List<String> = context.resources.getStringArray(R.array.pet_species).toList()

    /** Position in the bundled list; the face uses it to pick the right animations. */
    fun index(context: Context, id: String): Int = all(context).indexOf(id).coerceAtLeast(0)

    fun displayName(id: String): String = id.replace('_', ' ').replaceFirstChar { it.uppercase() }

    /** Complication value the face decodes: `speciesIndex * 100 + stage * 10 + mood`. */
    fun faceCode(context: Context, state: PetState, mood: Mood): Int =
        index(context, state.speciesId) * 100 + state.stage * 10 + mood.code

    /** Largest possible [faceCode] for the bundled pets. */
    fun maxFaceCode(context: Context): Int = (all(context).size - 1) * 100 + GameRules.MAX_STAGE * 10 + Mood.entries.size - 1
}
