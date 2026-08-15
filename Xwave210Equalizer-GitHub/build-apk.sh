#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

GRADLE_VERSION="9.5.0"
GRADLE_DIR="$PWD/.tools/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_DIR/bin/gradle"
APK_SRC="app/build/outputs/apk/debug/app-debug.apk"
APK_DST="APK/Xwave210-Equalizer.apk"

say() { printf '\n==> %s\n' "$*"; }
die() { printf '\nERROR: %s\n' "$*" >&2; exit 1; }

say "Xwave 210 Equalizer - Linux APK build"

# ---- Java 17 ----
command -v java >/dev/null 2>&1 || die "Java nije pronađena. Instaliraj JDK 17 (npr. sudo apt install openjdk-17-jdk)."
JAVA_MAJOR="$(java -version 2>&1 | awk -F[\\\".] '/version/ {print $2; exit}')"
if [ "${JAVA_MAJOR:-0}" -lt 17 ]; then
  die "Potreban je JDK 17 ili noviji. Trenutna Java: $(java -version 2>&1 | head -n1)"
fi
say "Java OK: $(java -version 2>&1 | head -n1)"

# ---- Android SDK ----
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [ -z "$SDK" ]; then
  for candidate in "$HOME/Android/Sdk" "$HOME/Android/sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
    if [ -d "$candidate" ]; then SDK="$candidate"; break; fi
  done
fi
[ -n "$SDK" ] || die "Android SDK nije pronađen. Postavi ANDROID_SDK_ROOT, npr. export ANDROID_SDK_ROOT=\$HOME/Android/Sdk"
[ -d "$SDK" ] || die "ANDROID_SDK_ROOT pokazuje na nepostojeći folder: $SDK"
export ANDROID_SDK_ROOT="$SDK"
export ANDROID_HOME="$SDK"
say "Android SDK: $SDK"

# local.properties so AGP always sees the SDK path
printf 'sdk.dir=%s\n' "$(printf '%s' "$SDK" | sed 's/\\/\\\\/g')" > local.properties

# ---- Required SDK packages ----
SDKMANAGER=""
for c in \
  "$SDK/cmdline-tools/latest/bin/sdkmanager" \
  "$SDK/cmdline-tools/bin/sdkmanager" \
  "$SDK/tools/bin/sdkmanager"; do
  if [ -x "$c" ]; then SDKMANAGER="$c"; break; fi
done

if [ -n "$SDKMANAGER" ]; then
  say "Provera Android SDK paketa"
  yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
  "$SDKMANAGER" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
else
  if [ ! -d "$SDK/platforms/android-36" ]; then
    die "Nedostaje Android platform 36 i sdkmanager nije pronađen. Instaliraj Android SDK Command-line Tools."
  fi
  if [ ! -d "$SDK/build-tools/36.0.0" ]; then
    die "Nedostaje Build Tools 36.0.0 i sdkmanager nije pronađen."
  fi
fi

# ---- Gradle 9.5.0 bootstrap ----
if [ ! -x "$GRADLE_BIN" ]; then
  say "Preuzimam Gradle $GRADLE_VERSION"
  mkdir -p "$PWD/.tools"
  ZIP="$PWD/.tools/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"

  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ZIP"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
  else
    die "Potreban je curl ili wget da se preuzme Gradle."
  fi

  command -v unzip >/dev/null 2>&1 || die "unzip nije instaliran. Na Debian/Ubuntu: sudo apt install unzip"
  unzip -q -o "$ZIP" -d "$PWD/.tools"
  rm -f "$ZIP"
fi

say "Gradle: $("$GRADLE_BIN" --version | awk '/^Gradle / {print; exit}')"

# ---- Build ----
say "Clean"
"$GRADLE_BIN" --no-daemon clean

say "Build debug APK"
"$GRADLE_BIN" --no-daemon :app:assembleDebug

[ -f "$APK_SRC" ] || die "Build je završen, ali APK nije pronađen: $APK_SRC"

mkdir -p APK
cp -f "$APK_SRC" "$APK_DST"

say "GOTOVO"
printf '%s\n' "$PWD/$APK_DST"
