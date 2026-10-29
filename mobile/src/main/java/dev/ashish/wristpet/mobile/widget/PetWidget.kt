package dev.ashish.wristpet.mobile.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.ashish.wristpet.mobile.MainActivity
import dev.ashish.wristpet.mobile.data.PhoneRepository
import dev.ashish.wristpet.pet.GameRules
import dev.ashish.wristpet.pet.PetSprites
import dev.ashish.wristpet.pet.Species
import java.time.ZoneId

/** Home-screen pet. Redrawn only when the watch publishes (see PhoneSyncService); never polls. */
class PetWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = runCatching { PhoneRepository(context).latest() }.getOrNull()
        // Falls back to a placeholder if the pet's image can't be loaded.
        val sprite = snapshot?.state?.let { pet ->
            runCatching { PetSprites.resolve(context, pet.speciesId, pet.stage, PetSprites.COMPLICATION).bitmap(context) }.getOrNull()
        }
        provideContent {
            Row(
                GlanceModifier.fillMaxSize().background(INK).cornerRadius(24.dp).padding(16.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (snapshot == null) {
                    Text("Open WristPet on your watch to meet your pet.", style = TextStyle(color = ColorProvider(CREAM)))
                    return@Row
                }
                val pet = snapshot.state
                val mood = GameRules.moodAt(pet, System.currentTimeMillis(), ZoneId.systemDefault())
                val (earned, needed) = GameRules.stageProgress(pet.xp)
                Image(
                    provider = sprite?.let { ImageProvider(it) } ?: ImageProvider(android.R.drawable.sym_def_app_icon),
                    contentDescription = pet.name,
                    modifier = GlanceModifier.size(80.dp),
                )
                Spacer(GlanceModifier.width(12.dp))
                Column {
                    Text(pet.name, style = TextStyle(color = ColorProvider(CREAM), fontSize = 20.sp, fontWeight = FontWeight.Bold))
                    Text(
                        "${Species.displayName(pet.speciesId)} · ${mood.name.lowercase()}",
                        style = TextStyle(color = ColorProvider(LAVENDER), fontSize = 13.sp),
                    )
                    Text(
                        if (needed == 0) "Stage ${pet.stage} · fully grown" else "Stage ${pet.stage} · $earned/$needed XP",
                        style = TextStyle(color = ColorProvider(MINT), fontSize = 13.sp),
                    )
                }
            }
        }
    }

    private companion object {
        val INK = Color(0xFF1A1C1E)
        val CREAM = Color(0xFFF2EBD9)
        val LAVENDER = Color(0xFFB9A6F2)
        val MINT = Color(0xFF7FE0B0)
    }
}

class PetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PetWidget()
}
