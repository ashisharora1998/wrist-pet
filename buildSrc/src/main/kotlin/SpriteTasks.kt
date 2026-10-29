import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Pets bundled into the face and both apps: every folder in `assets/sprites/` (the built-in
 * pets) plus the `library/` pets listed in `face-pets.txt`, one id per line (`#` comments allowed).
 * Each has `stageN/<slot>.png` files. Pets are ordered by name; that order is the species index in
 * the complication value, so the face and the apps always agree.
 */
fun Project.bundledPetDirs(): List<File> {
    val root = rootProject.layout.projectDirectory
    val builtIn = root.dir("assets/sprites").asFile.listFiles { f -> f.isDirectory }.orEmpty().toList()
    val listFile = root.file("face-pets.txt").asFile
    val chosen = if (listFile.isFile) {
        listFile.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.distinct()
    } else {
        emptyList()
    }
    val clash = builtIn.map { it.name }.intersect(chosen.toSet())
    if (clash.isNotEmpty()) throw GradleException("face-pets.txt lists built-in pets: $clash")
    val library = root.dir("library").asFile
    val picked = chosen.map { id ->
        File(library, id).also {
            if (!File(it, "stage1").isDirectory) throw GradleException("face-pets.txt lists '$id', but library/$id/stage1 doesn't exist")
        }
    }
    return (builtIn + picked).sortedBy { it.name }
}

private val resourceName = Regex("[a-z0-9_]+")

internal class SpeciesSprites(val id: String, val index: Int, val stages: List<Pair<Int, List<File>>>)

internal fun readSprites(petDirs: Collection<File>): List<SpeciesSprites> =
    petDirs.sortedBy { it.name }.mapIndexed { index, dir ->
        if (!resourceName.matches(dir.name)) {
            throw GradleException("Pet folder '${dir.path}' must be named with lowercase letters, digits and underscores only.")
        }
        val stages = dir.listFiles().orEmpty()
            .mapNotNull { stageDir -> Regex("stage(\\d+)").matchEntire(stageDir.name)?.let { it.groupValues[1].toInt() to stageDir } }
            .sortedBy { it.first }
            .map { (stage, stageDir) -> stage to stagePngs(stageDir) }
        SpeciesSprites(dir.name, index, stages)
    }

private fun stagePngs(stageDir: File): List<File> =
    stageDir.listFiles { f -> f.extension == "png" }.orEmpty().sortedBy { it.name }.onEach {
        if (!resourceName.matches(it.nameWithoutExtension)) {
            throw GradleException("Sprite '${it.path}' must be named with lowercase letters, digits and underscores only.")
        }
    }

/** Copies every bundled pet's slots into a generated `drawable-nodpi` folder and lists them in `R.array.pet_species`. */
abstract class CopySpritesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val petDirs: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val drawable = out.resolve("drawable-nodpi").apply { mkdirs() }
        val species = readSprites(petDirs.files)
        for (sp in species) {
            for ((stage, pngs) in sp.stages) {
                pngs.forEach { it.copyTo(drawable.resolve("${sp.id}_stage${stage}_${it.name}"), overwrite = true) }
            }
        }
        out.resolve("values").apply { mkdirs() }.resolve("pet_species.xml").writeText(
            buildString {
                appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
                appendLine("""<resources>""")
                appendLine("""    <!-- Generated from assets/sprites + face-pets.txt; the position is the species index in the complication value. -->""")
                appendLine("""    <string-array name="pet_species" translatable="false">""")
                species.forEach { appendLine("""        <item>${it.id}</item>""") }
                appendLine("""    </string-array>""")
                append("""</resources>""")
            },
        )
    }
}

/**
 * Renders `res/raw/watchface.xml` from a template, replacing `<!-- @PET_SPRITES@ -->` with the
 * per-pet, per-stage, per-mood animations of every bundled pet.
 *
 * Contract with the watch app's complication (see `Species.faceCode`): the RANGED_VALUE value is
 * `speciesIndex * 100 + stage * 10 + moodIndex`, where moodIndex is the position of the mood in [moods].
 */
abstract class GenerateWatchFaceXmlTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val template: RegularFileProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val petDirs: ConfigurableFileCollection

    @get:Input
    abstract val moods: ListProperty<String>

    /** Mood shown while the watch is charging (off the wrist), unless the app's mood is [restMood]. */
    @get:Input
    abstract val chargingMood: Property<String>

    @get:Input
    abstract val restMood: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val xml = template.get().asFile.readText()
            .replace(PLACEHOLDER, petSprites(readSprites(petDirs.files), moods.get(), chargingMood.get(), restMood.get()))
        if (xml.contains(PLACEHOLDER)) throw GradleException("Template still contains $PLACEHOLDER")
        val raw = outputDir.get().asFile.resolve("raw")
        raw.deleteRecursively()
        raw.mkdirs()
        raw.resolve("watchface.xml").writeText(xml)
    }

    /**
     * Emits one small Condition per pet inside an outer Condition that picks the pet. A single flat
     * Condition with hundreds of Compares silently stops matching past some size on the watch's WFF
     * runtime (the pet goes blank), so no Condition here grows with the number of pets.
     */
    private fun petSprites(species: List<SpeciesSprites>, moods: List<String>, chargingMood: String, restMood: String): String = buildString {
        val value = "[COMPLICATION.RANGED_VALUE_VALUE]"
        val restIndex = moods.indexOf(restMood)
        data class Animation(val name: String, val expression: String, val frames: List<String>)

        // Per pet: charging rules first (they win over the app's mood, except at rest), then moods.
        fun animationsOf(sp: SpeciesSprites): List<Animation> {
            val charging = mutableListOf<Animation>()
            val animated = mutableListOf<Animation>()
            for ((stage, pngs) in sp.stages) {
                val names = pngs.map { it.nameWithoutExtension }
                val prefix = "${sp.id}_stage$stage"
                val base = sp.index * 100 + stage * 10
                moods.forEachIndexed { moodIndex, mood ->
                    // Numbered frames only: `pet_sleep_ambient` is not part of the sleep loop.
                    val frame = Regex("pet_${mood}_\\d+")
                    val frames = names.filter { frame.matches(it) }.map { "${prefix}_$it" }
                    if (frames.isEmpty()) return@forEachIndexed
                    animated += Animation("${prefix}_$mood", "$value == ${base + moodIndex}", frames)
                    if (mood == chargingMood) {
                        // "Not resting" as two comparisons: the WFF runtime fails a Condition on `!=`.
                        val rest = if (restIndex >= 0) " &amp;&amp; ($value &lt; ${base + restIndex} || $value &gt; ${base + restIndex})" else ""
                        charging += Animation(
                            "${prefix}_charging",
                            "[BATTERY_CHARGING_STATUS] == 1 &amp;&amp; $value &gt;= $base &amp;&amp; $value &lt; ${base + 10}$rest",
                            frames,
                        )
                    }
                }
            }
            return charging + animated
        }

        fun petRange(sp: SpeciesSprites) = "$value &gt;= ${sp.index * 100} &amp;&amp; $value &lt; ${sp.index * 100 + 100}"

        appendLine("""<Group name="pet_active" x="0" y="0" width="$SIZE" height="$SIZE">""")
        appendLine("""  <Variant mode="AMBIENT" target="alpha" value="0"/>""")
        appendLine("""  <Condition>""")
        appendLine("""    <Expressions>""")
        species.forEach { appendLine("""      <Expression name="${it.id}">${petRange(it)}</Expression>""") }
        appendLine("""    </Expressions>""")
        for (sp in species) {
            val animations = animationsOf(sp)
            appendLine("""    <Compare expression="${sp.id}">""")
            appendLine("""      <Condition>""")
            appendLine("""        <Expressions>""")
            animations.forEach { appendLine("""          <Expression name="${it.name}">${it.expression}</Expression>""") }
            appendLine("""        </Expressions>""")
            animations.forEach { animation ->
                appendLine("""        <Compare expression="${animation.name}">""")
                appendLine("""          <PartAnimatedImage x="0" y="0" width="$SIZE" height="$SIZE">""")
                // Between plays the runtime shows the sequence's thumbnail; FIRST_FRAME leaves it blank on the watch.
                // No TAP trigger: an animation that replays on tap swallows the tap, so the complication's
                // tap action (petting) would never fire.
                appendLine("""            <AnimationController play="ON_VISIBLE" repeat="TRUE" delayRepeat="4" beforePlaying="THUMBNAIL" afterPlaying="THUMBNAIL"/>""")
                // `change` defaults to TAP, which claims every tap even with a single image set.
                appendLine("""            <AnimatedImages change="ON_NEXT_HOUR">""")
                appendLine("""              <SequenceImages loopCount="2" frameRate="4" thumbnail="${animation.frames.first()}">""")
                animation.frames.forEach { appendLine("""                <Image resource="$it"/>""") }
                appendLine("""              </SequenceImages>""")
                appendLine("""            </AnimatedImages>""")
                appendLine("""          </PartAnimatedImage>""")
                appendLine("""        </Compare>""")
            }
            appendLine("""      </Condition>""")
            appendLine("""    </Compare>""")
        }
        appendLine("""  </Condition>""")
        appendLine("""</Group>""")

        // Ambient: one outline per stage, chosen the same way (pet first, then stage).
        appendLine("""<Group name="pet_ambient" x="0" y="0" width="$SIZE" height="$SIZE" alpha="0">""")
        appendLine("""  <Variant mode="AMBIENT" target="alpha" value="255"/>""")
        appendLine("""  <Condition>""")
        appendLine("""    <Expressions>""")
        species.forEach { appendLine("""      <Expression name="${it.id}_ambient">${petRange(it)}</Expression>""") }
        appendLine("""    </Expressions>""")
        for (sp in species) {
            val stages = sp.stages.filter { (_, pngs) -> pngs.any { it.nameWithoutExtension == "pet_sleep_ambient" } }.map { it.first }
            appendLine("""    <Compare expression="${sp.id}_ambient">""")
            appendLine("""      <Condition>""")
            appendLine("""        <Expressions>""")
            stages.forEach { stage ->
                val base = sp.index * 100 + stage * 10
                appendLine("""          <Expression name="${sp.id}_stage${stage}_ambient">$value &gt;= $base &amp;&amp; $value &lt; ${base + 10}</Expression>""")
            }
            appendLine("""        </Expressions>""")
            stages.forEach { stage ->
                appendLine("""        <Compare expression="${sp.id}_stage${stage}_ambient">""")
                appendLine("""          <PartImage x="0" y="0" width="$SIZE" height="$SIZE">""")
                appendLine("""            <Image resource="${sp.id}_stage${stage}_pet_sleep_ambient"/>""")
                appendLine("""          </PartImage>""")
                appendLine("""        </Compare>""")
            }
            appendLine("""      </Condition>""")
            appendLine("""    </Compare>""")
        }
        appendLine("""  </Condition>""")
        append("""</Group>""")
    }

    private companion object {
        const val PLACEHOLDER = "<!-- @PET_SPRITES@ -->"
        const val SIZE = 160
    }
}
