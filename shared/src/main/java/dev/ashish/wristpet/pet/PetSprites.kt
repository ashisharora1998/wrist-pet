package dev.ashish.wristpet.pet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon

/** One bundled sprite image, as an Android drawable resource. */
data class Sprite(val id: Int) {
    /** Full-size pixels, never density-scaled, so pixel art stays crisp. */
    fun bitmap(context: Context): Bitmap =
        BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
            ?: error("Can't decode sprite resource $id")

    fun icon(context: Context): Icon = Icon.createWithResource(context, id)
}

/**
 * Resolves sprite slots (`<pet>/stageN/<slot>.png`, bundled as `<pet>_stageN_<slot>` drawables) by
 * name, so art can be swapped without touching code.
 */
object PetSprites {
    const val COMPLICATION = "pet_complication"
    const val SLEEP_AMBIENT = "pet_sleep_ambient"

    fun resolve(context: Context, species: String, stage: Int, slot: String): Sprite {
        val art = artFor(context, species)
        return find(context, art, stage, slot) ?: error("Missing sprite slot $art/stage$stage/$slot.png")
    }

    /** Animation frames `pet_<mood>_01..` for a mood, in order; empty if the slot has none. */
    fun frames(context: Context, species: String, stage: Int, mood: Mood): List<Sprite> {
        val art = artFor(context, species)
        return generateSequence(1) { it + 1 }
            .map { find(context, art, stage, "pet_${mood.name.lowercase()}_%02d".format(it)) }
            .takeWhile { it != null }
            .filterNotNull()
            .toList()
    }

    /**
     * The pet whose art to draw. A saved pet can stop being bundled (removed from face-pets.txt),
     * so the built-in default stands in instead of failing.
     */
    fun artFor(context: Context, species: String): String =
        if (find(context, species, 1, COMPLICATION) != null) species else PetState.DEFAULT_SPECIES

    @SuppressLint("DiscouragedApi") // Slot lookup by name is the point: art stays swappable.
    private fun find(context: Context, species: String, stage: Int, slot: String): Sprite? {
        val id = context.resources.getIdentifier("${species}_stage${stage}_$slot", "drawable", context.packageName)
        return if (id != 0) Sprite(id) else null
    }
}
