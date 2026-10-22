#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
NODE_MOBILE_DIR=${NODE_MOBILE_DIR:-/opt/dsh-android/nodejs-mobile}
NDK_DIR=${NDK_DIR:-/opt/android-sdk/ndk/29.0.14206865}
for abi in arm64-v8a x86_64; do
  if [[ "$abi" = arm64-v8a ]]; then triple=aarch64-linux-android28; libtriple=aarch64-linux-android; else triple=x86_64-linux-android28; libtriple=x86_64-linux-android; fi
  mkdir -p "app/src/main/jniLibs/$abi"
  cp "$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$libtriple/libc++_shared.so" "app/src/main/jniLibs/$abi/"
  cp "$NODE_MOBILE_DIR/bin/$abi/libnode.so" "app/src/main/jniLibs/$abi/"
  "$NDK_DIR/toolchains/llvm/prebuilt/linux-x86_64/bin/$triple-clang++" -shared -fPIC -std=c++17 -stdlib=libc++ -Wl,-z,max-page-size=16384 -I"$NODE_MOBILE_DIR/include/node" app/src/main/cpp/runtime.cpp -L"app/src/main/jniLibs/$abi" -lnode -llog -o "app/src/main/jniLibs/$abi/libharness.so"
done
