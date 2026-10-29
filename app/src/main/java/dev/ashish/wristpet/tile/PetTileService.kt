package dev.ashish.wristpet.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import dev.ashish.wristpet.PetActivity
import dev.ashish.wristpet.WristPetApp
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.PetRepository
import dev.ashish.wristpet.pet.PetSprites
import dev.ashish.wristpet.pet.PetState
import dev.ashish.wristpet.pet.Sprite
import kotlinx.coroutines.launch
import java.time.ZoneId

/** Swipe-left tile: the pet, its progress, and a Pet button that works without opening the app. */
class PetTileService : TileService() {
    private val repository by lazy { PetRepository(this) }

    override fun onTileRequest(request: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> = future {
        var state = repository.current()
        if (request.currentState.lastClickableId == CLICK_PET) {
            state = repository.update { GameRules.onPet(it, System.currentTimeMillis(), ZoneId.systemDefault()) }
        }
        TileBuilders.Tile.Builder()
            .setResourcesVersion(resourcesVersion(state))
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(state)))
            .build()
    }

    override fun onTileResourcesRequest(request: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> = future {
        val state = repository.current()
        ResourceBuilders.Resources.Builder()
            .setVersion(resourcesVersion(state))
            .addIdToImageMapping(
                IMAGE_PET,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(sprite(state).id).build())
                    .build(),
            )
            .build()
    }

    private fun sprite(state: PetState): Sprite = PetSprites.resolve(this, state.speciesId, state.stage, PetSprites.COMPLICATION)

    private fun layout(state: PetState): LayoutElementBuilders.LayoutElement {
        val (earned, needed) = GameRules.stageProgress(state.xp)
        val progress = if (needed == 0) "Fully grown" else "Stage ${state.stage} · $earned/$needed XP"
        val openApp = ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId(CLICK_OPEN)
                    .setOnClick(
                        ActionBuilders.LaunchAction.Builder()
                            .setAndroidActivity(
                                ActionBuilders.AndroidActivity.Builder()
                                    .setPackageName(packageName)
                                    .setClassName(PetActivity::class.java.name)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
        val petButton = ModifiersBuilders.Modifiers.Builder()
            .setClickable(ModifiersBuilders.Clickable.Builder().setId(CLICK_PET).setOnClick(ActionBuilders.LoadAction.Builder().build()).build())
            .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(MINT)).setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(20f)).build()).build())
            .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(18f)).setEnd(dp(18f)).setTop(dp(6f)).setBottom(dp(6f)).build())
            .build()

        return LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Image.Builder()
                    .setResourceId(IMAGE_PET)
                    .setWidth(dp(SPRITE_DP))
                    .setHeight(dp(SPRITE_DP))
                    .setModifiers(openApp)
                    .build(),
            )
            .addContent(text(state.name, 16f, CREAM, bold = true))
            .addContent(text(progress, 12f, LAVENDER))
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setModifiers(petButton)
                    .addContent(text("Pet", 14f, INK, bold = true))
                    .build(),
            )
            .build()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(sp(size))
                    .setColor(argb(color))
                    .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                    .build(),
            )
            .build()

    /** The image only changes with species or stage, so only then does the tile re-fetch it. */
    private fun resourcesVersion(state: PetState) = "${state.speciesId}_${state.stage}"

    private fun <T : Any> future(block: suspend () -> T): ListenableFuture<T> =
        CallbackToFutureAdapter.getFuture { completer ->
            val job = (application as WristPetApp).scope.launch {
                try {
                    completer.set(block())
                } catch (e: Throwable) {
                    completer.setException(e)
                }
            }
            completer.addCancellationListener({ job.cancel() }, Runnable::run)
            "PetTileService"
        }

    private companion object {
        const val IMAGE_PET = "pet"
        const val CLICK_PET = "pet"
        const val CLICK_OPEN = "open"
        // 160 px sprites at the watch's 2.125 px/dp: no resampling, so pixels stay crisp.
        const val SPRITE_DP = 75.3f
        const val MINT = 0xFF7FE0B0.toInt()
        const val CREAM = 0xFFF2EBD9.toInt()
        const val LAVENDER = 0xFFB9A6F2.toInt()
        const val INK = 0xFF1A1C1E.toInt()
    }
}
