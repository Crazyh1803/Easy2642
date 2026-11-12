#!/usr/bin/env bash
# Sets this computer up to build the signed release bundle (.aab) for Google Play.
# Safe to run again: it only does the steps that are still missing.
#
#   1. finds Java (Android Studio's bundled one, or JDK 17+)
#   2. finds or installs the Android SDK pieces the project needs
#   3. creates your upload keystore and keystore.properties (first run only)
#   4. builds app/build/outputs/bundle/release/app-release.aab
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

say()  { printf '\n==> %s\n' "$*"; }
fail() { printf '\nSTOPPED: %s\n' "$*" >&2; exit 1; }

OS="$(uname -s)"
case "$OS" in
  Darwin) TOOLS_ZIP="commandlinetools-mac-11076708_latest.zip"; DEFAULT_SDK="$HOME/Library/Android/sdk" ;;
  Linux)  TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"; DEFAULT_SDK="$HOME/Android/Sdk" ;;
  *) fail "Unsupported system '$OS'. On Windows, double-click \"Setup Release Build (Windows).bat\" instead." ;;
esac

# ---------------------------------------------------------------- 1. Java
say "Looking for Java"
java_ok() { [ -x "$1/bin/java" ] && [ -x "$1/bin/keytool" ]; }
JAVA_DIR=""
for candidate in \
  "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
  "$HOME/android-studio/jbr" "/opt/android-studio/jbr" "/snap/android-studio/current/jbr" \
  "${JAVA_HOME:-}"; do
  if [ -n "$candidate" ] && java_ok "$candidate"; then JAVA_DIR="$candidate"; break; fi
done
if [ -z "$JAVA_DIR" ] && command -v java >/dev/null 2>&1; then
  found="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java\.home = //p')"
  if [ -n "$found" ] && java_ok "$found"; then JAVA_DIR="$found"; fi
fi
if [ -z "$JAVA_DIR" ]; then
  if [ "$OS" = Darwin ] && command -v brew >/dev/null 2>&1; then
    read -r -p "Android Studio is not installed. Install it now with Homebrew? [y/N] " yn
    if [ "$yn" = y ] || [ "$yn" = Y ]; then
      brew install --cask android-studio
      JAVA_DIR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    fi
  fi
  java_ok "$JAVA_DIR" || fail "Java was not found. Install Android Studio from https://developer.android.com/studio, open it once, then run this again."
fi
export JAVA_HOME="$JAVA_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
major="$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n 's/.*version "\([0-9]*\).*/\1/p' | head -1)"
[ "${major:-0}" -ge 17 ] || fail "Java $major found at $JAVA_HOME, but version 17 or newer is needed. Installing Android Studio provides one."
echo "Using Java $major at $JAVA_HOME"

# ---------------------------------------------------------------- 2. Android SDK
say "Looking for the Android SDK"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$DEFAULT_SDK}}"
if [ ! -d "$SDK/platforms/android-36" ] || [ ! -d "$SDK/build-tools/35.0.0" ]; then
  echo "Installing the SDK pieces this project needs into $SDK (about 250 MB download)"
  MANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
  if [ ! -x "$MANAGER" ]; then
    mkdir -p "$SDK/cmdline-tools"
    tmp="$(mktemp -d)"
    curl -fL --progress-bar -o "$tmp/tools.zip" "https://dl.google.com/android/repository/$TOOLS_ZIP"
    unzip -q -o "$tmp/tools.zip" -d "$tmp"
    rm -rf "$SDK/cmdline-tools/latest"
    mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
    rm -rf "$tmp"
  fi
  # ${SETUP_SDKMANAGER_ARGS} is only for networks that need a proxy; normally empty.
  # shellcheck disable=SC2086
  (yes || true) | "$MANAGER" ${SETUP_SDKMANAGER_ARGS:-} "platforms;android-36" "build-tools;35.0.0" "platform-tools" >/dev/null
fi
[ -d "$SDK/platforms/android-36" ] || fail "The Android SDK could not be installed at $SDK."
echo "Using Android SDK at $SDK"
printf 'sdk.dir=%s\n' "$SDK" > "$ROOT/local.properties"

# ---------------------------------------------------------------- 3. Upload key
if [ -f "$ROOT/keystore.properties" ]; then
  say "Signing key already set up (keystore.properties found)"
else
  say "Creating your upload key"
  cat <<'NOTE'
This key signs every version you upload to Google Play.
  * Choose a password you will not lose, and store it in a password manager.
  * Afterwards, back up upload-keystore.jks AND the password somewhere safe.
    They are never committed to git.
NOTE
  KEYSTORE="$ROOT/upload-keystore.jks"
  [ ! -f "$KEYSTORE" ] || fail "upload-keystore.jks exists but keystore.properties does not. Restore keystore.properties from your backup, or move the old .jks away to create a new key."
  OWNER="${SETUP_KEY_NAME:-}"
  if [ -z "$OWNER" ]; then
    read -r -p "Your name or company (shown inside the key) [Apps by Dan]: " OWNER
    OWNER="${OWNER:-Apps by Dan}"
  fi
  OWNER="$(printf '%s' "$OWNER" | tr -d ',="\\+<>;#')"
  PASS="${SETUP_KEY_PASSWORD:-}"
  while [ -z "$PASS" ]; do
    read -r -s -p "New keystore password (6+ characters): " p1; echo
    read -r -s -p "Type it again: " p2; echo
    if [ "$p1" != "$p2" ]; then echo "They did not match. Try again."; continue; fi
    if [ "${#p1}" -lt 6 ]; then echo "Too short. Try again."; continue; fi
    case "$p1" in *\\*|*' '*) echo "Avoid spaces and backslashes. Try again."; continue ;; esac
    PASS="$p1"
  done
  STOREPASS="$PASS" "$JAVA_HOME/bin/keytool" -genkeypair \
    -keystore "$KEYSTORE" -storetype PKCS12 -alias upload \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass:env STOREPASS -keypass:env STOREPASS \
    -dname "CN=$OWNER" >/dev/null
  umask 077
  {
    echo "storeFile=upload-keystore.jks"
    echo "storePassword=$PASS"
    echo "keyAlias=upload"
    echo "keyPassword=$PASS"
  } > "$ROOT/keystore.properties"
  echo "Created upload-keystore.jks and keystore.properties"
fi

# ---------------------------------------------------------------- 4. Build
say "Building the signed release bundle (the first run downloads a lot; allow 5-15 minutes)"
GRADLE="${SETUP_GRADLE:-$ROOT/gradlew}"
"$GRADLE" --no-daemon :app:bundleRelease

AAB="$ROOT/app/build/outputs/bundle/release/app-release.aab"
[ -f "$AAB" ] || fail "The build finished but $AAB was not produced."
# jarsigner exits 0 for unsigned files too, so look for its "jar verified" line.
"$JAVA_HOME/bin/jarsigner" -verify "$AAB" 2>&1 | grep -q "jar verified" || fail "The bundle was built but is not signed. Check keystore.properties."

say "Done"
cat <<DONE
Signed bundle:  $AAB
Upload that file in the Play Console (Testing > Internal testing > Create new release).

Before each later upload, raise versionCode (and versionName) in app/build.gradle.kts,
then run this script again.

To work on the app, open this folder in Android Studio: $ROOT
DONE
if [ "$OS" = Darwin ]; then open -R "$AAB" || true; fi
