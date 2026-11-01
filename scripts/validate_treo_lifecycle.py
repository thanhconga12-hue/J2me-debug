#!/usr/bin/env python3
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(sys.argv[1]).resolve() / "app/src/main"
ET.parse(root / "AndroidManifest.xml")
for path in (root / "res").rglob("*.xml"):
    ET.parse(path)
service = (root / "java/javax/microedition/shell/TreoOnlineService.java").read_text()
lifecycle = (root / "java/javax/microedition/shell/MidletThread.java").read_text()
activity = (root / "java/javax/microedition/shell/MicroActivity.java").read_text()
canvas = (root / "java/javax/microedition/lcdui/Canvas.java").read_text()
manifest = (root / "AndroidManifest.xml").read_text()
assert 'android:process=":midlet"' in manifest
assert "FOREGROUND_SERVICE_SPECIAL_USE" in manifest
assert "active = true" in service and "WakeLock" in service
assert "if (!TreoOnlineService.isEnabled())" in lifecycle
assert "if (!TreoOnlineService.isEnabled()) target.obtainMessage(DESTROY)" in lifecycle
assert "isTreoBackgroundTransition()" in activity
assert "treoBackgroundSurfaceLoss" in canvas
assert "suppressing background HIDE_NOTIFY" in canvas
assert "moveTaskToBack(true)" in activity
assert "WeakReference" not in service
print("Manifest, resources, and Treo background lifecycle checks passed")
