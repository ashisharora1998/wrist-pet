plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.ashish.wristpet.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.ashish.wristpet.watchface"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // WFF packages must contain no code. R8 strips the generated R class so no classes.dex is
    // packaged. Resource shrinking stays off: watchface.xml refers to drawables by name.
    buildTypes {
        debug {
            isMinifyEnabled = true
        }
        release {
            isMinifyEnabled = true
        }
    }
}

// The face's pet animations are generated from the bundled pets (assets/sprites + face-pets.txt),
// the same list the apps use, so the complication's species index always matches.
val copyFaceSprites = tasks.register<CopySpritesTask>("copyFaceSprites") {
    petDirs.from(bundledPetDirs())
}

val generateWatchFaceXml = tasks.register<GenerateWatchFaceXmlTask>("generateWatchFaceXml") {
    template.set(layout.projectDirectory.file("src/main/watchface/watchface.xml"))
    petDirs.from(bundledPetDirs())
    // Order is the mood index in the complication value; keep in sync with Mood in shared/.
    moods.set(listOf("idle", "happy", "curious", "sleep", "evolving", "playing", "eating", "attack", "angry", "sad"))
    // While charging (off the wrist) the pet looks sad, except while it sleeps.
    chargingMood.set("sad")
    restMood.set("sleep")
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(copyFaceSprites, CopySpritesTask::outputDir)
        variant.sources.res?.addGeneratedSourceDirectory(generateWatchFaceXml, GenerateWatchFaceXmlTask::outputDir)
    }
}
