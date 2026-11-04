#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-/opt/gradle-cache}
node scripts/bundle.mjs
scripts/build-native.sh
for arch in aarch64 x86_64; do
  python3 scripts/termux/fetch-seed.py "$arch"
  python3 scripts/termux/build-seed.py "$arch"
done
bash scripts/termux/build-guard.sh
./gradlew --no-daemon :app:assembleDebug
