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
  @Test(timeout = 900000)
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
    String lecture = store.createLecture(0, "auto", false, "queued");
    Activity activity = null;
    try {
      for (int i = 0; i < sources.length(); i++) {
        JSONObject item = sources.getJSONObject(i);
        String name = item.getString("file").replace("audio/", "");
        assertTrue(name.matches("section-[0-9]{5}\\.m4a"));
        File source = new File(input, name).getCanonicalFile();
        assertEquals(input, source.getParentFile());
        File owned = new File(store.directory(context, lecture), name);
        Files.copy(source.toPath(), owned.toPath());
        long chunk =
            store.addChunk(lecture, i, owned.getAbsolutePath(), item.getLong("start_ms"), false);
        store.ready(chunk, item.getLong("end_ms") - item.getLong("start_ms"));
      }
      activity =
          instrumentation.startActivitySync(
              new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      instrumentation.waitForIdleSync();
      long began = SystemClock.elapsedRealtime();
      instrumentation.runOnMainSync(
          () -> EngineService.command(context, EngineService.PROCESS, null));
      long deadline = began + 840000;
      do {
        Thread.sleep(200);
        if (SystemClock.elapsedRealtime() > deadline) fail("Evidence replay did not finish");
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
