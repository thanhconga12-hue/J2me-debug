#!/usr/bin/env python3
"""Validate the generated Treo integration against the actual Android manifest."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("usage: validate_treo_lifecycle.py <project-root>")

root = Path(sys.argv[1]).resolve() / "app/src/main"
manifest_path = root / "AndroidManifest.xml"
tree = ET.parse(manifest_path)
manifest = tree.getroot()
android = "{http://schemas.android.com/apk/res/android}"

for path in (root / "res").rglob("*.xml"):
    ET.parse(path)

service_path = root / "java/javax/microedition/shell/TreoOnlineService.java"
lifecycle_path = root / "java/javax/microedition/shell/MidletThread.java"
activity_path = root / "java/javax/microedition/shell/MicroActivity.java"
canvas_path = root / "java/javax/microedition/lcdui/Canvas.java"

service = service_path.read_text(encoding="utf-8")
lifecycle = lifecycle_path.read_text(encoding="utf-8")
activity = activity_path.read_text(encoding="utf-8")
canvas = canvas_path.read_text(encoding="utf-8")

activity_node = next((node for node in manifest.iter()
                      if node.tag in ("activity", "activity-alias")
                      and node.get(android + "name", "").endswith(".MicroActivity")), None)
if activity_node is None:
    raise SystemExit("MicroActivity declaration missing from AndroidManifest.xml")

service_node = next((node for node in manifest.iter("service")
                     if node.get(android + "name") == "javax.microedition.shell.TreoOnlineService"), None)
if service_node is None:
    raise SystemExit("TreoOnlineService declaration missing from AndroidManifest.xml")

# Compare effective process names. An omitted process means the application process.
app_process = manifest.get(android + "process")
activity_process = activity_node.get(android + "process", app_process)
service_process = service_node.get(android + "process", app_process)
if activity_process != service_process:
    raise SystemExit(f"Treo service process mismatch: activity={activity_process!r}, service={service_process!r}")

permissions = {node.get(android + "name") for node in manifest.findall("uses-permission")}
required_permissions = {
    "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
    "android.permission.WAKE_LOCK",
}
missing_permissions = required_permissions - permissions
if missing_permissions:
    raise SystemExit("Missing required permissions: " + ", ".join(sorted(missing_permissions)))

if service_node.get(android + "foregroundServiceType") != "specialUse":
    raise SystemExit("Treo service must declare foregroundServiceType=specialUse")
if service_node.get(android + "exported") != "false":
    raise SystemExit("Treo service must be non-exported")

checks = {
    "foreground service activation": "startForeground(" in service,
    "CPU WakeLock": "PARTIAL_WAKE_LOCK" in service,
    "sticky service": "START_STICKY" in service,
    "lifecycle DESTROY guard": "if (!TreoOnlineService.isEnabled()) target.obtainMessage(DESTROY)" in lifecycle,
    "background transition tracking": "isTreoBackgroundTransition()" in activity and "onUserLeaveHint()" in activity,
    "Canvas background handling": "treoBackgroundSurfaceLoss" in canvas and "suppressing background HIDE_NOTIFY" in canvas,
    "background task transition": "moveTaskToBack(true)" in activity,
}
failed = [name for name, ok in checks.items() if not ok]
if failed:
    raise SystemExit("Treo validation failed: " + ", ".join(failed))

print("Manifest, process alignment, permissions, service, and lifecycle checks passed")
