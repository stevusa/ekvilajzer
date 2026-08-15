#!/usr/bin/env bash
set -euo pipefail

echo "Instaliranje osnovnih Linux build alata (Debian/Ubuntu)..."
sudo apt update
sudo apt install -y openjdk-17-jdk curl unzip

cat <<'EOF'

Osnovni alati su instalirani.

Još je potreban Android SDK.
Ako koristiš Android Studio, SDK je obično:
  $HOME/Android/Sdk

Zatim:
  export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
  ./build-apk.sh

Ako već imaš ANDROID_SDK_ROOT podešen, samo pokreni:
  ./build-apk.sh
EOF
