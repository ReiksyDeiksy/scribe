package ro.scribe.lectures;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class EngineService extends Service {
  public static final String RECORD = "record",
      STOP = "stop",
      PAUSE = "pause",
      MARK = "mark",
      PROCESS = "process",
      CANCEL = "cancel";
  public static volatile String activeLecture;
  public static volatile boolean processing;

  // The process-wide capture handle holds only Application context and is cleared on completion.
  @android.annotation.SuppressLint("StaticFieldLeak")
  public static volatile RecorderEngine capture;

  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile boolean cancelled;
  private volatile boolean destroyed;
  private final Object completionLock = new Object();
  private Store store;
  private Context localized;

  @Override
  public void onCreate() {
    super.onCreate();
    store = Store.get(this);
    localized = ScribeApp.localized(this);
    NotificationChannel channel =
        new NotificationChannel(
            "lecture",
            localized.getString(R.string.notification_channel),
            NotificationManager.IMPORTANCE_LOW);
    getSystemService(NotificationManager.class).createNotificationChannel(channel);
  }

  public static void command(Context c, String action, String lecture) {
    Intent intent = new Intent(c, EngineService.class).setAction(action);
    if (lecture != null) intent.putExtra("lecture", lecture);
    c.startForegroundService(intent);
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent == null ? PROCESS : intent.getAction();
    if (RECORD.equals(action)) {
      if (capture != null || processing) return START_NOT_STICKY;
      activeLecture = intent.getStringExtra("lecture");
      try {
        foreground(true);
      } catch (RuntimeException e) {
        store.status(activeLecture, "error", "microphone_permission");
        activeLecture = null;
        stopSelf();
        return START_NOT_STICKY;
      }
      RecorderEngine recording = new RecorderEngine(this, activeLecture);
      capture = recording;
      String id = activeLecture;
      worker.execute(
          () -> {
            String error = "";
            try {
              recording.run();
              error = recording.interruption;
            } catch (Exception e) {
              error = code(e);
            } finally {
              store.status(id, error.isEmpty() ? "queued" : "interrupted", error);
              capture = null;
              activeLecture = null;
              main.post(
                  () -> {
                    if (!destroyed) {
                      foreground(false);
                      startQueue();
                    }
                  });
            }
          });
    } else if (PAUSE.equals(action) && capture != null) {
      capture.paused = !capture.paused;
      store.status(activeLecture, capture.paused ? "paused" : "recording", "");
      foreground(true);
    } else if (MARK.equals(action) && capture != null) {
      store.mark(activeLecture, capture.position);
      foreground(true);
    } else if (STOP.equals(action) && capture != null) {
      capture.stopped = true;
    } else if (CANCEL.equals(action)) {
      if (processing) {
        synchronized (completionLock) {
          cancelled = true;
          Whisper.cancel();
          if (activeLecture != null) store.status(activeLecture, "cancelled", "");
        }
      } else if (capture == null) {
        foreground(false);
        stopSelf();
      }
    } else if (PROCESS.equals(action) && capture == null) {
      foreground(false);
      startQueue();
    } else if (capture == null && !processing) {
      foreground(false);
      stopSelf();
    }
    return START_NOT_STICKY;
  }

  private PendingIntent action(String action, int code) {
    return PendingIntent.getService(
        this,
        code,
        new Intent(this, EngineService.class).setAction(action),
        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
  }

  private void foreground(boolean recording) {
    Notification.Builder n =
        new Notification.Builder(this, "lecture")
            .setSmallIcon(R.drawable.ic_scribe)
            .setContentTitle("scribe")
            .setContentText(
                localized.getString(
                    recording ? R.string.recording_notification : R.string.transcribing))
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
            .setOngoing(true)
            .setOnlyAlertOnce(true);
    if (recording) {
      n.addAction(
          new Notification.Action.Builder(null, localized.getString(R.string.mark), action(MARK, 1))
              .build());
      n.addAction(
          new Notification.Action.Builder(
                  null,
                  localized.getString(
                      capture != null && capture.paused ? R.string.resume : R.string.pause),
                  action(PAUSE, 2))
              .build());
      n.addAction(
          new Notification.Action.Builder(null, localized.getString(R.string.stop), action(STOP, 3))
              .build());
    } else
      n.addAction(
          new Notification.Action.Builder(
                  null, localized.getString(R.string.cancel), action(CANCEL, 4))
              .build());
    int type =
        recording
            ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            : Build.VERSION.SDK_INT >= 35
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING
                : ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
    startForeground(41, n.build(), type);
  }

  private void startQueue() {
    if (destroyed || processing || capture != null) return;
    processing = true;
    cancelled = false;
    Whisper.resetCancel();
    worker.execute(this::processQueue);
  }

  private void processQueue() {
    PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
    PowerManager.WakeLock wake =
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "scribe:transcription");
    Whisper model = null;
    try {
      wake.acquire(6 * 60 * 60 * 1000L);
      Store.Chunk chunk;
      while (!cancelled && (chunk = store.next()) != null) {
        activeLecture = chunk.lecture();
        store.chunkState(chunk.id(), "processing");
        store.status(chunk.lecture(), "processing", "");
        final Store.Chunk current = chunk;
        try {
          if (chunk.imported() && chunk.duration() == 0) {
            store.ready(chunk.id(), AudioDecoder.duration(this, chunk.path()));
            store.chunkState(chunk.id(), "processing");
          }
          if (model == null) model = new Whisper(this);
          final Whisper loaded = model;
          Store.Lecture lecture = store.lecture(chunk.lecture());
          final long[] checkpoint = {chunk.processed()};
          List<Store.Segment> prior = store.segments(chunk.lecture());
          final String[] previous = {prior.isEmpty() ? "" : prior.get(prior.size() - 1).text()};
          long end =
              AudioDecoder.decode(
                  this,
                  chunk.path(),
                  chunk.processed(),
                  () -> cancelled,
                  (start, samples) -> {
                    long endMs = start + samples.length * 1000L / Core.SAMPLE_RATE;
                    if (endMs <= checkpoint[0]) return;
                    if (StorageGuard.available(this) < Core.RESERVE_BYTES / 2)
                      throw new IOException("storage_low");
                    if (power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE)
                      throw new IOException("thermal");
                    List<Store.Piece> pieces = new ArrayList<>();
                    double energy = 0;
                    for (float s : samples) energy += s * s;
                    if (Math.sqrt(energy / samples.length) >= 0.0015) {
                      String prompt = previous[0];
                      if (prompt.length() > 500) prompt = prompt.substring(prompt.length() - 500);
                      JSONArray result = loaded.transcribe(samples, lecture.language(), prompt);
                      for (int i = 0; i < result.length(); i++) {
                        JSONObject s = result.getJSONObject(i);
                        long a = start + s.getLong("start"),
                            b = Math.min(endMs, start + s.getLong("end"));
                        if (b <= checkpoint[0]) continue;
                        String text = Core.trimOverlap(previous[0], s.getString("text"));
                        if (!Core.hasWords(text)
                            || text.matches("(?i)\\[.*(?:blank|silence|music).*\\]")) continue;
                        a = Math.max(a, checkpoint[0]);
                        if (b <= a) b = a + 1;
                        pieces.add(new Store.Piece(current.start() + a, current.start() + b, text));
                        previous[0] = text;
                      }
                    }
                    if (cancelled) throw new IOException("cancelled");
                    store.saveWindow(current, endMs, pieces);
                    checkpoint[0] = endMs;
                  });
          if (cancelled) throw new IOException("cancelled");
          if (chunk.imported()) store.ready(chunk.id(), end);
          if (store.segmentCount(chunk.id()) == 0) {
            store.chunkState(chunk.id(), "error");
            store.status(chunk.lecture(), "error", "no_words");
          } else {
            synchronized (completionLock) {
              if (cancelled) throw new IOException("cancelled");
              store.chunkState(chunk.id(), "done");
              deleteIfEligible(chunk, lecture);
            }
          }
          store.settle(chunk.lecture());
        } catch (Exception e) {
          store.chunkState(chunk.id(), cancelled ? "ready" : "error");
          store.status(
              chunk.lecture(), cancelled ? "cancelled" : "error", cancelled ? "" : code(e));
          if ("storage_low".equals(e.getMessage()) || "thermal".equals(e.getMessage())) break;
        }
      }
    } catch (Exception e) {
      if (activeLecture != null) store.status(activeLecture, "error", code(e));
    } finally {
      if (model != null) model.close();
      if (wake.isHeld()) wake.release();
      processing = false;
      activeLecture = null;
      main.post(
          () -> {
            if (!processing && capture == null) {
              stopForeground(STOP_FOREGROUND_REMOVE);
              stopSelf();
            }
          });
    }
  }

  private void deleteIfEligible(Store.Chunk chunk, Store.Lecture lecture) throws IOException {
    if (!Core.mayDelete(
        lecture.deleteAudio(), "done", store.segmentCount(chunk.id()), true, chunk.imported()))
      return;
    File file = new File(chunk.path()).getCanonicalFile(),
        directory = store.directory(this, lecture.id()).getCanonicalFile();
    if (!file.getParentFile().equals(directory)) throw new IOException("Invalid audio path");
    if (file.exists() && !file.delete()) throw new IOException("Could not remove completed audio");
  }

  private static String code(Exception e) {
    String s = e.getMessage();
    if (s != null
        && List.of(
                "storage_low",
                "thermal",
                "cancelled",
                "microphone_permission",
                "microphone_unavailable",
                "microphone_interrupted",
                "unsupported_audio",
                "no_words")
            .contains(s)) return s;
    return "processing_error";
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    cancelled = true;
    Whisper.cancel();
    if (capture != null) {
      capture.interruption = "interrupted";
      capture.stopped = true;
    }
    if (activeLecture != null) store.status(activeLecture, "interrupted", "interrupted");
    stopSelf();
  }

  @Override
  public void onDestroy() {
    destroyed = true;
    cancelled = true;
    if (capture != null) {
      capture.interruption = "interrupted";
      capture.stopped = true;
    }
    if (processing) Whisper.cancel();
    worker.shutdown();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }
}
