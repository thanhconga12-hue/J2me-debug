#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
DEST="$ROOT/app/src/main/cpp/mmapi_tsf/TinySoundFont"
if [ -d "$DEST/.git" ] || [ -f "$DEST/tsf.h" ]; then
  echo "TinySoundFont already present: $DEST"
  exit 0
fi
rmdir "$DEST" 2>/dev/null || true
git clone --depth 1 https://github.com/schellingb/TinySoundFont.git "$DEST"
echo "Fetched TinySoundFont into $DEST"
