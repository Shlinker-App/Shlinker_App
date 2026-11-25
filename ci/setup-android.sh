#!/usr/bin/env bash
# Install JDK 21 and the Android SDK in the Debian CI container.
# Pin tool downloads and verify their checksums.
set -euo pipefail

JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz"
JDK_SHA256="d7f5d5cf865a2902b70ff549b97586a7d3f234e016f041ab447ea740c7d66d1c"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"
CMDLINE_SHA1="e025545c62a8e64c7559119566a569fb1dec5f60"
SDK_PACKAGES=("platform-tools" "platforms;android-37.0" "build-tools;37.0.0" "build-tools;36.0.0")

TOOLS="${CI_TOOLS_DIR:-/opt/ci}"
export JAVA_HOME="$TOOLS/jdk-21"
export ANDROID_HOME="$TOOLS/android-sdk"
mkdir -p "$TOOLS"

if ! command -v unzip >/dev/null; then
  apt-get update -qq && apt-get install -y -qq unzip >/dev/null
fi

if [ ! -x "$JAVA_HOME/bin/java" ]; then
  echo "Downloading JDK 21..."
  curl -fsSL "$JDK_URL" -o /tmp/jdk.tgz
  echo "$JDK_SHA256  /tmp/jdk.tgz" | sha256sum -c -
  mkdir -p "$JAVA_HOME" && tar -xzf /tmp/jdk.tgz -C "$JAVA_HOME" --strip-components=1
  rm /tmp/jdk.tgz
fi

if [ ! -x "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "Downloading Android command-line tools..."
  curl -fsSL "$CMDLINE_URL" -o /tmp/cmdline.zip
  echo "$CMDLINE_SHA1  /tmp/cmdline.zip" | sha1sum -c -
  mkdir -p "$ANDROID_HOME/cmdline-tools"
  unzip -q /tmp/cmdline.zip -d "$ANDROID_HOME/cmdline-tools"
  mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
  rm /tmp/cmdline.zip
fi

export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager --install "${SDK_PACKAGES[@]}" "$@" >/dev/null
java -version 2>&1 | head -1

# Export tool paths for later steps on Forgejo and GitHub.
if [ -n "${GITHUB_ENV:-}" ]; then
  { echo "JAVA_HOME=$JAVA_HOME"; echo "ANDROID_HOME=$ANDROID_HOME"; echo "ANDROID_SDK_ROOT=$ANDROID_HOME"; } >> "$GITHUB_ENV"
  { echo "$JAVA_HOME/bin"; echo "$ANDROID_HOME/cmdline-tools/latest/bin"; echo "$ANDROID_HOME/platform-tools"; echo "$ANDROID_HOME/emulator"; } >> "$GITHUB_PATH"
fi
