#!/usr/bin/env bash
set -eu
cd "$(dirname "$0")/../.."
ndk=${NDK_DIR:-/opt/android-sdk/ndk/29.0.14206865}
for spec in aarch64:arm64-v8a x86_64:x86_64; do
 arch=${spec%:*}; abi=${spec#*:}; dir="app/src/full/jniLibs/$abi"
 mkdir -p "$dir"
 "$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/$arch-linux-android28-clang" -O2 -shared -fPIC -Wl,-z,max-page-size=16384 app/src/full/cpp/engine_guard.c -o "$dir/libdshguard.so"
done
