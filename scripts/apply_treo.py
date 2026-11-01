#!/usr/bin/env python3
from pathlib import Path
import sys
import re
import xml.etree.ElementTree as ET

if len(sys.argv) != 2:
    raise SystemExit("usage: apply_treo.py <project-root>")
r = Path(sys.argv[1]).resolve()
a = r / "app/src/main"

def replace_once(path, old, new, what):
    p = r / path
    s = p.read_text(encoding="utf-8")
    if new in s:
        return
    if s.count(old) != 1:
        raise SystemExit(f"{what}: expected 1 anchor, got {s.count(old)}")
    p.write_text(s.replace(old, new, 1), encoding="utf-8")

# Manifest: foreground service shares MicroActivity's :midlet process.
p = a / "AndroidManifest.xml"
s = p.read_text(encoding="utf-8")
if "FOREGROUND_SERVICE_SPECIAL_USE" not in s:
    s = s.replace('    <uses-permission android:name="android.permission.RECORD_AUDIO" />', '''    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />''', 1)
if "javax.microedition.shell.TreoOnlineService" not in s:
    s = s.replace('''        <service
            android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"''', '''        <service
            android:name="javax.microedition.shell.TreoOnlineService"
            android:enabled="true"
            android:exported="false"
            android:process=":midlet"
            android:stopWithTask="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Keep the user-started J2ME MIDlet active while the user backgrounds the game or turns off the screen." />
        </service>
        <service
            android:name="androidx.appcompat.app.AppLocalesMetadataHolderService"''', 1)
# The service MUST share MicroActivity's process because readiness and lifecycle
# flags are ordinary static fields. A hard-coded :midlet process can split them.
root = ET.fromstring(s)
android_ns = "{http://schemas.android.com/apk/res/android}"
activity = next((node for node in root.iter()
                 if node.tag in ("activity", "activity-alias")
                 and node.get(android_ns + "name", "").endswith(".MicroActivity")), None)
if activity is None:
    raise SystemExit("Could not find MicroActivity in AndroidManifest.xml")
activity_process = activity.get(android_ns + "process")
service_node = next((node for node in root.iter("service")
                     if node.get(android_ns + "name") == "javax.microedition.shell.TreoOnlineService"), None)
if service_node is None:
    raise SystemExit("TreoOnlineService declaration missing from manifest")
if activity_process:
    service_node.set(android_ns + "process", activity_process)
else:
    service_node.attrib.pop(android_ns + "process", None)
# Preserve the original manifest formatting while correcting only the service
# process attribute.
match = re.search(r'<service\s+android:name="javax\.microedition\.shell\.TreoOnlineService".*?</service>', s, re.S)
if not match:
    raise SystemExit("Could not locate TreoOnlineService XML declaration")
service_xml = re.sub(r'\s+android:process="[^"]+"', "", match.group(0))
if activity_process:
    service_xml = service_xml.replace(
        'android:name="javax.microedition.shell.TreoOnlineService"',
        'android:name="javax.microedition.shell.TreoOnlineService"\n            android:process="' + activity_process + '"', 1)
s = s[:match.start()] + service_xml + s[match.end():]
p.write_text(s, encoding="utf-8")
print("Treo service process aligned with MicroActivity:", activity_process or "default")

# Foreground Treo service with CPU/Wi-Fi/multicast locks and guarded releases.
svc = r / "app/src/main/java/javax/microedition/shell/TreoOnlineService.java"
svc.parent.mkdir(parents=True, exist_ok=True)
svc.write_text(r'''package javax.microedition.shell;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import ru.playsoftware.j2meloader.R;

public final class TreoOnlineService extends Service {
    private static final String TAG = "TreoOnlineService";
    private static final String CHANNEL = "nst_treo";
    private static final int NOTIFICATION_ID = 48116;
    private static final String START = "javax.microedition.shell.action.TREO_START";
    private static final String STOP = "javax.microedition.shell.action.TREO_STOP";
    private static volatile boolean requested;
    private static volatile boolean active;
    private static volatile String failureReason = "";
    public static String getFailureReason() { return failureReason; }
    private PowerManager.WakeLock cpu;
    private WifiManager.WifiLock wifi;
    private WifiManager.MulticastLock multicast;

    public static boolean isEnabled() { return requested; }
    public static boolean isActive() { return active; }

    public static boolean enable(Context context) {
        requested = true; // Publish before ON_STOP can be delivered.
        active = false;
        failureReason = "";
        Intent i = new Intent(context, TreoOnlineService.class).setAction(START);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i);
            else context.startService(i);
            return true;
        } catch (RuntimeException e) {
            requested = false;
            active = false;
            failureReason = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            Log.e(TAG, "Unable to start Treo service: " + failureReason, e);
            return false;
        }
    }

    public static void disable(Context context) {
        requested = false;
        active = false;
        try { context.stopService(new Intent(context, TreoOnlineService.class)); }
        catch (RuntimeException e) { Log.w(TAG, "Unable to stop Treo service", e); }
        MidletThread.pauseIfActivityStopped();
    }

    @Override public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL,
                        getString(R.string.treo_channel_name), NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification makeNotification() {
        Intent open = new Intent(this, MicroActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(this, NOTIFICATION_ID, open, flags);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.treo_notification_title))
                .setContentText(getString(R.string.treo_notification_text))
                .setContentIntent(pi)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true).setOnlyAlertOnce(true).build();
    }

    @Override public int onStartCommand(@Nullable Intent intent, int flags, int id) {
        if (intent != null && STOP.equals(intent.getAction())) {
            requested = false;
            active = false;
            releaseLocks();
            stopForeground(true);
            stopSelf(id);
            MidletThread.pauseIfActivityStopped();
            return START_NOT_STICKY;
        }
        requested = true; // START_STICKY may restart with a null Intent.
        try {
            Notification n = makeNotification();
            if (Build.VERSION.SDK_INT >= 34)
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NOTIFICATION_ID, n);
            acquireLocks();
            active = true; // Activity may only move to background after foreground is confirmed.
            return START_STICKY;
        } catch (RuntimeException e) {
            active = false;
            requested = false;
            releaseLocks();
            failureReason = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
            Log.e(TAG, "Unable to enter foreground Treo mode: " + failureReason, e);
            stopForeground(true);
            stopSelf(id);
            MidletThread.pauseIfActivityStopped();
            return START_NOT_STICKY;
        }
    }

    private void acquireLocks() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && cpu == null) {
            cpu = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":TreoCPU");
            cpu.setReferenceCounted(false);
            cpu.acquire();
        }
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            if (wifi == null) {
                wifi = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, getPackageName() + ":TreoWifi");
                wifi.setReferenceCounted(false);
            }
            if (!wifi.isHeld()) wifi.acquire();
            if (multicast == null) {
                multicast = wm.createMulticastLock(getPackageName() + ":TreoMulticast");
                multicast.setReferenceCounted(false);
            }
            if (!multicast.isHeld()) multicast.acquire();
        }
    }

    private void releaseLocks() {
        PowerManager.WakeLock c = cpu; cpu = null;
        if (c != null) try { if (c.isHeld()) c.release(); }
        catch (RuntimeException e) { Log.w(TAG, "CPU lock release failed", e); }
        WifiManager.WifiLock w = wifi; wifi = null;
        if (w != null) try { if (w.isHeld()) w.release(); }
        catch (RuntimeException e) { Log.w(TAG, "Wi-Fi lock release failed", e); }
        WifiManager.MulticastLock m = multicast; multicast = null;
        if (m != null) try { if (m.isHeld()) m.release(); }
        catch (RuntimeException e) { Log.w(TAG, "Multicast lock release failed", e); }
    }

    @Override public void onTaskRemoved(Intent rootIntent) { super.onTaskRemoved(rootIntent); }

    @Override public void onDestroy() {
        active = false;
        requested = false;
        releaseLocks();
        super.onDestroy();
        MidletThread.pauseIfActivityStopped();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }
}
''', encoding="utf-8")

# Patch every legacy TreoOnlineService too. The device crash log identifies
# ru.playsoftware.j2meloader.keepalive.TreoOnlineService, not only the
# replacement javax.microedition.shell.TreoOnlineService generated above.
legacy_patched = []
for legacy in r.rglob("TreoOnlineService.java"):
    if legacy == svc:
        continue
    legacy_text = legacy.read_text(encoding="utf-8")
    if "MidletThread.pauseIfBackground()" in legacy_text:
        legacy_text = legacy_text.replace(
            "MidletThread.pauseIfBackground()",
            "MidletThread.pauseIfActivityStopped()")
        legacy.write_text(legacy_text, encoding="utf-8")
        legacy_patched.append(str(legacy.relative_to(r)))
print("Legacy Treo services patched:", legacy_patched or "no direct legacy source match")

# Track lifecycle locally; never dereference ContextHolder's nullable WeakReference.
mt = r / "app/src/main/java/javax/microedition/shell/MidletThread.java"
s = mt.read_text(encoding="utf-8")
if "private volatile boolean activityStarted;" not in s:
    s = s.replace("\tprivate Handler handler;\n\tprivate int state;",
                  "\tprivate Handler handler;\n\tprivate int state;\n\tprivate volatile boolean activityStarted;", 1)
if "static void pauseIfActivityStopped()" not in s:
    s = s.replace("\tprivate void onActivityStateChanged(LifecycleOwner lifecycleOwner, Lifecycle.Event event) {", '''\tstatic void pauseIfActivityStopped() {
\t\tMidletThread current = instance;
\t\tif (current != null && current.handler != null && !current.activityStarted) {
\t\t\tcurrent.handler.obtainMessage(PAUSE).sendToTarget();
\t\t}
\t}

\tprivate void onActivityStateChanged(LifecycleOwner lifecycleOwner, Lifecycle.Event event) {''', 1)
old = "\t\t\tcase ON_START -> handler.obtainMessage(START).sendToTarget();\n\t\t\tcase ON_STOP -> handler.obtainMessage(PAUSE).sendToTarget();"
new = '''\t\t\tcase ON_START -> {
\t\t\t\tactivityStarted = true;
\t\t\t\thandler.obtainMessage(START).sendToTarget();
\t\t\t}
\t\t\tcase ON_STOP -> {
\t\t\t\tactivityStarted = false;
\t\t\t\tif (!TreoOnlineService.isEnabled()) handler.obtainMessage(PAUSE).sendToTarget();
\t\t\t}'''
if old in s: s = s.replace(old, new, 1)
elif "if (!TreoOnlineService.isEnabled())" not in s:
    raise SystemExit("MidletThread lifecycle anchor changed")
mt.write_text(s, encoding="utf-8")

# The Treo menu action starts the service, waits for foreground readiness, then
# backgrounds the current task without finishing the Activity.
menu = r / "app/src/main/res/menu/midlet_displayable.xml"
s = menu.read_text(encoding="utf-8")
if "action_treo_online" not in s:
    s = s.replace('''    <item
        android:id="@+id/action_exit_midlet"''', '''    <item
        android:id="@+id/action_treo_online"
        android:title="@string/treo_online"
        android:checkable="true"
        android:checked="false" />
    <item
        android:id="@+id/action_exit_midlet"''', 1)
menu.write_text(s, encoding="utf-8")

act = r / "app/src/main/java/javax/microedition/shell/MicroActivity.java"
s = act.read_text(encoding="utf-8")
if "waitForTreoAndHide(" not in s:
    s = s.replace("\tpublic boolean onPrepareOptionsMenu(Menu menu) {", '''\tpublic boolean onPrepareOptionsMenu(Menu menu) {
\t\tMenuItem treo = menu.findItem(R.id.action_treo_online);
\t\tif (treo != null) treo.setChecked(TreoOnlineService.isEnabled());''', 1)
    s = s.replace('''\t\tint id = item.getItemId();
\t\tif (id == R.id.action_exit_midlet) {''', '''\t\tint id = item.getItemId();
\t\tif (id == R.id.action_treo_online) {
\t\t\tif (TreoOnlineService.isEnabled()) {
\t\t\t\tTreoOnlineService.disable(this);
\t\t\t\titem.setChecked(false);
\t\t\t\tToast.makeText(this, R.string.treo_disabled, Toast.LENGTH_SHORT).show();
\t\t\t} else if (TreoOnlineService.enable(this)) {
\t\t\t\titem.setChecked(true);
\t\t\t\tToast.makeText(this, R.string.treo_starting, Toast.LENGTH_SHORT).show();
\t\t\t\twaitForTreoAndHide(0);
\t\t\t} else {
\t\t\t\tString reason = TreoOnlineService.getFailureReason();
\t\t\t\tif (reason == null || reason.isEmpty()) reason = "startService() bị Android từ chối";
\t\t\t\tToast.makeText(this, getString(R.string.treo_start_failed) + ": " + reason, Toast.LENGTH_LONG).show();
\t\t\t}
\t\t} else if (id == R.id.action_exit_midlet) {''', 1)
    s = s.replace('''\t@Override
\tpublic boolean onOptionsItemSelected(@NonNull MenuItem item) {''', '''\tprivate void waitForTreoAndHide(int attempt) {
\t\tif (isFinishing()) return;
\t\tif (TreoOnlineService.isActive()) moveTaskToBack(true);
\t\telse if (TreoOnlineService.isEnabled() && attempt < 20)
\t\t\tbinding.getRoot().postDelayed(() -> waitForTreoAndHide(attempt + 1), 100);
\t\telse {
\t\t\tString reason = TreoOnlineService.getFailureReason();
\t\t\tif (reason == null || reason.isEmpty()) reason = "Service không xác nhận trạng thái chạy sau 2 giây";
\t\t\tToast.makeText(this, getString(R.string.treo_start_failed) + ": " + reason, Toast.LENGTH_LONG).show();
\t\t\tinvalidateOptionsMenu();
\t\t}
\t}

\t@Override
\tpublic boolean onOptionsItemSelected(@NonNull MenuItem item) {''', 1)
act.write_text(s, encoding="utf-8")

strings = '''    <string name="treo_online">Treo online (ẩn game và chạy nền)</string>
    <string name="treo_starting">Đang khởi chạy chế độ treo...</string>
    <string name="treo_enabled">Đang treo online trong nền</string>
    <string name="treo_disabled">Đã tắt treo online</string>
    <string name="treo_start_failed">Không thể khởi chạy chế độ treo</string>
    <string name="treo_channel_name">J2ME Treo online</string>
    <string name="treo_notification_title">J2ME đang chạy nền</string>
    <string name="treo_notification_text">Phiên game được giữ hoạt động khi bạn chuyển ứng dụng hoặc tắt màn hình</string>
'''
for rel in ("app/src/main/res/values/strings.xml", "app/src/main/res/values-vi/strings.xml"):
    p = r / rel
    s = p.read_text(encoding="utf-8")
    if 'name="treo_online"' not in s:
        p.write_text(s.replace("</resources>", strings + "</resources>", 1), encoding="utf-8")


# Null-safe Activity reference: a WeakReference itself may be null during teardown.
ctx = r / "app/src/main/java/javax/microedition/util/ContextHolder.java"
cs = ctx.read_text(encoding="utf-8")
cs = cs.replace("private static WeakReference<MicroActivity> currentActivity;",
                "private static volatile WeakReference<MicroActivity> currentActivity;")
cs = cs.replace("""\tpublic static void setCurrentActivity(MicroActivity activity) {
\t\tcurrentActivity = new WeakReference<>(activity);
\t}""", """\tpublic static void setCurrentActivity(MicroActivity activity) {
\t\tcurrentActivity = activity == null ? null : new WeakReference<>(activity);
\t}

\tprivate static MicroActivity getCurrentActivity() {
\t\tWeakReference<MicroActivity> reference = currentActivity;
\t\treturn reference == null ? null : reference.get();
\t}""")
cs = cs.replace("MicroActivity context = currentActivity.get();", "MicroActivity context = getCurrentActivity();")
cs = cs.replace("return currentActivity.get();", "return getCurrentActivity();")
if "private static MicroActivity getCurrentActivity()" not in cs:
    raise SystemExit("ContextHolder null-safe patch anchor failed")
if "currentActivity.get()" in cs:
    raise SystemExit("Unsafe direct WeakReference dereference remains in ContextHolder")
ctx.write_text(cs, encoding="utf-8")

# Guard HandlerThread lifecycle races and avoid touching Activity from Service teardown.
mt = r / "app/src/main/java/javax/microedition/shell/MidletThread.java"
ms = mt.read_text(encoding="utf-8")
ms = ms.replace("private static MidletThread instance;", "private static volatile MidletThread instance;")
ms = ms.replace("private Handler handler;", "private volatile Handler handler;")
ms = ms.replace("private int state;", "private volatile int state;")
ms = ms.replace("""\tpublic static void notifyPaused() {
\t\tinstance.state = PAUSED;
\t}""", """\tpublic static void notifyPaused() {
\t\tMidletThread current = instance;
\t\tif (current != null) current.state = PAUSED;
\t}""")
ms = ms.replace("""\t\tif (instance != null && activity != null && activity.isVisible())
\t\t\tinstance.handler.obtainMessage(START).sendToTarget();""", """\t\tMidletThread current = instance;
\t\tHandler target = current == null ? null : current.handler;
\t\tif (activity != null && activity.isVisible() && target != null)
\t\t\ttarget.obtainMessage(START).sendToTarget();""")
ms = ms.replace("""\t\tif (instance != null) {
\t\t\tinstance.handler.obtainMessage(DESTROY).sendToTarget();
\t\t}""", """\t\tMidletThread current = instance;
\t\tHandler target = current == null ? null : current.handler;
\t\tif (target != null) target.obtainMessage(DESTROY).sendToTarget();""")
ms = ms.replace("""\t\thandler = new Handler(getLooper(), this);
\t\tContextHolder.getActivity().getLifecycle().addObserver(activityLifecycleObserver);""", """\t\thandler = new Handler(getLooper(), this);
\t\tMicroActivity activity = ContextHolder.getActivity();
\t\tif (activity != null) activity.getLifecycle().addObserver(activityLifecycleObserver);
\t\telse Log.w(TAG, "MicroActivity unavailable while starting MIDlet thread");""")
ms = ms.replace("""\tstatic void pauseIfActivityStopped() {
\t\tMidletThread current = instance;
\t\tif (current != null && current.handler != null && !current.activityStarted) {
\t\t\tcurrent.handler.obtainMessage(PAUSE).sendToTarget();
\t\t}
\t}""", """\tstatic void pauseIfActivityStopped() {
\t\tMidletThread current = instance;
\t\tif (current == null || current.activityStarted || current.state != STARTED) return;
\t\tHandler target = current.handler;
\t\tif (target != null && target.getLooper().getThread().isAlive())
\t\t\ttarget.obtainMessage(PAUSE).sendToTarget();
\t}""")
old_life = """\tprivate void onActivityStateChanged(LifecycleOwner lifecycleOwner, Lifecycle.Event event) {
\t\tswitch (event) {
\t\t\tcase ON_CREATE -> handler.obtainMessage(INIT).sendToTarget();
\t\t\tcase ON_START -> {
\t\t\t\tactivityStarted = true;
\t\t\t\thandler.obtainMessage(START).sendToTarget();
\t\t\t}
\t\t\tcase ON_STOP -> {
\t\t\t\tactivityStarted = false;
\t\t\t\tif (!TreoOnlineService.isEnabled()) handler.obtainMessage(PAUSE).sendToTarget();
\t\t\t}
\t\t\tcase ON_DESTROY -> handler.obtainMessage(DESTROY).sendToTarget();
\t\t}
\t}"""
new_life = """\tprivate void onActivityStateChanged(LifecycleOwner lifecycleOwner, Lifecycle.Event event) {
\t\tHandler target = handler;
\t\tif (target == null || !target.getLooper().getThread().isAlive()) return;
\t\tswitch (event) {
\t\t\tcase ON_CREATE -> target.obtainMessage(INIT).sendToTarget();
\t\t\tcase ON_START -> {
\t\t\t\tactivityStarted = true;
\t\t\t\ttarget.obtainMessage(START).sendToTarget();
\t\t\t}
\t\t\tcase ON_STOP -> {
\t\t\t\tactivityStarted = false;
\t\t\t\tif (!TreoOnlineService.isEnabled()) target.obtainMessage(PAUSE).sendToTarget();
\t\t\t}
\t\t\tcase ON_DESTROY -> target.obtainMessage(DESTROY).sendToTarget();
\t\t}
\t}"""
if old_life in ms: ms = ms.replace(old_life, new_life, 1)
if "private static volatile MidletThread instance;" not in ms or "current.state != STARTED" not in ms:
    raise SystemExit("MidletThread lifecycle patch anchor failed")

# Binary-compatibility shim for an older TreoOnlineService that still invokes
# MidletThread.pauseIfBackground(). Keep this method public and delegate to the
# lifecycle-only guard; it must never dereference ContextHolder.getActivity().
if "public static void pauseIfBackground()" not in ms:
    marker = "\tstatic void pauseIfActivityStopped() {"
    shim = """	public static void pauseIfBackground() {
		pauseIfActivityStopped();
	}

"""
    if ms.count(marker) != 1:
        raise SystemExit("Could not locate pauseIfActivityStopped() for legacy shim")
    ms = ms.replace(marker, shim + marker, 1)
mt.write_text(ms, encoding="utf-8")

# Static validation before Gradle.
ET.parse(a / "AndroidManifest.xml")
for p in (a / "res").rglob("*.xml"):
    ET.parse(p)
svc_text = svc.read_text(encoding="utf-8")
mt_text = mt.read_text(encoding="utf-8")
act_text = act.read_text(encoding="utf-8")
manifest_text = (a / "AndroidManifest.xml").read_text(encoding="utf-8")
assert 'android:name="javax.microedition.shell.TreoOnlineService"' in manifest_text
assert "active = true" in svc_text and "requested = true" in svc_text
assert "if (!TreoOnlineService.isEnabled())" in mt_text and "pauseIfActivityStopped" in mt_text
assert "moveTaskToBack(true)" in act_text and "TreoOnlineService.isActive()" in act_text
assert "WeakReference" not in svc_text
assert all(x in svc_text for x in ("if (c != null)", "if (w != null)", "if (m != null)"))
print("Treo patch applied; Manifest and all resource XML files validated")

# Lifecycle hardening is applied by scripts/apply_treo_lifecycle.py in the build workflow.
