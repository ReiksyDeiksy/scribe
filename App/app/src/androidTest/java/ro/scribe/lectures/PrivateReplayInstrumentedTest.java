package ro.scribe.lectures;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Opt-in local evidence replay. Private fixtures are never included in either APK or Git. */
@RunWith(AndroidJUnit4.class)
public class PrivateReplayInstrumentedTest {
  @Test(timeout = 1800000)
  public void replayRetainedAudioThroughTheRealQueue() throws Exception {
    var args = InstrumentationRegistry.getArguments();
    Assume.assumeTrue(
        "Explicit private-fixture opt-in required", "true".equals(args.getString("replay")));
    String tag = args.getString("output_tag", "replay");
    assertTrue(tag.matches("[a-z0-9-]{1,40}"));
    var instrumentation = InstrumentationRegistry.getInstrumentation();
    Context context = instrumentation.getTargetContext();
    Store store = Store.get(context);
    File input = new File(context.getExternalFilesDir(null), "qa-input-report").getCanonicalFile();
    File manifestFile = new File(input, "report.json");
    assertTrue(manifestFile.length() <= 4 * 1024 * 1024);
    JSONObject manifest =
        new JSONObject(
            new String(Files.readAllBytes(manifestFile.toPath()), StandardCharsets.UTF_8));
    JSONArray sources = manifest.getJSONArray("audio");
    assertTrue(sources.length() > 0 && sources.length() <= 200);
    int repeats = Integer.parseInt(args.getString("repeats", "1"));
    assertTrue(repeats > 0 && repeats <= 100);
    String engine = args.getString("engine", "whisper");
    String lecture = store.createLecture(0, "auto", false, "queued", engine);
    Activity activity = null;
    try {
      long cycle = manifest.getJSONObject("lecture").getLong("duration_ms");
      for (int repeat = 0; repeat < repeats; repeat++)
        for (int i = 0; i < sources.length(); i++) {
          JSONObject item = sources.getJSONObject(i);
          String name = item.getString("file").replace("audio/", "");
          assertTrue(name.matches("section-[0-9]{5}\\.m4a"));
          File source = new File(input, name).getCanonicalFile();
          assertEquals(input, source.getParentFile());
          File owned = new File(store.directory(context, lecture), repeat + "-" + name);
          Files.copy(source.toPath(), owned.toPath());
          long chunk =
              store.addChunk(
                  lecture,
                  repeat * sources.length() + i,
                  owned.getAbsolutePath(),
                  repeat * cycle + item.getLong("start_ms"),
                  false);
          store.ready(chunk, item.getLong("end_ms") - item.getLong("start_ms"));
        }
      activity =
          instrumentation.startActivitySync(
              new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      instrumentation.waitForIdleSync();
      long began = SystemClock.elapsedRealtime();
      instrumentation.runOnMainSync(
          () -> EngineService.command(context, EngineService.PROCESS, null));
      long deadline = began + 1740000;
      long nextSample = 0, peakPss = 0, editedSegment = 0;
      long pauseAt = Long.parseLong(args.getString("pause_at_ms", "0"));
      boolean resumed = false;
      do {
        Thread.sleep(200);
        if (SystemClock.elapsedRealtime() > deadline) fail("Evidence replay did not finish");
        long processed = 0;
        for (Store.Chunk chunk : store.chunks(lecture)) processed += chunk.processed();
        if (pauseAt > 0 && !resumed && processed >= pauseAt && EngineService.processing) {
          instrumentation.runOnMainSync(
              () -> EngineService.command(context, EngineService.CANCEL, null));
          long stopDeadline = SystemClock.elapsedRealtime() + 60000;
          while (EngineService.processing && SystemClock.elapsedRealtime() < stopDeadline)
            Thread.sleep(100);
          assertFalse("Cancellation did not settle", EngineService.processing);
          instrumentation.waitForIdleSync();
          Thread.sleep(500);
          editedSegment = store.segments(lecture).get(0).id();
          store.edit(editedSegment, "Preserved QA correction");
          store.retry(lecture);
          instrumentation.runOnMainSync(
              () -> EngineService.command(context, EngineService.PROCESS, null));
          resumed = true;
        }
        if (SystemClock.elapsedRealtime() >= nextSample) {
          peakPss = Math.max(peakPss, android.os.Debug.getPss());
          JSONObject progress =
              new JSONObject()
                  .put("elapsed_ms", SystemClock.elapsedRealtime() - began)
                  .put("processed_ms", processed)
                  .put("duration_ms", store.lecture(lecture).duration())
                  .put("peak_process_pss_kib", peakPss)
                  .put("resumed", resumed);
          File latest = new File(context.getExternalFilesDir(null), "qa-" + tag + "-progress.json");
          File temp = new File(latest.getPath() + ".partial");
          Files.write(temp.toPath(), progress.toString().getBytes(StandardCharsets.UTF_8));
          assertTrue(temp.renameTo(latest));
          nextSample = SystemClock.elapsedRealtime() + 10000;
        }
      } while (EngineService.processing
          || (!store.lecture(lecture).status().equals("done")
              && !store.lecture(lecture).status().equals("error")));
      JSONObject result =
          new JSONObject()
              .put(
                  "version",
                  context
                      .getPackageManager()
                      .getPackageInfo(context.getPackageName(), 0)
                      .versionName)
              .put("wall_elapsed_ms", SystemClock.elapsedRealtime() - began)
              .put("recognition_engine", engine)
              .put("audio_duration_ms", store.lecture(lecture).duration())
              .put("source_count", store.chunks(lecture).size())
              .put("peak_process_pss_kib", peakPss)
              .put("pause_resume_checked", resumed)
              .put("status", store.lecture(lecture).status())
              .put("error", store.lecture(lecture).error())
              .put("transcript", store.reportText(lecture, 0, Long.MAX_VALUE))
              .put("diagnostics", store.reportDiagnostics(lecture, 0, Long.MAX_VALUE));
      try (FileOutputStream out =
          new FileOutputStream(
              new File(context.getExternalFilesDir(null), "qa-" + tag + ".json"))) {
        out.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
      }
      assertTrue(store.segments(lecture).size() > 0);
      if (pauseAt > 0) {
        assertTrue(resumed);
        long preserved = editedSegment;
        assertTrue(
            store.segments(lecture).stream()
                .anyMatch(s -> s.id() == preserved && s.text().equals("Preserved QA correction")));
      }
      assertEquals("done", store.lecture(lecture).status());
      for (Store.Chunk chunk : store.chunks(lecture)) {
        assertEquals("done", chunk.state());
        assertTrue(chunk.processed() >= chunk.duration());
      }
      for (Store.Chunk chunk : store.chunks(lecture)) assertTrue(new File(chunk.path()).isFile());
    } finally {
      if (EngineService.processing) EngineService.command(context, EngineService.CANCEL, null);
      long deadline = SystemClock.elapsedRealtime() + 30000;
      while (EngineService.processing && SystemClock.elapsedRealtime() < deadline)
        Thread.sleep(100);
      for (Store.Chunk chunk : store.chunks(lecture)) new File(chunk.path()).delete();
      store.directory(context, lecture).delete();
      store.deleteLecture(lecture);
      if (activity != null) {
        Activity current = activity;
        instrumentation.runOnMainSync(current::finish);
      }
    }
  }
}
