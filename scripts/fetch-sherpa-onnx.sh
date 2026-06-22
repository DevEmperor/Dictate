#!/usr/bin/env bash
#
# Fetches the vendored sherpa-onnx native libraries for the on-device STT feature (issue #104).
#
# These .so/.jar are reproducible build artifacts from the pinned sherpa-onnx GitHub release, so
# they are NOT committed to git (see .gitignore). Run this once after cloning (and whenever VERSION
# below changes) to populate:
#
#   app/libs/sherpa-onnx-<VERSION>.jar          – the Kotlin/JNI API
#   app/src/main/jniLibs/arm64-v8a/*.so         – the native runtime (onnxruntime + jni)
#
# Usage:  scripts/fetch-sherpa-onnx.sh [--force]
#
set -euo pipefail

VERSION="1.13.3"
# sha256 of sherpa-onnx-<VERSION>.aar from the GitHub release (verified 2026-06-22).
AAR_SHA256="243ad797a3b6e75ebbeaf7a2ab4aec0777e7d71b730685abb762a120940b07b6"
URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${VERSION}/sherpa-onnx-${VERSION}.aar"

# Native libs the Kotlin/JNI path actually needs. c-api/cxx-api are for native C/C++ consumers and
# are intentionally omitted (saves ~4.6 MB; verified working without them in the Phase 0 spike).
SO_FILES=("libonnxruntime.so" "libsherpa-onnx-jni.so")
ABI="arm64-v8a"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR_DST="$REPO_ROOT/app/libs/sherpa-onnx-${VERSION}.jar"
JNI_DST="$REPO_ROOT/app/src/main/jniLibs/$ABI"

FORCE="${1:-}"
if [[ "$FORCE" != "--force" ]]; then
  have_all=true
  [[ -f "$JAR_DST" ]] || have_all=false
  for so in "${SO_FILES[@]}"; do [[ -f "$JNI_DST/$so" ]] || have_all=false; done
  if $have_all; then
    echo "✓ sherpa-onnx $VERSION already present (use --force to re-fetch)."
    exit 0
  fi
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "↓ Downloading sherpa-onnx $VERSION AAR …"
curl -fSL --retry 3 -o "$TMP/sherpa.aar" "$URL"

echo "· Verifying checksum …"
echo "$AAR_SHA256  $TMP/sherpa.aar" | sha256sum -c -

echo "· Extracting jar + $ABI native libs …"
mkdir -p "$REPO_ROOT/app/libs" "$JNI_DST"
python3 - "$TMP/sherpa.aar" "$JAR_DST" "$JNI_DST" "$ABI" "${SO_FILES[@]}" <<'PY'
import sys, zipfile, shutil
aar, jar_dst, jni_dst, abi, *sos = sys.argv[1:]
z = zipfile.ZipFile(aar)
with z.open("classes.jar") as f, open(jar_dst, "wb") as o:
    shutil.copyfileobj(f, o)
for so in sos:
    with z.open(f"jni/{abi}/{so}") as f, open(f"{jni_dst}/{so}", "wb") as o:
        shutil.copyfileobj(f, o)
print("  extracted:", ", ".join(["classes.jar"] + sos))
PY

echo "✓ sherpa-onnx $VERSION ready:"
echo "    $JAR_DST"
for so in "${SO_FILES[@]}"; do echo "    $JNI_DST/$so"; done
