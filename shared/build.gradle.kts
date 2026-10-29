plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// Pet model, game rules, sprites and the watch <-> phone sync schema, used by both apps.
android {
    namespace = "dev.ashish.wristpet.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

// Sprites and the species list come from assets/sprites plus the library pets in face-pets.txt,
// so adding a pet never touches code. The face (watchface/) bundles the same list.
val copySprites = tasks.register<CopySpritesTask>("copySprites") {
    petDirs.from(bundledPetDirs())
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(copySprites, CopySpritesTask::outputDir)
    }
}

dependencies {
    api(libs.play.services.wearable)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)
}
