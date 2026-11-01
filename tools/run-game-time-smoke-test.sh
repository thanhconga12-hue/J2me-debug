#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
BUILD_DIR=$(mktemp -d)
trap 'rm -rf "$BUILD_DIR"' EXIT INT TERM
javac --release 8 -d "$BUILD_DIR" \
  "$ROOT/app/src/main/java/javax/microedition/shell/GameTime.java" \
  "$ROOT/tools/GameTimeSmokeTest.java"
java -cp "$BUILD_DIR" GameTimeSmokeTest
