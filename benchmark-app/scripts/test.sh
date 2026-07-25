#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
BUILD="$ROOT/build/classes"
rm -rf "$BUILD"
mkdir -p "$BUILD"
mapfile -t SOURCES < <(find "$ROOT/src/main/java" "$ROOT/src/test/java" -name '*.java' -print)
javac --release 17 -encoding UTF-8 -d "$BUILD" "${SOURCES[@]}"
java -cp "$BUILD" lab.benchmark.AllPublicTests
