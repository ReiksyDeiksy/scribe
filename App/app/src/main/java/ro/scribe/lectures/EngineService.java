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
import android.os.SystemClock;
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
            long started = SystemClock.elapsedRealtime();
            Diagnostics.record(
                this,
                id,
                "recording_started",
                -1,
                -1,
                "build",
                Diagnostics.build(this),
                "recognition",
                Diagnostics.recognition(store.recognitionEngine(id)),
                "sample_rate_hz",
                Core.SAMPLE_RATE,
                "audio_bitrate",
                64000);
            try {
              recording.run();
              error = recording.interruption;
            } catch (Exception e) {
              error = code(e);
            } finally {
              Diagnostics.record(
                  this,
                  id,
                  "recording_finished",
                  -1,
                  -1,
                  "wall_elapsed_ms",
                  SystemClock.elapsedRealtime() - started,
                  "audio_duration_ms",
                  recording.position,
                  "error",
                  error);
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
            .setSmallIcon(R.drawable.ic_notification)
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

  public static volatile long windowStartMs, windowEndMs, windowBeganMs;
  public static volatile boolean checkingSpeech;

  private static long covered(JSONArray segments) throws Exception {
    long end = 0, covered = 0;
    for (int i = 0; i < segments.length(); i++) {
      JSONObject segment = segments.getJSONObject(i);
      long a = Math.max(end, segment.getLong("start")), b = segment.getLong("end");
      if (b > a) covered += b - a;
      end = Math.max(end, b);
    }
    return covered;
  }

  private static String recognizedText(JSONArray segments) throws Exception {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < segments.length(); i++)
      text.append(segments.getJSONObject(i).getString("text")).append(' ');
    return text.toString();
  }

  private List<Store.Chunk> sequence(Store.Chunk first) {
    List<Store.Chunk> all = store.chunks(first.lecture()), sources = new ArrayList<>();
    int index = 0;
    while (index < all.size() && all.get(index).id() != first.id()) index++;
    if (index > 0 && first.processed() < 2000 && !first.imported()) {
      Store.Chunk previous = all.get(index - 1);
      if (!previous.imported()
          && previous.state().equals("done")
          && new File(previous.path()).isFile()
          && previous.start() + previous.duration() == first.start()) sources.add(previous);
    }
    sources.add(first);
    if (!first.imported())
      for (int i = index + 1; i < all.size(); i++) {
        Store.Chunk next = all.get(i), prior = sources.get(sources.size() - 1);
        if (next.imported()
            || !next.state().equals("ready")
            || next.processed() != 0
            || prior.start() + prior.duration() != next.start()) break;
        sources.add(next);
      }
    return sources;
  }

  private void processQueue() {
    PowerManager power = getSystemService(PowerManager.class);
    PowerManager.WakeLock wake =
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "scribe:transcription");
    SpeechModel model = null;
    String modelEngine = "";
    String retryLecture = "";
    int windowsSinceRetry = 6;
    long primaryBudget = 0, retrySpent = 0;
    try {
      wake.acquire(6 * 60 * 60 * 1000L);
      Store.Chunk chunk;
      while (!cancelled && (chunk = store.next()) != null) {
        activeLecture = chunk.lecture();
        store.status(chunk.lecture(), "processing", "");
        List<Store.Chunk> sources = sequence(chunk);
        Store.Lecture lecture = store.lecture(chunk.lecture());
        String engine = store.recognitionEngine(lecture.id());
        boolean lectureMode = engine.equals(LectureModel.ENGINE);
        if (!retryLecture.equals(lecture.id())) {
          retryLecture = lecture.id();
          windowsSinceRetry = 6;
          primaryBudget = 0;
          retrySpent = 0;
        }
        for (Store.Chunk source : sources)
          if (!source.state().equals("done")) store.chunkState(source.id(), "processing");
        final long[] checkpoint = {chunk.start() + chunk.processed()};
        final int[] retryDistance = {windowsSinceRetry};
        final long[] budgets = {primaryBudget, retrySpent};
        try {
          Diagnostics.record(
              this,
              lecture.id(),
              "chunk_started",
              -1,
              -1,
              "chunk_number",
              chunk.number(),
              "resume_from_ms",
              chunk.processed(),
              "source_count",
              sources.size(),
              "build",
              Diagnostics.build(this),
              "recognition",
              Diagnostics.recognition(engine));
          if (model == null || !modelEngine.equals(engine)) {
            if (model != null) {
              model.close();
              model = null;
            }
            long before = SystemClock.elapsedRealtime();
            model = lectureMode ? new LectureModel(this, () -> cancelled) : new Whisper(this);
            modelEngine = engine;
            Diagnostics.record(
                this,
                lecture.id(),
                "model_loaded",
                -1,
                -1,
                "elapsed_ms",
                SystemClock.elapsedRealtime() - before,
                "build",
                Diagnostics.build(this),
                "recognition",
                Diagnostics.recognition(engine));
          }
          final SpeechModel loaded = model;
          String known = store.detectedLanguage(lecture.id());
          final String[] language = {
            lectureMode
                ? "auto"
                : lecture.language().equals("auto") && !known.isEmpty() ? known : lecture.language()
          };
          List<Store.Segment> prior = store.segments(lecture.id());
          String previousText = "";
          for (Store.Segment segment : prior)
            if (segment.end() <= checkpoint[0]) previousText = segment.text();
          final String[] previous = {previousText};
          final long[] decodeStarted = {SystemClock.elapsedRealtime()};
          long end =
              LectureDecoder.decode(
                  this,
                  sources,
                  checkpoint[0],
                  () -> cancelled,
                  (start, samples) -> {
                    long began = SystemClock.elapsedRealtime(), decodeMs = began - decodeStarted[0];
                    long endMs = start + samples.length * 1000L / Core.SAMPLE_RATE;
                    if (endMs <= checkpoint[0]) return;
                    if (StorageGuard.available(this) < Core.RESERVE_BYTES / 2)
                      throw new IOException("storage_low");
                    if (power.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE)
                      throw new IOException("thermal");
                    windowStartMs = start;
                    windowEndMs = endMs;
                    windowBeganMs = began;
                    checkingSpeech = false;
                    double energy = 0;
                    for (float sample : samples) energy += sample * sample;
                    double rms = Math.sqrt(energy / samples.length);
                    JSONArray result = new JSONArray();
                    double confidence = 0;
                    String requestedLanguage = language[0], actualLanguage = language[0];
                    Object detectionProbability = JSONObject.NULL;
                    long recognitionMs = 0, retryMs = 0;
                    boolean review = false, retried = false, retryFailed = false;
                    if (rms >= 0.0015) {
                      long before = SystemClock.elapsedRealtime();
                      Whisper.Recognition first = loaded.recognize(samples, language[0], false);
                      if (!lectureMode && requestedLanguage.equals("auto"))
                        detectionProbability = first.languageProbability();
                      recognitionMs = SystemClock.elapsedRealtime() - before;
                      budgets[0] += recognitionMs;
                      Whisper.Recognition selected = first;
                      String text = recognizedText(first.segments());
                      long coverage = covered(first.segments()), duration = endMs - start;
                      boolean suspicious =
                          SpeechQuality.suspicious(
                              text, first.averageLogProbability(), coverage, duration, rms);
                      if (!lectureMode
                          && language[0].equals("auto")
                          && samples.length >= Core.SAMPLE_RATE * 8
                          && first.languageProbability() >= 0.5
                          && Core.hasWords(text)
                          && !SpeechQuality.repetitive(text)) {
                        language[0] = first.language();
                        store.recognitionState(lecture.id(), language[0], false);
                      }
                      // At most one extra pass per six windows, with a cumulative time budget.
                      if (!lectureMode
                          && suspicious
                          && duration >= 8000
                          && retryDistance[0] >= 6
                          && budgets[1] <= Math.max(15000, budgets[0] / 5)
                          && !cancelled) {
                        checkingSpeech = true;
                        retried = true;
                        retryDistance[0] = 0;
                        before = SystemClock.elapsedRealtime();
                        Whisper.Recognition retry = null;
                        try {
                          retry = loaded.recognize(samples, first.language(), true);
                        } catch (Exception retryError) {
                          if (cancelled || "cancelled".equals(retryError.getMessage()))
                            throw retryError;
                          retryFailed = true;
                          Diagnostics.record(
                              this,
                              lecture.id(),
                              "retry_failed",
                              start,
                              endMs,
                              "exception",
                              Diagnostics.failure(retryError));
                        }
                        retryMs = SystemClock.elapsedRealtime() - before;
                        budgets[1] += retryMs;
                        if (retry != null
                            && SpeechQuality.score(
                                    recognizedText(retry.segments()),
                                    retry.averageLogProbability(),
                                    covered(retry.segments()),
                                    duration)
                                > SpeechQuality.score(
                                    text, first.averageLogProbability(), coverage, duration))
                          selected = retry;
                      }
                      retryDistance[0]++;
                      result = selected.segments();
                      actualLanguage = selected.language();
                      confidence = selected.averageLogProbability();
                      review =
                          lectureMode
                              || retryFailed
                              || SpeechQuality.suspicious(
                                  recognizedText(result),
                                  confidence,
                                  covered(result),
                                  duration,
                                  rms);
                      recognitionMs += retryMs;
                    }
                    if (cancelled) throw new IOException("cancelled");
                    List<Store.Piece> pieces = new ArrayList<>();
                    for (int i = 0; i < result.length(); i++) {
                      JSONObject segment = result.getJSONObject(i);
                      long a = Math.max(start + segment.getLong("start"), checkpoint[0]);
                      long b = Math.min(endMs, start + segment.getLong("end"));
                      if (b <= checkpoint[0]) continue;
                      String text = segment.getString("text").trim();
                      // Only the overlapping beginning can duplicate the preceding passage.
                      if (!lectureMode && start + segment.getLong("start") < checkpoint[0])
                        text = Core.trimOverlap(previous[0], text);
                      if (!Core.hasWords(text)
                          || text.matches("(?i)\\[.*(?:blank|silence|music).*\\]")) continue;
                      if (b <= a) {
                        review = true;
                        continue;
                      }
                      pieces.add(new Store.Piece(a, b, text));
                      previous[0] = text;
                    }
                    if (lectureMode) pieces = LectureWords.passages(pieces);
                    if (review) store.recognitionState(lecture.id(), "", true);
                    synchronized (completionLock) {
                      if (cancelled) throw new IOException("cancelled");
                      store.saveSequenceWindow(sources, endMs, pieces);
                    }
                    checkpoint[0] = endMs;
                    Diagnostics.record(
                        this,
                        lecture.id(),
                        "window_processed",
                        start,
                        endMs,
                        "audio_ms",
                        samples.length * 1000L / Core.SAMPLE_RATE,
                        "decode_elapsed_ms",
                        decodeMs,
                        "recognition_elapsed_ms",
                        recognitionMs,
                        "retry_elapsed_ms",
                        retryMs,
                        "recognition_and_commit_elapsed_ms",
                        SystemClock.elapsedRealtime() - began,
                        "rms",
                        rms,
                        "returned_segments",
                        result.length(),
                        "accepted_segments",
                        pieces.size(),
                        "language",
                        actualLanguage,
                        "recognition_engine",
                        engine,
                        "language_requested",
                        requestedLanguage,
                        "language_detection_probability",
                        detectionProbability,
                        "average_log_probability",
                        lectureMode ? JSONObject.NULL : confidence,
                        "beam_retry",
                        retried,
                        "review_required",
                        review);
                    windowBeganMs = 0;
                    checkingSpeech = false;
                    decodeStarted[0] = SystemClock.elapsedRealtime();
                  },
                  lectureMode);
          for (Store.Chunk source : store.chunks(lecture.id())) {
            if (sources.stream().noneMatch(s -> s.id() == source.id())
                || source.state().equals("done")) continue;
            if (source.start() + source.duration() <= end
                && source.processed() >= source.duration()) {
              boolean words = store.overlappingSegments(source) > 0;
              store.chunkState(source.id(), words ? "done" : "error");
              if (!words) store.status(lecture.id(), "error", "no_words");
            }
          }
          store.settle(lecture.id());
          synchronized (completionLock) {
            Store.Lecture completed = store.lecture(lecture.id());
            if (!cancelled && completed.status().equals("done") && !store.needsReview(lecture.id()))
              for (Store.Chunk source : store.chunks(lecture.id())) {
                try {
                  deleteIfEligible(source, completed);
                } catch (IOException error) {
                  store.recognitionState(lecture.id(), "", true);
                  Diagnostics.record(
                      this,
                      lecture.id(),
                      "audio_retention_failed",
                      -1,
                      -1,
                      "exception",
                      Diagnostics.failure(error));
                  break;
                }
              }
          }
        } catch (Exception error) {
          boolean stopped = cancelled || "cancelled".equals(error.getMessage());
          Diagnostics.record(
              this,
              lecture.id(),
              "processing_failed",
              -1,
              -1,
              "error",
              stopped ? "cancelled" : code(error),
              "exception_type",
              error.getClass().getSimpleName(),
              "exception",
              Diagnostics.failure(error));
          long bad =
              error instanceof LectureDecoder.SourceFailure failed ? failed.chunk : chunk.id();
          for (Store.Chunk source : sources)
            if (!source.state().equals("done"))
              store.chunkState(source.id(), stopped || source.id() != bad ? "ready" : "error");
          store.status(lecture.id(), stopped ? "cancelled" : "error", stopped ? "" : code(error));
          if (List.of("storage_low", "thermal", "memory_low")
              .contains(error.getMessage() == null ? "" : error.getMessage())) break;
        } finally {
          windowBeganMs = 0;
          checkingSpeech = false;
          windowsSinceRetry = retryDistance[0];
          primaryBudget = budgets[0];
          retrySpent = budgets[1];
        }
      }
    } catch (Exception error) {
      if (activeLecture != null) store.status(activeLecture, "error", code(error));
    } finally {
      if (model != null) model.close();
      if (wake.isHeld()) wake.release();
      processing = false;
      activeLecture = null;
      windowBeganMs = 0;
      checkingSpeech = false;
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
                "memory_low",
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
