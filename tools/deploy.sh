#!/usr/bin/env bash
# Builds WristPet and installs it on the watch and phone, in the order the face needs
# (watch app before face), with the reconnect fixes Wi-Fi debugging usually needs.
#
#   tools/deploy.sh --watch 192.168.1.19:41277            # build + install everything
#   tools/deploy.sh --watch :41277                        # reuse the last watch IP, new port
#   tools/deploy.sh --watch 192.168.1.19:41277 --only face --no-build
#   tools/deploy.sh --phone R5CY61DC68D --only phone
#
# Options:
#   --watch <ip:port>   watch address from Settings > Developer options > Wireless debugging
#   --phone <serial>    phone serial (default: the only USB device adb sees)
#   --only <list>       comma list of app,face,phone (default: all three)
#   --no-build          install the existing APKs
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STATE="$ROOT/.deploy-watch"   # last watch address, git-ignored
WATCH=""; PHONE=""; ONLY="app,face,phone"; BUILD=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --watch) WATCH="$2"; shift 2 ;;
    --phone) PHONE="$2"; shift 2 ;;
    --only) ONLY="$2"; shift 2 ;;
    --no-build) BUILD=0; shift ;;
    -h|--help) sed -n '2,14p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

want() { [[ ",$ONLY," == *",$1,"* ]]; }
step() { printf '\n== %s\n' "$*"; }
fail() { printf '\nFAILED: %s\n' "$*" >&2; exit 1; }

APK_APP="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
APK_FACE="$ROOT/watchface/build/outputs/apk/debug/watchface-debug.apk"
APK_PHONE="$ROOT/mobile/build/outputs/apk/debug/mobile-debug.apk"

command -v adb >/dev/null || fail "adb not found. Install it: brew install --cask android-platform-tools"
[[ -f "$ROOT/local.properties" ]] || fail "local.properties missing. Run: echo \"sdk.dir=\$HOME/Library/Android/sdk\" > local.properties"

# ---------- build ----------
if (( BUILD )); then
  if [[ -z "${JAVA_HOME:-}" ]] && command -v brew >/dev/null; then
    export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
  fi
  tasks=()
  want app && tasks+=(:app:assembleDebug)
  want face && tasks+=(:watchface:assembleDebug)
  want phone && tasks+=(:mobile:assembleDebug)
  step "Building ${tasks[*]}"
  (cd "$ROOT" && ./gradlew --console=plain -q "${tasks[@]}") || fail "build failed (see the Gradle output above)"
fi

# ---------- watch ----------
if want app || want face; then
  # ":41277" means "same IP as last time, new port".
  if [[ "$WATCH" == :* && -f "$STATE" ]]; then WATCH="$(cut -d: -f1 "$STATE")$WATCH"; fi
  [[ -n "$WATCH" ]] || fail "pass --watch <ip:port> (Wireless debugging screen on the watch)"
  [[ "$WATCH" == *:* ]] || fail "--watch must be ip:port, got '$WATCH'"

  step "Connecting to watch $WATCH"
  connected() { adb devices | awk -v s="$WATCH" '$1 == s && $2 == "device" {f=1} END {exit !f}'; }
  for attempt in 1 2 3; do
    adb connect "$WATCH" >/dev/null 2>&1 || true
    sleep 1
    connected && break
    # A stale adb server often reports "No route to host" even when the port is open.
    (( attempt == 2 )) && { echo "restarting adb server"; adb kill-server >/dev/null 2>&1 || true; adb start-server >/dev/null 2>&1 || true; }
    sleep 2
  done
  connected || fail "can't reach $WATCH. On the watch: keep it on the charger, turn Wireless debugging on again, and use the port it shows now (it changes every time). If it was never paired: adb pair <ip>:<pairing-port> <code>"
  echo "$WATCH" > "$STATE"

  install_watch() {
    local apk="$1" name="$2"
    for attempt in 1 2 3; do
      adb -s "$WATCH" install -r "$apk" >/dev/null 2>&1 && { echo "installed $name"; return 0; }
      echo "retrying $name ($attempt)"; sleep 2
      connected || adb connect "$WATCH" >/dev/null 2>&1 || true
    done
    fail "couldn't install $name on the watch"
  }

  adb -s "$WATCH" logcat -c 2>/dev/null || true
  # The face's pet slot points at the app's provider, so the app goes first.
  want app && { [[ -f "$APK_APP" ]] || fail "missing $APK_APP (drop --no-build)"; install_watch "$APK_APP" "watch app"; }
  want face && { [[ -f "$APK_FACE" ]] || fail "missing $APK_FACE (drop --no-build)"; install_watch "$APK_FACE" "watch face"; }

  step "Checking watch logs"
  sleep 5
  errors="$(adb -s "$WATCH" logcat -d -v brief 2>/dev/null \
    | grep -E "FATAL EXCEPTION|E/IWatchFaceInstanceServiceStub|E/DWF|AndroidRuntime.*dev\.ashish\.wristpet" \
    | grep -v -E "ResourceManager.*Path is empty" | head -10 || true)"
  if [[ -n "$errors" ]]; then
    echo "$errors"
    fail "the watch logged errors after install (above). Face XML problems show as IWatchFaceInstanceServiceStub/DWF lines."
  fi
  echo "no app or face errors"
fi

# ---------- phone ----------
if want phone; then
  if [[ -z "$PHONE" ]]; then
    # The phone is the one USB device: no ':' (Wi-Fi) and no '._adb-tls' (mDNS) in its serial.
    PHONE="$(adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /:|_adb/ {print $1}' | head -1)"
  fi
  if [[ -z "$PHONE" ]]; then
    if adb devices | grep -q unauthorized; then
      fail "the phone is connected but not authorised. Unlock it and accept 'Allow USB debugging?', then rerun with --only phone --no-build"
    fi
    fail "no phone found. Plug it in over USB with USB debugging on, then rerun with --only phone --no-build"
  fi
  [[ -f "$APK_PHONE" ]] || fail "missing $APK_PHONE (drop --no-build)"
  step "Installing phone app on $PHONE"
  adb -s "$PHONE" install -r "$APK_PHONE" >/dev/null || fail "couldn't install the phone app"
  echo "installed phone app"
fi

step "Done"
want app && echo "watch app: open WristPet on the watch (allow activity access the first time)"
want face && echo "watch face: long-press the face > + Add watch face > WristPet (already selected? it reloads itself)"
want phone && echo "phone app: open WristPet; it shows 'Watch connected' once the watch syncs"
