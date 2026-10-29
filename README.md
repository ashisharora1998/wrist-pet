# WristPet

A pixel-pet watch face with companion apps for Wear OS and Android. Your pet lives on the watch face, grows from your steps and attention, sleeps at bedtime, and syncs with a phone app. It has no death, no streaks and no guilt.

- **Watch face** (Watch Face Format): time, date, battery, your pet's full animations for every mood, two widget slots, step and battery rings, themes and tap shortcuts.
- **Watch app:** pet screen, game (XP, evolution, moods), bedtime sleep, gentle nudges, and a tile.
- **Phone app:** dashboard, pet gallery, 30-day history, settings and a home-screen widget.

Built and tested on a **Galaxy Watch4 (SM-R870, 44 mm, Wear OS 6)** with a **Galaxy S25 Ultra**. It's sideloaded with `adb`, not published to a store.

## Compatibility

| Part | Needs |
|---|---|
| Watch face | Wear OS 6 or newer (One UI 8 Watch on Samsung), because it uses Watch Face Format v4 |
| Watch app | Wear OS 4 or newer |
| Phone app | Android 8.0 or newer, paired with the watch |

The layout is designed for a 450×450 round screen. Other round sizes (e.g. 480×480) scale automatically; the pixel art is slightly softer there. Watches running Tizen (older Galaxy watches) can't run it.

---

## Installation

You need a Mac (the steps below use Homebrew; Linux works with equivalent packages), the watch, and optionally the Android phone it's paired with. Android Studio isn't required.

### 1. Install the toolchain (one time)

```bash
brew install openjdk@17
```

```bash
brew install --cask android-commandlinetools android-platform-tools
```

Install the Android SDK platform and build tools. This asks you to accept the Android SDK licence.

```bash
sdkmanager --sdk_root="$HOME/Library/Android/sdk" --licenses
```

```bash
sdkmanager --sdk_root="$HOME/Library/Android/sdk" "platforms;android-36" "build-tools;36.0.0"
```

Make Gradle use JDK 17. Add this line to your shell profile so it's set in every terminal:

```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
```

### 2. Get the code and point it at the SDK

```bash
git clone git@github.com:ashisharorambdev/wrist-pet.git
```

```bash
cd wrist-pet && echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

`local.properties` is git-ignored because the path is machine-specific.

### 3. Build

```bash
./gradlew :app:assembleDebug :watchface:assembleDebug :mobile:assembleDebug
```

The first build downloads Gradle and the libraries, which takes a few minutes; later builds are fast. It produces three APKs:

| APK | Goes on |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | watch (the WristPet app) |
| `watchface/build/outputs/apk/debug/watchface-debug.apk` | watch (the face) |
| `mobile/build/outputs/apk/debug/mobile-debug.apk` | phone |

> **Build the watch and phone APKs on the same computer.** The watch and phone only talk to each other when both apps are signed with the same key. Debug builds use this computer's debug key (`~/.android/debug.keystore`).

### 4. Connect the watch over Wi-Fi

1. On the watch, open **Settings → About watch → Software information** and tap **Software version** 5 times. Developer options unlock.
2. Open **Settings → Developer options** and turn on **ADB debugging**, then **Wireless debugging**. The watch and computer must be on the same Wi-Fi.
3. Put the watch **on its charger**. Wi-Fi debugging drops when the watch sleeps, and charging keeps it awake.
4. Open **Wireless debugging → Pair new device**. The watch shows a pairing IP:port and a 6-digit code. Pair right away, before the screen dims, with the code at the end:

   ```bash
   adb pair <watch-ip>:<pairing-port> <code>
   ```

5. Connect using the IP:port shown at the top of the main **Wireless debugging** screen. It's a different port from pairing, and it changes every time Wireless debugging turns on.

   ```bash
   adb connect <watch-ip>:<port>
   ```

6. Check that the watch is listed as `device`:

   ```bash
   adb devices
   ```

Pairing is a one-time step. Later, you only need step 5 with the current port.

### 5. Install the watch app, then the face

Install the **app first**. The face's pet slot points at the app's pet provider, so the app must already be there when the face loads.

```bash
adb -s <watch-ip>:<port> install -r app/build/outputs/apk/debug/app-debug.apk
```

```bash
adb -s <watch-ip>:<port> install -r watchface/build/outputs/apk/debug/watchface-debug.apk
```

Then on the watch:

1. **Choose the face:** long-press the current watch face, swipe to the end, tap **+ Add watch face**, and pick **WristPet**.
2. **If the pet area is empty:** long-press the face → **Customize** → **Complications** → tap the pet area → **WristPet pet** → allow the prompt.
3. **Open the app:** open **WristPet** from the app list (or tap the pet on the face). When it asks for **activity access**, allow it so your steps earn XP. The pet still works without it, through petting and daily visits.
4. **Add the tile (optional):** swipe left from the face, scroll to the end, tap **+**, and add **WristPet**.

### 6. Install the phone app (optional)

1. On the phone, open **Settings → About phone → Software information** and tap **Build number** 7 times.
2. Open **Settings → Developer options** and turn on **USB debugging**.
3. Plug the phone into the computer and accept **Allow USB debugging?**.
4. Find the phone's serial with `adb devices` (it's the entry without an IP address), then install:

   ```bash
   adb -s <phone-serial> install -r mobile/build/outputs/apk/debug/mobile-debug.apk
   ```

5. Open **WristPet** on the phone. Within a few seconds it shows your pet and "Watch connected". If it says "No pet yet", open WristPet on the watch once so it syncs.
6. **Add the widget (optional):** long-press the home screen → **Widgets** → **WristPet**.

### Updating later

Rebuild (step 3) and run the same `adb install -r` commands. Progress is kept across updates. When updating both watch APKs, install the app before the face.

Or let `tools/deploy.sh` do it all: build, connect to the watch (with retries and an adb restart if needed), install in the right order, install the phone app, and check the watch logs for errors.

```bash
tools/deploy.sh --watch <watch-ip>:<port>
```

Add `--only app,face` or `--only phone` to deploy part of it, `--no-build` to install the existing APKs, and `--watch :<port>` to reuse the last watch IP with a new port.

**With Claude Code:** the repo includes a `wristpet-deploy` skill (`.claude/skills/wristpet-deploy/`). Ask Claude to "deploy to the watch" and give it the Wireless debugging port. It works out which parts changed, runs the script, and fixes or explains any failure.

### Troubleshooting

| Problem | Fix |
|---|---|
| `adb pair` fails with "protocol fault" | The pairing screen closed. Open **Pair new device** again, which gives a new code and port, and pair immediately. |
| `adb connect` is refused, or says "No route to host" | Wireless debugging turned off or the port changed. Turn it on again, use the new port, and keep the watch on the charger. If the port is open but adb still fails, run `adb kill-server` and connect again. |
| "more than one device" | Add `-s <serial>` to the command (serials come from `adb devices`). |
| WristPet is missing from the face list, or the face is blank | The face XML was rejected. Read the reason with `adb logcat -d \| grep -E "DWF\|IWatchFaceInstance"`. |
| Phone says "Watch away" | The watch is out of Bluetooth range, or the two apps were built on different computers (different signing keys). |
| Install fails with no message | Wi-Fi hiccup. Run the same command again. |

---

## How it works

### Modules

| Module | What it is |
|---|---|
| `watchface/` | Watch Face Format v4 face. It contains resources only, no code (`android:hasCode="false"`); the system's WFF runtime draws `res/raw/watchface.xml`. |
| `app/` | Kotlin Wear OS app. It owns the pet's state (DataStore), runs the game, provides the **pet complication**, the tile, and the pet screen (Compose for Wear). |
| `shared/` | Android library for both apps: pet model, `GameRules` and tests, sprites and the generated pet list, and the watch ↔ phone sync format. |
| `mobile/` | Android phone companion: Material 3 Compose and a Glance home-screen widget. |
| `buildSrc/` | Build tasks that turn `assets/sprites/` into Android drawables and generate the face's sprite XML. |
| `tools/` | `gen_placeholder_sprites.py` (draws the placeholder pets) and `import_art.py` (imports your own art). |

### Face ↔ app contract

The face can't read app state; it only sees complication data. The app's `PetComplicationService` sends a **RANGED_VALUE** complication:

- **Value:** `speciesIndex * 100 + stage * 10 + mood`.
  - Moods: idle 0, happy 1, curious 2, sleep 3, evolving 4, playing 5, eating 6, attack 7, angry 8, sad 9 (`Mood` in `shared/.../pet/PetState.kt`, `moods` in `watchface/build.gradle.kts`, `MOODS` in `tools/import_art.py`).
  - Species index: the bundled pets in name order. Both apps and the face are generated from the same list, so they always agree.
- **Small image:** the pet's `pet_complication.png`, with `pet_sleep_ambient.png` for ambient mode. The face falls back to it for any value it doesn't recognise.
- **Tap action:** opens the pet screen.

The face plays the matching bundled animation natively (smooth, drawn by the system). While `[BATTERY_CHARGING_STATUS]` is charging (off the wrist) it plays the pet's sad animation, unless the pet is asleep. In ambient mode it shows the pet's outline. Updates are push-only: `UPDATE_PERIOD_SECONDS=0`, and `PetSignals` requests an update only when the species, stage or current mood changes.

### Which pets are bundled

```
assets/sprites/  (Sprout, Puff, Pebble: built in)  ┐
library/<pet>    listed in face-pets.txt             ┘──▶ face + watch app + phone app
```

- `library/` holds everything you import, and is git-ignored.
- `face-pets.txt` lists the library pets to bundle, one id per line. It's git-ignored too.
- Every bundled pet gets **all its animations** on the face, the watch's pet screen and the phone.
- **Changing your picks:** edit `face-pets.txt`, then rebuild and redeploy all three (`tools/deploy.sh`).
- **Keep it to about 10–20 pets.** Every bundled frame goes into the face and both apps; with all 151 the build hits Android's resource limit and the face would exceed its memory budget.
- **A removed pet:** if your current pet is taken off the list, the apps draw Sprout until you choose another; XP and stage are kept.

### Face layout (450×450, x, y, w, h)

| Element | Bounds |
|---|---|
| Date (tap: Calendar) | 125, 55, 200, 30 |
| Time (tap: Alarms) | 75, 90, 300, 110 |
| Pet complication (tap: WristPet) | 145, 220, 160, 160 |
| Left widget slot (default: steps) | 50, 240, 80, 80 |
| Right widget slot (default: heart rate) | 320, 240, 80, 80 |
| Battery (tap: battery status; hidden in ambient) | 175, 395, 100, 25 |
| Edge rings: steps (left), battery (right) | radius 215, 6 px stroke |

Long-press → **Customize** to change:
- the theme (Mint, Lavender, Coral, Mono)
- whether the steps and battery rings show
- what each widget slot shows

Ambient mode keeps only the time, date and the sleeping pet. Screenshots: `docs/phase0-face.png`, `docs/face-widgets.png`.

### Game

All rules are pure functions in `shared/.../pet/GameRules.kt`, covered by unit tests. Progress only goes up: no death, illness, lost levels, streaks or guilt.

| Source | XP | Limit |
|---|---|---|
| Steps (Health Services, batched) | 1 per 100 steps | 150 per day |
| Petting (watch, tile or phone) | +2 | 10 times per day; still makes the pet happy after that |
| First app visit of the day | +5 | once per day |

- **Stages:** stage 1 → 2 at 100 XP, stage 2 → 3 at 400 XP. Switching pets keeps XP and stage.
- **Moods** (the first that applies wins):

  | Mood | When |
  |---|---|
  | sleep | bedtime (default 22:00–07:00) |
  | evolving | the first 2 min after reaching a new stage, then happy for an hour |
  | happy / playing | 15 seconds after each tap on the pet (they alternate), then back to idle. Happy also plays for 30 min at each 2,000 steps |
  | angry | 5 taps within a minute; grumpy for 5 min, no XP lost |
  | attack | reaching 10,000 steps for the day, a 15-min "power-up" |
  | curious | a gentle nudge, for 15 min |
  | eating | two random snack times a day (11:00–14:00 and 17:00–20:00), 15 min each |
  | sad | on the face only, while the watch is charging (off your wrist), except at bedtime |
  | idle | otherwise |

- **History:** each finished day (steps, XP, pets) is kept for 30 days and shown on the phone.
- **No polling:**
  - Steps arrive from Health Services passive monitoring.
  - Time-based mood changes use one inexact alarm, and nudges use an optional inexact repeating alarm.
  - Both alarms are re-armed after a reboot or update.

### Watch ↔ phone sync

```
watch ── DataItem /pet/state (state + 30-day history) ──▶ phone app + widget
watch ◀── message /pet/command (choose, rename, bedtime, nudge, pet) ── phone
```

- **Ownership:** the watch owns the game and works fully without the phone.
- **Matching apps:** both apps use the package `dev.ashish.wristpet`. The Data Layer only connects apps with the same package name and signing key.
- **Watch → phone:**
  - The watch publishes only when something visible changes.
  - Step updates are non-urgent so the system can bundle them; user actions sync immediately.
  - The phone keeps the last snapshot, so the app and widget work offline and work out the current mood themselves.
- **Phone → watch:**
  - Commands go to watches with the `wristpet_watch` capability.
  - The watch applies them through the same `GameRules`, so the petting cap applies to phone petting too. Unknown commands are ignored.
  - If no watch is reachable, the phone disables its controls and shows an error. Nothing is queued.
- **One watch per phone:** the phone app currently assumes a single watch. With two WristPet watches paired, it would show whichever synced last.

---

## Pet art

### Sprite slots

```
assets/sprites/<pet>/stage{1,2,3}/
  pet_idle_01..04.png      # face and app loops
  pet_happy_01..04.png
  pet_curious_01..04.png
  pet_sleep_01..02.png     # bedtime loop
  pet_evolving_NN.png  pet_playing_NN.png  pet_eating_NN.png
  pet_attack_NN.png    pet_angry_NN.png    pet_sad_NN.png
  pet_sleep_ambient.png    # static, low-pixel, for ambient mode
  pet_complication.png     # fallback image, tile, picker and widget icon
```

- **Size:** 160×160 transparent PNG. For crisp pixel art, draw on a 32×32 or 40×40 grid and export with nearest-neighbour scaling.
- **Names:** lowercase letters, digits and `_` only; the build rejects anything else. Frames are `pet_<mood>_NN.png`, and any number of frames works.
- **Ambient:** mostly transparent, outline only, under ~15% lit pixels (OLED burn-in and battery).
- **Adding a pet:** import it into `library/` (below), add its id to `face-pets.txt`, and rebuild. Folders in `assets/sprites/` are always bundled; keep that for original art.
- **Placeholders:** the three original pets (Sprout, Puff, Pebble) come from `python3 tools/gen_placeholder_sprites.py`. Re-running it overwrites their slots.

### Importing your own art

`tools/import_art.py` turns ordinary images into a full set of slots:
- removes a solid background
- crops and pixelates (or keeps the art smooth with `--style smooth`)
- animates any mood you don't supply by moving the image (bob, hop, tilt, breathe)
- traces the ambient outline
- writes a preview sheet

One-time setup:

```bash
python3 -m venv .venv && .venv/bin/pip install pillow
```

Put one folder per pet in `incoming/` (git-ignored), named by stage and optional mood. PNG, GIF, WebP and JPG all work.

```
incoming/<pet>/stage1.png   stage1_happy.png   stage1_sleep.png   stage2.gif   stage3.png
```

Then run the importer:

```bash
.venv/bin/python tools/import_art.py
```

Imports go to `library/`. Check `incoming/preview.png` (rows are stages; columns are the moods, then ambient). Then add the pets you want to `face-pets.txt` and rebuild and redeploy:

```bash
tools/deploy.sh --watch <watch-ip>:<port>
``` Missing stages reuse the nearest stage you provided. Images need a plain or transparent background; the importer can't cut a character out of a scene.

**Sprite packs:** if your art comes as a pack with one folder per creature (`<folder>/animations/<state>.gif`, optionally `gif/front.gif` or `png/front.png`), pick an evolution line by listing up to three folders, one per stage. Repeat `--line` for each pet:

```bash
.venv/bin/python tools/import_art.py --pack <pack-folder> --line <pet>=<stage1-folder>,<stage2-folder>,<stage3-folder>
```

Pack animations map to moods by name: `idle`, `happy`, `surprised` (or `curious`) → curious, `sleep`, `evolving`, `playing`, `eating`, `attack`, `angry` and `sad`. Any mood a pack doesn't have is animated from the idle image (bob, hop, shake, droop and so on).

To import the whole pack, add `--all`: every folder becomes its own pet (`001-first-name` → `first_name`), the same at all three stages. Combine it with `--line` for the creatures you want to evolve; folders used in a line aren't also imported on their own.

```bash
.venv/bin/python tools/import_art.py --pack <pack-folder> --all --line <pet>=<stage1>,<stage2>,<stage3>
```

Import as many as you like; only the pets listed in `face-pets.txt` are bundled.

**Third-party art stays local.** `.gitignore` excludes `library/`, `incoming/`, `face-pets.txt` and every folder in `assets/sprites/` except the three original placeholders, so imported art is never committed.

---

## Development

Run the unit tests (game rules and sync format):

```bash
./gradlew :shared:testDebugUnitTest
```

Watch the face runtime and app logs:

```bash
adb -s <watch> logcat -s DWF:DeclarativeWatchFaceRuntime IWatchFaceInstanceServiceStub AndroidRuntime
```

Open the pet screen directly:

```bash
adb -s <watch> shell am start -n dev.ashish.wristpet/.PetActivity
```

**Gradle notes for Node developers:**
- `./gradlew` is like a pinned `npx`: it downloads the Gradle version named in `gradle/wrapper/gradle-wrapper.properties`.
- `gradle/libs.versions.toml` is the single place dependency versions are set.
- Each module's `build.gradle.kts` is its own build config.

**Lessons from the real watch:**
- A face slot's fallback `defaultSystemProviderType` must be one of the slot's `supportedTypes`. `EMPTY` makes the runtime refuse to load the face.
- AGP packages a `classes.dex` even with `hasCode="false"`, so the face module enables R8 to strip it.
- The 44 mm screen is about 212 dp wide (density 340), so watch layouts must be tight.
- The charging screen covers apps in `adb screencap`.

## Roadmap

- Backup and restore of pet progress through the phone.
- Weather reactions on the face.
- A native 480×480 layout (Galaxy Watch Ultra) and support for several watches per phone.
- Choosing pets that aren't bundled (from the whole library) without a rebuild, e.g. via Watch Face Push.
- Watch Face Push, so new pet art can reach the face without `adb`.
- A release signing key, so builds from different computers can sync.

## Product rules

No death, illness, lost levels, streaks or guilt notifications. The core pet works fully offline. Updates are event-driven only, with no polling.
