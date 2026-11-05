#!/usr/bin/env bash
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
SIMH_DIR="$PROJECT/../simh-dvk"
NDK="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
CLANG="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang"
cd "$SIMH_DIR"
SOURCES="$(make -s -f makefile --eval='demos_sources: ; @echo $(PDP11) $(SIM)' demos_sources NONETWORK=1 DONT_USE_ROMS=1 | grep '^PDP11/pdp11_fp.c ' | head -1)"
if [[ -z "$SOURCES" ]]; then echo "SIMH source selection failed" >&2; exit 1; fi
for ABI in arm64-v8a x86_64; do
  case "$ABI" in
    arm64-v8a) TARGET=aarch64-linux-android35 ;;
    x86_64) TARGET=x86_64-linux-android35 ;;
  esac
  OUTPUT="$PROJECT/app/src/main/jniLibs/$ABI"
  mkdir -p "$OUTPUT"
  echo "--- Building $ABI SIMH DVK ---"
  "$CLANG" --target="$TARGET" -O2 -fPIE -pie -DVM_PDP11 \
    -DDONT_USE_INTERNAL_ROM -I. -I PDP11 -Wno-error \
    -Wl,-z,max-page-size=16384 $SOURCES \
    -o "$OUTPUT/libpdp11.so" -lm -ldl -pthread
  "$CLANG" --target="$TARGET" -O2 -fPIC -shared \
    -Wl,-z,max-page-size=16384 -o "$OUTPUT/libptybridge.so" \
    "$PROJECT/app/src/main/cpp/ptybridge.c"
  file "$OUTPUT/"*.so
done
