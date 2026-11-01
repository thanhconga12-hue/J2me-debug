#!/usr/bin/env python3
from pathlib import Path
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_treo_lifecycle.py <project-root>")

root = Path(sys.argv[1]).resolve()

def replace_once(path: Path, old: str, new: str, label: str):
    source = path.read_text(encoding="utf-8")
    if new in source:
        return
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    path.write_text(source.replace(old, new, 1), encoding="utf-8")

# An Android Activity teardown is not an explicit MIDlet exit. The explicit
# exit confirmation already dispatches DESTROY; backgrounding must not do so.
midlet = root / "app/src/main/java/javax/microedition/shell/MidletThread.java"
replace_once(
    midlet,
    "\t\t\tcase ON_DESTROY -> target.obtainMessage(DESTROY).sendToTarget();",
    """\t\t\tcase ON_DESTROY -> {
\t\t\t\tactivityStarted = false;
\t\t\t\tif (!TreoOnlineService.isEnabled()) target.obtainMessage(DESTROY).sendToTarget();
\t\t\t}""",
    "MidletThread ON_DESTROY"
)

# Track intentional user backgrounding. onUserLeaveHint also covers pressing
# Home/gesture navigation after Treo mode has been enabled.
activity = root / "app/src/main/java/javax/microedition/shell/MicroActivity.java"
source = activity.read_text(encoding="utf-8")
if "isTreoBackgroundTransition()" not in source:
    source = source.replace(
        "public class MicroActivity extends AppCompatActivity {",
        """public class MicroActivity extends AppCompatActivity {
\tprivate static volatile boolean treoBackgroundTransition;

\tpublic static boolean isTreoBackgroundTransition() {
\t\treturn treoBackgroundTransition;
\t}""",
        1
    )
if "public void onUserLeaveHint()" not in source:
    source = source.replace(
        "\t@Override\n\tpublic void onPause() {",
        """\t@Override
\tpublic void onUserLeaveHint() {
\t\tif (TreoOnlineService.isEnabled()) treoBackgroundTransition = true;
\t\tsuper.onUserLeaveHint();
\t}

\t@Override
\tpublic void onPause() {""",
        1
    )
old_move = "\t\tif (TreoOnlineService.isActive()) moveTaskToBack(true);"
new_move = """\t\tif (TreoOnlineService.isActive()) {
\t\t\ttreoBackgroundTransition = true;
\t\t\tmoveTaskToBack(true);
\t\t}"""
if new_move not in source:
    if source.count(old_move) != 1:
        raise SystemExit("MicroActivity moveTaskToBack anchor changed")
    source = source.replace(old_move, new_move, 1)
old_focus = """\tpublic void onWindowFocusChanged(boolean hasFocus) {
\t\tsuper.onWindowFocusChanged(hasFocus);"""
new_focus = """\tpublic void onWindowFocusChanged(boolean hasFocus) {
\t\tif (hasFocus) treoBackgroundTransition = false;
\t\tsuper.onWindowFocusChanged(hasFocus);"""
if new_focus not in source:
    if source.count(old_focus) != 1:
        raise SystemExit("MicroActivity window-focus anchor changed")
    source = source.replace(old_focus, new_focus, 1)
activity.write_text(source, encoding="utf-8")

# Android destroys the SurfaceView surface when the task is backgrounded.
# The default HIDE_NOTIFY often makes J2ME game loops stop themselves.
# In Treo mode, preserve logical Canvas visibility and suppress the paired
# SHOW_NOTIFY on return; the renderer/surface is still stopped and recreated.
canvas = root / "app/src/main/java/javax/microedition/lcdui/Canvas.java"
source = canvas.read_text(encoding="utf-8")
if "treoBackgroundSurfaceLoss" not in source:
    old_fields = """\tprivate class ViewCallbacks implements View.OnTouchListener, SurfaceHolder.Callback, View.OnKeyListener {
\t\tprivate final View mView;"""
    new_fields = """\tprivate class ViewCallbacks implements View.OnTouchListener, SurfaceHolder.Callback, View.OnKeyListener {
\t\tprivate final View mView;
\t\tprivate boolean treoBackgroundSurfaceLoss;"""
    if source.count(old_fields) != 1:
        raise SystemExit("Canvas ViewCallbacks field anchor changed")
    source = source.replace(old_fields, new_fields, 1)

    old_show = "\t\t\tDisplay.postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.SHOW_NOTIFY));"
    new_show = """\t\t\tboolean resumedFromTreoBackground = treoBackgroundSurfaceLoss;
\t\t\ttreoBackgroundSurfaceLoss = false;
\t\t\tif (!resumedFromTreoBackground)
\t\t\t\tDisplay.postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.SHOW_NOTIFY));"""
    if source.count(old_show) != 1:
        raise SystemExit("Canvas SHOW_NOTIFY anchor changed")
    source = source.replace(old_show, new_show, 1)

    old_hide = "\t\t\tDisplay.postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.HIDE_NOTIFY));"
    new_hide = """\t\t\tif (javax.microedition.shell.TreoOnlineService.isEnabled() && javax.microedition.shell.MicroActivity.isTreoBackgroundTransition()) {
\t\t\t\ttreoBackgroundSurfaceLoss = true;
\t\t\t\tLog.i(TAG, "Treo mode: suppressing background HIDE_NOTIFY");
\t\t\t} else {
\t\t\t\tDisplay.postEvent(CanvasEvent.getInstance(Canvas.this, CanvasEvent.HIDE_NOTIFY));
\t\t\t}"""
    if source.count(old_hide) != 1:
        raise SystemExit("Canvas HIDE_NOTIFY anchor changed")
    source = source.replace(old_hide, new_hide, 1)
    canvas.write_text(source, encoding="utf-8")

# Fail the build early if the generated sources do not contain all safeguards.
m = midlet.read_text(encoding="utf-8")
a = activity.read_text(encoding="utf-8")
c = canvas.read_text(encoding="utf-8")
assert "if (!TreoOnlineService.isEnabled()) target.obtainMessage(DESTROY)" in m
assert "isTreoBackgroundTransition()" in a and "onUserLeaveHint()" in a
assert "treoBackgroundSurfaceLoss" in c and "suppressing background HIDE_NOTIFY" in c
print("Treo background lifecycle safeguards applied and validated")
