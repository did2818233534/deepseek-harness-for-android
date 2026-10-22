#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME=${ANDROID_HOME:-/opt/android-sdk}
export GRADLE_USER_HOME=${GRADLE_USER_HOME:-/opt/gradle-cache}
node scripts/bundle.mjs
scripts/build-native.sh
./gradlew --no-daemon :app:assembleDebug
