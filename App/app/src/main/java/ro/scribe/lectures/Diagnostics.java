package ro.scribe.lectures;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Debug;
import android.os.PowerManager;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded, content-free measurements. Diagnostics must never interrupt capture or ASR. */
final class Diagnostics {
  private Diagnostics() {}

  static JSONObject object(Object... pairs) {
    JSONObject result = new JSONObject();
    try {
      for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
    } catch (org.json.JSONException e) {
      throw new IllegalArgumentException(e);
    }
    return result;
  }

  static JSONObject build(Context context) {
    String version = "unknown";
    long code = 0;
    try {
      var info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
      version = info.versionName;
      code = info.getLongVersionCode();
    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
    }
    return object(
        "version_name",
        version,
        "version_code",
        code,
        "manufacturer",
        Build.MANUFACTURER,
        "model",
        Build.MODEL,
        "android_release",
        Build.VERSION.RELEASE,
        "sdk",
        Build.VERSION.SDK_INT,
        "abi",
        Build.SUPPORTED_ABIS.length == 0 ? "unknown" : Build.SUPPORTED_ABIS[0]);
  }

  static JSONObject recognition() {
    return object(
        "model",
        Whisper.MODEL,
        "model_sha256",
        Whisper.SHA256,
        "backend",
        "CPU",
        "threads",
        Math.max(1, Math.min(3, Runtime.getRuntime().availableProcessors() - 1)),
        "sampling",
        "greedy",
        "temperature",
        0,
        "temperature_increment",
        0,
        "window_ms",
        30000,
        "overlap_ms",
        2000,
        "sample_rate_hz",
        Core.SAMPLE_RATE);
  }

  static JSONObject failure(Exception error) {
    JSONArray frames = new JSONArray();
    StackTraceElement[] stack = error.getStackTrace();
    for (int i = 0; i < Math.min(12, stack.length); i++) {
      StackTraceElement frame = stack[i];
      frames.put(frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber());
    }
    return object("type", error.getClass().getName(), "frames", frames);
  }

  static JSONObject sample(Context context) {
    Intent battery =
        context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
    context.getSystemService(ActivityManager.class).getMemoryInfo(memory);
    return object(
        "thermal_status",
        context.getSystemService(PowerManager.class).getCurrentThermalStatus(),
        "battery_level",
        battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        "battery_scale",
        battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        "plugged",
        battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1),
        "native_heap_bytes",
        Debug.getNativeHeapAllocatedSize(),
        "java_heap_bytes",
        Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
        "device_ram_bytes",
        memory.totalMem,
        "free_storage_bytes",
        StorageGuard.available(context));
  }

  static void record(
      Context context, String lecture, String kind, long start, long end, Object... pairs) {
    if (lecture == null) return;
    try {
      if (StorageGuard.available(context) < Core.RESERVE_BYTES) return;
      JSONObject data = object(pairs);
      data.put("sample", sample(context));
      Store.get(context).diagnostic(lecture, kind, start, end, data);
    } catch (RuntimeException | org.json.JSONException ignored) {
      // Low storage or unavailable system measurements must not make a recording fail.
    }
  }
}
