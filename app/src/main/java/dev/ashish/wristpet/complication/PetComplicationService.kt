package dev.ashish.wristpet.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import dev.ashish.wristpet.PetTapActivity
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.Mood
import dev.ashish.wristpet.pet.PetRepository
import dev.ashish.wristpet.pet.PetSprites
import dev.ashish.wristpet.pet.PetState
import dev.ashish.wristpet.pet.Species
import java.time.ZoneId

/**
 * The bridge to the WFF face. RANGED_VALUE carries [Species.faceCode] so the face can pick its
 * bundled animation; the small image is the fallback (and the whole payload for SMALL_IMAGE slots).
 * Tapping the pet pets it ([petOnTap]). Updates are pushed by [dev.ashish.wristpet.pet.PetSignals]; UPDATE_PERIOD_SECONDS is 0, so the
 * system never polls.
 */
class PetComplicationService : SuspendingComplicationDataSourceService() {

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val state = PetRepository(this).current()
        return build(request.complicationType, state, GameRules.moodAt(state, System.currentTimeMillis(), ZoneId.systemDefault()))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, PetState(), Mood.IDLE)

    private fun build(type: ComplicationType, state: PetState, mood: Mood): ComplicationData? {
        val description = PlainComplicationText.Builder("${state.name}, ${mood.name.lowercase()}").build()
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(
                    value = Species.faceCode(this, state, mood).toFloat(),
                    min = 0f,
                    max = Species.maxFaceCode(this).toFloat(),
                    contentDescription = description,
                )
                    .setText(PlainComplicationText.Builder(state.name).build())
                    .setSmallImage(smallImage(state))
                    .setTapAction(petOnTap())
                    .build()

            ComplicationType.SMALL_IMAGE ->
                SmallImageComplicationData.Builder(smallImage(state), description)
                    .setTapAction(petOnTap())
                    .build()

            else -> null
        }
    }

    private fun smallImage(state: PetState): SmallImage {
        fun icon(slot: String) = PetSprites.resolve(this, state.speciesId, state.stage, slot).icon(this)
        return SmallImage.Builder(icon(PetSprites.COMPLICATION), SmallImageType.PHOTO)
            .setAmbientImage(icon(PetSprites.SLEEP_AMBIENT))
            .build()
    }

    /**
     * Tapping the pet on the face pets it (happy or playing, grumpy after a quick burst) through an
     * invisible activity, so the face stays up and plays the new mood's animation.
     */
    private fun petOnTap(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, PetTapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
