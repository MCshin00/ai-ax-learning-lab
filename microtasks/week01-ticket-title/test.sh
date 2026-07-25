#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
OUT="$ROOT/out"
rm -rf "$OUT"
mkdir -p "$OUT"
javac --release 17 -d "$OUT" \
  "$ROOT/src/TicketTitleNormalizer.java" \
  "$ROOT/test/TicketTitleNormalizerTest.java"
java -cp "$OUT" lab.week01.TicketTitleNormalizerTest
