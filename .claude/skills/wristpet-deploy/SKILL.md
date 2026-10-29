---
name: wristpet-deploy
description: Build WristPet and install it on the Wear OS watch and/or Android phone over adb, then verify. Use when the user asks to build, install, deploy, reinstall, update or "push to the watch/phone", or after code or sprite changes that need to reach the devices.
---

# Deploy WristPet to the watch and phone

All the work is in `tools/deploy.sh` (run from the repo root). It builds, connects the watch over Wi-Fi with retries, installs in the required order (watch app before face), installs the phone app, and checks the watch logs for app or face errors. Run it rather than retyping the commands.

## 1. Work out what to deploy

Map what changed to `--only`:

| Changed | Deploy |
|---|---|
| `watchface/` (face XML, strings, layout) | `face` |
| `app/` | `app` |
| `mobile/` | `phone` |
| `shared/` or `buildSrc/` | `app,face,phone` |
| `assets/sprites/` or `face-pets.txt` (bundled pets changed) | `app,face,phone` (all three bundle the pets, and the species order must match) |
| Unsure, or the user said "everything" | omit `--only` |

The watch app and phone app must come from builds on the same computer, because the Data Layer needs matching signing keys. Never install just one of them from a different machine's build.

## 2. Get the device addresses

- **Watch:** the IP:port shown at the top of **Settings → Developer options → Wireless debugging**. The port changes whenever Wireless debugging restarts, so ask the user for it each session.
  - `--watch :<port>` reuses the last IP the script saved in `.deploy-watch`.
  - If the user only gives a port, try that first.
  - Before asking, run `adb devices`: if a watch is already listed as `device`, use its address.
- **Phone:** found automatically (the one USB device). Pass `--phone <serial>` only if several phones are plugged in.

## 3. Run it

```bash
tools/deploy.sh --watch <ip:port>
```

Variants:
- Everything: omit `--only`.
- Watch only: `--only app,face`.
- Phone only: `--only phone` (no `--watch` needed).
- Already built: add `--no-build`.

Builds can take minutes on first run, so use a long timeout (10 min) or run in the background and wait for it to finish.

## 4. Read the result and fix what failed

The script stops at the first failure with a `FAILED:` line. Act on it:

| Failure | What to do |
|---|---|
| `can't reach <watch>` | Ask the user to put the watch on its charger, turn Wireless debugging on again and send the **new port**. Never pair on their behalf without a code they gave you. If the watch was never paired, they open **Pair new device** and you run `adb pair <ip>:<pairing-port> <code>` straight away (the code expires when the screen dims). |
| `the phone is connected but not authorised` | Ask the user to unlock the phone and accept **Allow USB debugging?** (this happens after an `adb kill-server`), then rerun with `--only phone --no-build`. |
| `no phone found` | Ask for the phone to be plugged in with USB debugging on. |
| `build failed` | Fix the compile error in the code, then rerun. Don't use `--no-build` to skip it. |
| `the watch logged errors after install` | Read the printed lines. An `IWatchFaceInstanceServiceStub` or `DWF` line names the face XML problem (e.g. a fallback provider type not in `supportedTypes`). An `AndroidRuntime` line is an app crash. Fix the source, rebuild and redeploy. |
| `couldn't install … on the watch` | Usually Wi-Fi dropping mid-install. Rerun with `--no-build`. |

Repeated `ResourceManager … Path is empty` lines are harmless (widgets with no icon) and are already filtered out.

## 5. Report

Tell the user what was installed where, and what they should check on screen: the face reloads by itself if it's already selected; otherwise **long-press → + Add watch face → WristPet**. Say plainly what you didn't verify. The script checks logs, not the screen; a screenshot is `adb -s <watch> exec-out screencap -p > shot.png`, but the charging screen covers apps.

## Rules

- **Pet art isn't part of this skill.** If the user wants new art imported, point them to `tools/import_art.py` in the README and let them run it. Only run it yourself on original art, never on images of well-known copyrighted characters.
- **Don't change device settings.** Developer options, pairing, and on-screen permission prompts are the user's to do.
- **Don't commit or push** as part of a deploy unless the user asks.
